package kg.obukhov.wakethemallbot.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.obukhov.wakethemallbot.bot.Bot;
import kg.obukhov.wakethemallbot.service.ChatUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@EnableAsync
@Configuration
@ConfigurationPropertiesScan("kg.obukhov.wakethemallbot.config")
@EntityScan("kg.obukhov.wakethemallbot.model")
@EnableScheduling
public class BotConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public Bot bot(ChatUserService chatUserService, BotProperties botProperties) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        Bot bot = new Bot(chatUserService, botProperties);
        botsApi.registerBot(bot);

        bot.registerCommands();

        log.info("{} started!", botProperties.getUsername());

        return bot;
    }

}