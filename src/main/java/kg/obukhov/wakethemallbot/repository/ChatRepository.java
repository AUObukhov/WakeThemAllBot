package kg.obukhov.wakethemallbot.repository;

import kg.obukhov.wakethemallbot.model.ChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends JpaRepository<ChatEntity, Long> {
}