package kg.obukhov.wakethemallbot.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.obukhov.wakethemallbot.dto.UserDto;
import kg.obukhov.wakethemallbot.exception.StorageException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.HashSet;
import java.util.Set;

@Service
public class StorageService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JavaType setType;

    public StorageService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        setType = objectMapper.getTypeFactory().constructCollectionType(Set.class, UserDto.class);
    }

    public Set<UserDto> readUsers(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return objectMapper.readValue(json, setType);
        } catch (Exception e) {
            throw new StorageException("Failed to load users", e);
        }
    }

    public void addUserToChat(String key, User user) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            UserDto userDto = new UserDto(user);

            Set<UserDto> users = StringUtils.isBlank(json)
                    ? new HashSet<>()
                    : objectMapper.readValue(json, setType);

            boolean userStored = users.stream()
                    .anyMatch(dto -> dto.getId().equals(userDto.getId()));
            if (userStored) {
                return;
            }

            users.add(userDto);

            String updatedJson = objectMapper.writeValueAsString(users);
            redisTemplate.opsForValue().set(key, updatedJson);
        } catch (Exception e) {
            throw new StorageException("Failed to add user " + user.getUserName() + " to chat " + key, e);
        }
    }

}