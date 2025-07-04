package kg.obukhov.wakethemallbot.bot;

import kg.obukhov.wakethemallbot.config.BotProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
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
@Component
public class Bot extends TelegramLongPollingBot {

    private static final List<String> HARDCODED_USERNAMES = List.of(
            "DC_kzhakypova",
            "DC_aibraimova",
            "DC_rseitbelialov",
            "DC_mkazachukhin",
            "DC_rabdilamituulu",
            "dc_ksaryeva",
            "DC_Kfedorenko",
            "DC_ertashtanbekov",
            "DC_adalymbekov",
            "DC_erashidova",
            "DC_asutsepina",
            "DC_basakeev",
            "kanaabakir",
            "DC_ibolotov",
            "dc_amamytova",
            "dc_aiatchenko",
            "dc_nporvani",
            "DC_ARashidov",
            "DC_pimangazieva",
            "dc_razimova",
            "DC_AShkuratenko",
            "DC_nchybyeva"
    );
    public static final Set<Long> ALLOWED_CHAT_IDS = Set.of(-4894980653L, -4836567752L);
    public static final long HARDCODED_CHAT_ID = -4836567752L;
    public static final String[] TRIGGER_COMMANDS = {"/all", "@all"};

    private final Map<Long, Set<User>> activeUsers = new HashMap<>();

    @Getter
    private final String botUsername;

    public Bot(BotProperties properties) {
        super(properties.getToken());
        this.botUsername = properties.getUsername();
        addHardcodedUsers();
    }

    private void addHardcodedUsers() {
        Set<User> users = new HashSet<>();
        for (int i = 0; i < HARDCODED_USERNAMES.size(); i++) {
            User user = new User();
            user.setIsBot(false);
            user.setUserName(HARDCODED_USERNAMES.get(i));
            user.setId((long) i);
            users.add(user);
        }
        activeUsers.put(HARDCODED_CHAT_ID, users);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMyChatMember()) {
            long chatId = update.getMyChatMember().getChat().getId();
            if (ALLOWED_CHAT_IDS.contains(chatId)) {
                User from = update.getMyChatMember().getFrom();
                saveActiveUser(chatId, from);
            }
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();
            if (ALLOWED_CHAT_IDS.contains(chatId)) {
                User from = message.getFrom();
                saveActiveUser(chatId, from);

                if (message.hasText()) {
                    String text = message.getText();

                    if (StringUtils.containsAnyIgnoreCase(text, TRIGGER_COMMANDS)) {
                        sendMentionAll(chatId, from);
                    }
                }
            }
        }
    }

    private void saveActiveUser(Long chatId, User user) {
        if (user.getIsBot()) {
            return;
        }

        Set<User> users = activeUsers.get(chatId);
        if (users == null) {
            users = new HashSet<>();
        }
        users.add(user);

        activeUsers.put(chatId, users);
    }

    private void sendMentionAll(Long chatId, User from) {
        Set<User> chatUsers = activeUsers.get(chatId);

        if (chatUsers == null || chatUsers.isEmpty()) {
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
                .filter(user -> isUserStillInGroup(chatId, user.getId()))
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
        return text.replaceAll("([_\\*\\[\\]\\(\\)~`>#+\\-=|{}.!])", "\\\\$1");
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

    public boolean isUserStillInGroup(Long chatId, Long userId) {
        if (userId < HARDCODED_USERNAMES.size()) {
            return true;
        }

        GetChatMember getChatMember = new GetChatMember();
        getChatMember.setChatId(chatId.toString());
        getChatMember.setUserId(userId);

        try {
            ChatMember chatMember = execute(getChatMember);
            String status = chatMember.getStatus();
            return List.of("member", "administrator", "creator").contains(status);
        } catch (TelegramApiException e) {
            log.warn("Failed to check user {} in chat {}: {}", userId, chatId, e.getMessage());
            return false;
        }
    }

}