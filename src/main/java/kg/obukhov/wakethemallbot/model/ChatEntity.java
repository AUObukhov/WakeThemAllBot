package kg.obukhov.wakethemallbot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@Entity(name = "chat")
public class ChatEntity {

    @Id
    @Column
    private Long id;

    @Column
    private String type;

    @Column
    private String title;

    @ManyToMany(mappedBy = "chats", fetch = FetchType.EAGER)
    @EqualsAndHashCode.Exclude
    private Set<TelegramUserEntity> users = new HashSet<>();

}