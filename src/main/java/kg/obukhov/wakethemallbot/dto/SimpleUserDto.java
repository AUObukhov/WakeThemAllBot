package kg.obukhov.wakethemallbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.User;

@Data
@NoArgsConstructor
public class SimpleUserDto {
    private Long id;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("username")
    private String userName;

    public SimpleUserDto(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.userName = user.getUserName();
    }

}