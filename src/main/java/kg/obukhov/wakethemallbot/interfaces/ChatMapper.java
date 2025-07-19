package kg.obukhov.wakethemallbot.interfaces;

import kg.obukhov.wakethemallbot.model.ChatEntity;
import org.mapstruct.Mapper;
import org.telegram.telegrambots.meta.api.objects.Chat;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    ChatEntity toEntity(Chat user);

}