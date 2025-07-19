package kg.obukhov.wakethemallbot.interfaces;

import kg.obukhov.wakethemallbot.model.TelegramUserEntity;
import org.mapstruct.Mapper;
import org.telegram.telegrambots.meta.api.objects.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    TelegramUserEntity toEntity(User user);

}