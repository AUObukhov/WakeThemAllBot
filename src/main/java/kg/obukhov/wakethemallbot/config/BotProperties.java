package kg.obukhov.wakethemallbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("bot")
public class BotProperties {

    private String username;

    private String token;

}
