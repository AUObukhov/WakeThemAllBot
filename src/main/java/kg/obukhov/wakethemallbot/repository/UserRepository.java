package kg.obukhov.wakethemallbot.repository;

import kg.obukhov.wakethemallbot.model.TelegramUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<TelegramUserEntity, Long> {
}