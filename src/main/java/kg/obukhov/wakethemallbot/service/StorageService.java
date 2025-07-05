package kg.obukhov.wakethemallbot.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.obukhov.wakethemallbot.exception.StorageException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class StorageService {

    private static final String USERS_KEY = "users";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JavaType mapType;

    public StorageService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        mapType = objectMapper.getTypeFactory().constructMapType(
                Map.class,
                objectMapper.getTypeFactory().constructType(Long.class),
                objectMapper.getTypeFactory().constructCollectionType(Set.class, User.class)
        );
    }

    public Map<Long, Set<User>> readUsers() {
        try {
            String json = redisTemplate.opsForValue().get(USERS_KEY);
            return objectMapper.readValue(json, mapType);
        } catch (Exception e) {
            throw new StorageException("Failed to load users", e);
        }
    }

    public void addUserToChat(long chatId, User user) {
        try {
            String json = redisTemplate.opsForValue().get(USERS_KEY);

            Map<Long, Set<User>> chatMap = StringUtils.isBlank(json)
                    ? new HashMap<>()
                    : objectMapper.readValue(json, mapType);

            chatMap.computeIfAbsent(chatId, key -> new HashSet<>()).add(user);

            String updatedJson = objectMapper.writeValueAsString(chatMap);
            redisTemplate.opsForValue().set(USERS_KEY, updatedJson);
        } catch (Exception e) {
            throw new StorageException("Failed to add user " + user.getUserName() + " to chat " + chatId, e);
        }
    }

}