package kg.obukhov.wakethemallbot.service;

import kg.obukhov.wakethemallbot.interfaces.ChatMapper;
import kg.obukhov.wakethemallbot.interfaces.UserMapper;
import kg.obukhov.wakethemallbot.model.ChatEntity;
import kg.obukhov.wakethemallbot.model.TelegramUserEntity;
import kg.obukhov.wakethemallbot.repository.ChatRepository;
import kg.obukhov.wakethemallbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatUserService {

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final ChatMapper chatMapper;
    private final UserMapper userMapper;

    public List<TelegramUserEntity> findAllByChatId(Long chatId) {
        return userRepository.findAllByChatId(chatId);
    }

    public void saveChatAndUser(Chat chat, User user) {
        if (user.getIsBot()) {
            return;
        }

        TelegramUserEntity telegramUserEntity = userRepository.findById(user.getId())
                .orElseGet(() -> userMapper.toEntity(user));
        boolean userNotSavedToChat = telegramUserEntity.getChats().stream()
                .map(ChatEntity::getId)
                .noneMatch(chat.getId()::equals);
        if (userNotSavedToChat) {
            telegramUserEntity.getChats().add(chatMapper.toEntity(chat));
            userRepository.save(telegramUserEntity);
        }
    }

    public void removeUserFromChat(TelegramUserEntity user, Chat chat) {
        ChatEntity chatEntity = chatRepository.findById(chat.getId())
                .orElse(null);
        user.getChats().remove(chatEntity);
        userRepository.save(user);
    }

}