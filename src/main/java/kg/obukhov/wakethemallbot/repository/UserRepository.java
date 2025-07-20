package kg.obukhov.wakethemallbot.repository;

import kg.obukhov.wakethemallbot.model.TelegramUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<TelegramUserEntity, Long> {

    @Query("SELECT u FROM telegram_user u JOIN u.chats c WHERE c.id = :chatId")
    List<TelegramUserEntity> findAllByChatId(@Param("chatId") Long chatId);

}