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

@Service
@RequiredArgsConstructor
public class ChatUserService {

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final ChatMapper chatMapper;
    private final UserMapper userMapper;

    public ChatEntity findOrMapChat(Chat chat) {
        return chatRepository.findById(chat.getId())
                .orElseGet(() -> chatMapper.toEntity(chat));
    }

    public void saveChatAndUser(Chat chat, User user) {
        if (!user.getIsBot()) {
            ChatEntity chatEntity = chatRepository.findById(chat.getId())
                    .orElseGet(() -> chatRepository.save(chatMapper.toEntity(chat)));

            TelegramUserEntity telegramUserEntity = userRepository.findById(user.getId())
                    .orElseGet(() -> userMapper.toEntity(user));
            telegramUserEntity.getChats().add(chatEntity);
            userRepository.save(telegramUserEntity);
        }
    }
}