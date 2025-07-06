package kg.obukhov.wakethemallbot.bot;

import kg.obukhov.wakethemallbot.config.BotProperties;
import kg.obukhov.wakethemallbot.service.StorageService;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Set;
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

    private final StorageService storageService;

    @Getter
    private final String botUsername;

    public Bot(StorageService storageService, BotProperties properties) {
        super(properties.getToken());
        this.storageService = storageService;
        this.botUsername = properties.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.debug("Update received: {}", update);

        if (update.hasMyChatMember()) {
            long chatId = update.getMyChatMember().getChat().getId();
            User from = update.getMyChatMember().getFrom();
            saveUser(chatId, from);
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();
            User author = message.getFrom();
            saveUser(chatId, author);

            if (message.hasText()) {
                String text = message.getText();

                if (StringUtils.containsAnyIgnoreCase(text, MENTION_ALL_COMMANDS)) {
                    sendMentions(chatId, message.getMessageId(), author, ALL_GROUP_MEMBER_STATUSES);
                } else if (StringUtils.containsAnyIgnoreCase(text, MENTION_ADMIN_COMMANDS)) {
                    sendMentions(chatId, message.getMessageId(), author, ADMIN_GROUP_MEMBER_STATUSES);
                }
            }
        }
    }

    private void saveUser(long chatId, User user) {
        if (!user.getIsBot()) {
            storageService.addUserToChat(String.valueOf(chatId), user);
        }
    }

    private void sendMentions(long chatId, Integer replyToMessageId, User author, Set<String> memberStatuses) {
        Set<User> chatUsers = storageService.readUsers(String.valueOf(chatId));
        chatUsers = chatUsers.stream()
                .filter(user -> !author.getUserName().equals(user.getUserName()))
                .distinct()
                .filter(user -> isUserGroupMember(chatId, user.getId(), memberStatuses))
                .collect(Collectors.toSet());

        if (chatUsers.isEmpty()) {
            reply(chatId, NO_MEMBERS_MESSAGE, replyToMessageId, false);
            return;
        }

        String text = getMessageText(chatUsers);

        reply(chatId, text, replyToMessageId, true);
    }

    private void reply(long chatId, String text, Integer replyToMessageId, boolean notifyIsCaseOfError) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyToMessageId(replyToMessageId)
                .parseMode("MarkdownV2")
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
                    send(chatId, SEND_FAILED_MESSAGE, false);
                }
            }
        }
    }

    private void send(long chatId, String text, boolean notifyIsCaseOfError) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("MarkdownV2")
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

    private String getMessageText(Set<User> users) {
        return users.stream()
                .distinct()
                .map(Bot::getMentionString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String getMentionString(User user) {
        if (StringUtils.isEmpty(user.getUserName())) {
            String name = user.getFirstName();
            String escapedName = escapeMarkdownV2(name);
            return "[" + escapedName + "](tg://user?id=" + user.getId() + ")";
        } else {
            return "@" + escapeMarkdownV2(user.getUserName());
        }
    }

    private static String escapeMarkdownV2(String text) {
        return text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!])", "\\\\$1");
    }

    public boolean isUserGroupMember(long chatId, @NonNull Long userId, Set<String> memberStatuses) {
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