package kg.obukhov.wakethemallbot.config;

import kg.obukhov.wakethemallbot.bot.Bot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@ConfigurationPropertiesScan("kg.obukhov.wakethemallbot.config")
public class BotConfiguration {

    @Bean
    public Bot bot(BotProperties botProperties) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        Bot bot = new Bot(botProperties);
        botsApi.registerBot(bot);

        log.info("{} started!", botProperties.getUsername());

        return bot;
    }

}
