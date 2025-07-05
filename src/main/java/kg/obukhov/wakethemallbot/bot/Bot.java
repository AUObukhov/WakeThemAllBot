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
            User from = message.getFrom();
            saveUser(chatId, from);

            if (message.hasText()) {
                String text = message.getText();

                if (StringUtils.containsAnyIgnoreCase(text, TRIGGER_COMMANDS)) {
                    sendMentionAll(chatId, from);
                }
            }
        }
    }

    private void saveUser(Long chatId, User user) {
        if (user.getIsBot()) {
            return;
        }

        storageService.addUserToChat(chatId, user);
    }

    private void sendMentionAll(Long chatId, User from) {
        Set<User> chatUsers = storageService.readUsers().get(chatId);
        chatUsers = chatUsers.stream()
                .filter(user -> !from.getUserName().equals(user.getUserName()))
                .distinct()
                .filter(user -> isUserInGroup(chatId, user.getId()))
                .collect(Collectors.toSet());

        if (chatUsers.isEmpty()) {
            sendTextMessage(chatId, "Нет активных пользователей для упоминания");
            return;
        }

        String text = getMessageText(chatId, from, chatUsers);

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .disableWebPagePreview(true)
                .disableNotification(false)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
            sendTextMessage(chatId, "Мой автор криворукий, поэтому я не смог отправить уведомление");
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