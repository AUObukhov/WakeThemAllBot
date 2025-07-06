package kg.obukhov.wakethemallbot.bot;

import kg.obukhov.wakethemallbot.config.BotProperties;
import kg.obukhov.wakethemallbot.service.StorageService;
import lombok.Getter;
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

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Bot extends TelegramLongPollingBot {

    private static final String[] TRIGGER_COMMANDS = {"/all", "@all"};
    public static final Set<String> ALL_GROUP_MEMBER_STATUSES = Set.of("member", "administrator", "creator");
    public static final String MESSAGE_DELETED_ERROR = "[400] Bad Request: message to be replied not found";
    public static final String SEND_FAILED_MESSAGE = "Мой автор криворукий, поэтому я не смог отправить уведомление";

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

                if (StringUtils.containsAnyIgnoreCase(text, TRIGGER_COMMANDS)) {
                    sendMentionAll(chatId, message.getMessageId(), author);
                }
            }
        }
    }

    private void saveUser(long chatId, User user) {
        if (!user.getIsBot()) {
            storageService.addUserToChat(String.valueOf(chatId), user);
        }
    }

    private void sendMentionAll(long chatId, Integer replyToMessageId, User author) {
        Set<User> chatUsers = storageService.readUsers(String.valueOf(chatId));
        chatUsers = chatUsers.stream()
                .filter(user -> !author.getUserName().equals(user.getUserName()))
                .distinct()
                .filter(user -> isUserInGroup(chatId, user.getId()))
                .collect(Collectors.toSet());

        if (chatUsers.isEmpty()) {
            sendTextMessage(chatId, "Нет активных пользователей для упоминания");
            return;
        }

        String text = getMessageText(chatId, author, chatUsers);

        reply(chatId, text, replyToMessageId);
    }

    private void reply(long chatId, String text, Integer replyToMessageId) {
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
                send(chatId, text);
            } else {
                log.error(e.getMessage(), e);
                sendTextMessage(chatId, SEND_FAILED_MESSAGE);
            }
        }
    }

    private void send(long chatId, String text) {
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
            sendTextMessage(chatId, SEND_FAILED_MESSAGE);
        }
    }

    private String getMessageText(Long chatId, User from, Set<User> users) {
        return users.stream()
                .filter(user -> !from.getUserName().equals(user.getUserName()))
                .distinct()
                .filter(user -> isUserInGroup(chatId, user.getId()))
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

    private void sendTextMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }

    public boolean isUserInGroup(Long chatId, Long userId) {
        GetChatMember getChatMember = new GetChatMember();
        getChatMember.setChatId(chatId.toString());
        getChatMember.setUserId(userId);

        try {
            ChatMember chatMember = execute(getChatMember);
            String status = chatMember.getStatus();
            return ALL_GROUP_MEMBER_STATUSES.contains(status);
        } catch (TelegramApiException e) {
            log.warn("Failed to check user {} in chat {}: {}", userId, chatId, e.getMessage());
            return false;
        }
    }

}