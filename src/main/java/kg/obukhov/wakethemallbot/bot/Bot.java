package kg.obukhov.wakethemallbot.bot;

import kg.obukhov.wakethemallbot.config.BotProperties;
import kg.obukhov.wakethemallbot.dto.SimpleUserDto;
import kg.obukhov.wakethemallbot.service.StorageService;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
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
public class Bot extends TelegramLongPollingBot {

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
    private static final String PERSONAL_CHAT_MESSAGE = "Бот предназначен для групповых чатов";
    private static final String MESSAGE_TEXT_TO_DELETE = "delete this message";

    private final Map<Long, List<Instant>> lastMentions;

    private final StorageService storageService;

    @Getter
    private final String botUsername;

    public Bot(StorageService storageService, BotProperties properties) {
        super(properties.getToken());
        this.storageService = storageService;
        this.botUsername = properties.getUsername();
        this.lastMentions = new HashMap<>();
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.debug("Update received: {}", update);

        if (update.hasMyChatMember()) {
            Long chatId = update.getMyChatMember().getChat().getId();
            User from = update.getMyChatMember().getFrom();
            saveUser(chatId, from);
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            if (isGroupChat(chatId)) {
                if (message.getText().contains(MESSAGE_TEXT_TO_DELETE)) {
                    deleteMessage(chatId, message);
                    return;
                }
                User author = message.getFrom();
                saveUser(chatId, author);
                sendMentions(message, chatId, author);
            } else {
                sendPersonalChatMessage(chatId);
            }
        }
    }

    private static boolean isGroupChat(Long chatId) {
        return chatId < 0;
    }

    private void deleteMessage(Long chatId, Message message) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId.toString());
        deleteMessage.setMessageId(message.getMessageId());

        try {
            execute(deleteMessage);
        } catch (Exception e) {
            log.warn("Failed to delete message {} ({}) from chat {}: {}",
                    message.getMessageId(), message.getText(), chatId, e.getMessage());
        }
    }

    private void sendPersonalChatMessage(Long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(PERSONAL_CHAT_MESSAGE)
                .parseMode(PARSE_MODE)
                .disableWebPagePreview(true)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void saveUser(Long chatId, User user) {
        if (!user.getIsBot()) {
            storageService.addUserToChat(String.valueOf(chatId), user);
        }
    }

    private void sendMentions(Message message, Long chatId, User author) {
        if (message.hasText()) {
            String text = message.getText();

            if (StringUtils.containsAnyIgnoreCase(text, MENTION_ALL_COMMANDS)) {
                sendMentions(chatId, message.getMessageId(), author, ALL_GROUP_MEMBER_STATUSES);
            } else if (StringUtils.containsAnyIgnoreCase(text, MENTION_ADMIN_COMMANDS)) {
                sendMentions(chatId, message.getMessageId(), author, ADMIN_GROUP_MEMBER_STATUSES);
            }
        }
    }

    private void sendMentions(Long chatId, Integer replyToMessageId, User author, Set<String> memberStatuses) {
        Set<SimpleUserDto> chatUsers = storageService.readUsers(String.valueOf(chatId));
        chatUsers = chatUsers.stream()
                .filter(user -> !author.getUserName().equals(user.getUserName()))
                .distinct()
                .filter(user -> isUserGroupMember(chatId, user.getId(), memberStatuses))
                .collect(Collectors.toSet());

        if (chatUsers.isEmpty()) {
            reply(chatId, escapeMarkdownV2(NO_MEMBERS_MESSAGE), replyToMessageId, false);
            return;
        }

        String text = getMessageText(chatUsers);

        reply(chatId, text, replyToMessageId, true);
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

    private String getMessageText(Set<SimpleUserDto> users) {
        return users.stream()
                .distinct()
                .map(Bot::getMentionString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String getMentionString(SimpleUserDto user) {
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

}