package kg.obukhov.wakethemallbot.bot;

import kg.obukhov.wakethemallbot.config.BotProperties;
import kg.obukhov.wakethemallbot.model.ChatEntity;
import kg.obukhov.wakethemallbot.model.TelegramUserEntity;
import kg.obukhov.wakethemallbot.service.ChatUserService;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Bot extends TelegramWebhookBot {

    private static final String[] MENTION_ALL_COMMANDS = {"/all", "@all", "/everyone", "@everyone"};
    private static final String[] MENTION_ADMIN_COMMANDS = {"/admins", "@admins", "/administrators", "@administrators"};
    private static final Set<String> ALL_GROUP_MEMBER_STATUSES = Set.of("member", "administrator", "creator");
    private static final Set<String> ADMIN_GROUP_MEMBER_STATUSES = Set.of("administrator", "creator");
    private static final String MESSAGE_DELETED_ERROR = "[400] Bad Request: message to be replied not found";
    private static final String SEND_FAILED_MESSAGE = "Мой автор криворукий, поэтому я не смог отправить уведомление";
    private static final String NO_MEMBERS_MESSAGE = "Не удалось найти подходящих пользователей для упоминания";
    private static final String PARSE_MODE = "MarkdownV2";
    private static final Duration LAST_MENTIONS_DURATION = Duration.ofSeconds(5);
    private static final int LAST_MENTIONS_LIMIT = 1;
    private static final String LAST_MENTIONS_LIMIT_MESSAGE = "Не флудите. Пожалейте народ!";
    private static final String PRIVATE_CHAT_RESPONSE_MESSAGE = "Бот вас запомнил и будет пересылать упоминания в общих чатах";

    private final Map<Long, List<Instant>> lastMentions;

    private final ChatUserService chatUserService;

    @Getter
    private final String botUsername;

    public Bot(ChatUserService chatUserService, BotProperties properties) {
        super(properties.getToken());
        this.chatUserService = chatUserService;
        this.botUsername = properties.getUsername();
        this.lastMentions = new HashMap<>();
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        log.debug("Update received: {}", update);

        if (update.hasMyChatMember()) {
            Chat chat = update.getMyChatMember().getChat();
            User from = update.getMyChatMember().getFrom();
            chatUserService.saveChatAndUser(chat, from);
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            User author = message.getFrom();
            chatUserService.saveChatAndUser(message.getChat(), author);
            if (isGroupChat(message.getChat())) {
                sendMentions(message, author);
            } else {
                sendPrivateChatDenialMessage(message.getChat());
            }
        }

        return null;
    }

    private static boolean isGroupChat(Chat chat) {
        return chat.getId() < 0;
    }

    private void sendPrivateChatDenialMessage(Chat chat) {
        SendMessage privateMessage = SendMessage.builder()
                .chatId(chat.getId())
                .text(PRIVATE_CHAT_RESPONSE_MESSAGE)
                .parseMode(PARSE_MODE)
                .disableWebPagePreview(true)
                .build();
        try {
            execute(privateMessage);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void sendMentions(Message message, User author) {
        if (StringUtils.containsAnyIgnoreCase(message.getText(), MENTION_ALL_COMMANDS)) {
            log.debug("Sending mentions to all members of chat {}", message.getChat().getTitle());
            sendMentions(message, author, ALL_GROUP_MEMBER_STATUSES);
        } else if (StringUtils.containsAnyIgnoreCase(message.getText(), MENTION_ADMIN_COMMANDS)) {
            log.debug("Sending mentions to admin members of chat {}", message.getChat().getTitle());
            sendMentions(message, author, ADMIN_GROUP_MEMBER_STATUSES);
        } else {
            log.debug("No mention commands found in message {}", message.getMessageId());
        }
    }

    private void sendMentions(Message messageToReply, User author, Set<String> memberStatuses) {
        Chat chat = messageToReply.getChat();
        Set<TelegramUserEntity> chatUsers = getChatUsers(chat, author, memberStatuses);

        if (chatUsers.isEmpty()) {
            reply(chat.getId(), escapeMarkdownV2(NO_MEMBERS_MESSAGE), messageToReply.getMessageId(), false);
        } else {
            String text = getMessageText(chatUsers);
            reply(chat.getId(), text, messageToReply.getMessageId(), true);
            for (TelegramUserEntity user : chatUsers) {
                sendPrivateMention(user, chat);
            }
        }
    }

    private Set<TelegramUserEntity> getChatUsers(Chat chat, User excluded, Set<String> memberStatuses) {
        return chatUserService.findOrMapChat(chat)
                .getUsers()
                .stream()
                .filter(user -> !excluded.getUserName().equals(user.getUserName()))
                .filter(user -> isUserGroupMember(chat.getId(), user.getId(), memberStatuses))
                .collect(Collectors.toSet());
    }

    private void reply(Long chatId, String text, Integer replyToMessageId, boolean notifyIsCaseOfError) {
        if (!ensureLastMentionsLimit(chatId, replyToMessageId, notifyIsCaseOfError)) {
            return;
        }

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyToMessageId(replyToMessageId)
                .parseMode(PARSE_MODE)
                .disableWebPagePreview(true)
                .disableNotification(false)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains(MESSAGE_DELETED_ERROR)) {
                send(chatId, text, notifyIsCaseOfError);
            } else {
                log.error(e.getMessage(), e);
                if (notifyIsCaseOfError) {
                    send(chatId, escapeMarkdownV2(SEND_FAILED_MESSAGE), false);
                }
            }
        }
    }

    private void sendPrivateMention(TelegramUserEntity user, Chat chat) {
        ChatEntity privateChat = user.getChats().stream()
                .filter(userChat -> "private".equals(userChat.getType()))
                .findFirst()
                .orElse(null);
        if (privateChat == null) {
            log.debug("User {} private chat is unknown", user.getUserName());
            return;
        }

        try {
            log.debug("Sending private message to user {}", user.getUserName());

            String priceMessageText = "Вас упомянули в чате " + chat.getTitle();
            SendMessage message = SendMessage.builder()
                    .chatId(privateChat.getId())
                    .text(priceMessageText)
                    .parseMode(PARSE_MODE)
                    .disableWebPagePreview(true)
                    .disableNotification(false)
                    .build();

            execute(message);
        } catch (Exception exception) {
            log.error("Failed to send message to private chat {} of user {}",
                    privateChat.getId(), user.getUserName(), exception);
        }
    }

    private boolean ensureLastMentionsLimit(Long chatId, Integer replyToMessageId, boolean notifyIsCaseOfError) {
        if (!lastMentions.containsKey(chatId)) {
            lastMentions.put(chatId, new ArrayList<>());
        }
        Instant now = Instant.now();
        List<Instant> mentions = lastMentions.get(chatId).stream()
                .filter(instant -> instant.isAfter(now.minus(LAST_MENTIONS_DURATION)))
                .collect(Collectors.toCollection(ArrayList::new));
        lastMentions.put(chatId, mentions);
        if (mentions.size() >= LAST_MENTIONS_LIMIT) {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(escapeMarkdownV2(LAST_MENTIONS_LIMIT_MESSAGE))
                    .parseMode(PARSE_MODE)
                    .replyToMessageId(replyToMessageId)
                    .disableWebPagePreview(true)
                    .disableNotification(false)
                    .build();
            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error(e.getMessage(), e);
                if (e.getMessage() == null || !e.getMessage().contains(MESSAGE_DELETED_ERROR)
                        && notifyIsCaseOfError) {
                    send(chatId, escapeMarkdownV2(SEND_FAILED_MESSAGE), false);
                }
            }
            return false;
        }

        lastMentions.get(chatId).add(now);
        return true;
    }

    private void send(Long chatId, String text, boolean notifyIsCaseOfError) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(PARSE_MODE)
                .disableWebPagePreview(true)
                .disableNotification(false)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
            if (notifyIsCaseOfError) {
                send(chatId, SEND_FAILED_MESSAGE, false);
            }
        }
    }

    private String getMessageText(Set<TelegramUserEntity> users) {
        return users.stream()
                .distinct()
                .map(Bot::getMentionString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String getMentionString(TelegramUserEntity user) {
        String name = user.getLastName() == null
                ? user.getFirstName()
                : user.getFirstName() + " " + user.getLastName();
        String escapedName = escapeMarkdownV2(name);
        return "[" + escapedName + "](tg://user?id=" + user.getId() + ")";
    }

    private static String escapeMarkdownV2(String text) {
        return text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!])", "\\\\$1");
    }

    public boolean isUserGroupMember(Long chatId, @NonNull Long userId, Set<String> memberStatuses) {
        GetChatMember getChatMember = new GetChatMember();
        getChatMember.setChatId(String.valueOf(chatId));
        getChatMember.setUserId(userId);

        try {
            ChatMember chatMember = execute(getChatMember);
            String status = chatMember.getStatus();
            return memberStatuses.contains(status);
        } catch (TelegramApiException e) {
            log.warn("Failed to check user {} in chat {}: {}", userId, chatId, e.getMessage());
            return false;
        }
    }

    @Override
    public String getBotPath() {
        return "/bot-webhook";
    }

}