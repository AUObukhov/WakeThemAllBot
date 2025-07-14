package kg.obukhov.wakethemallbot.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.obukhov.wakethemallbot.bot.Bot;
import kg.obukhov.wakethemallbot.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.WebhookInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@ConfigurationPropertiesScan("kg.obukhov.wakethemallbot.config")
public class BotConfiguration {

    @Value("${app.webhook-url}")
    public String webhookUrl;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public Bot bot(StorageService storageService, BotProperties botProperties) throws TelegramApiException {
        Bot bot = new Bot(storageService, botProperties);
        SetWebhook setWebhook = SetWebhook.builder()
                .url(webhookUrl)
                .build();

        bot.setWebhook(setWebhook);

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot, setWebhook);

        WebhookInfo webhookInfo = bot.getWebhookInfo();
        if (webhookInfo == null) {
            log.warn("WebhookInfo is null");
        } else {
            log.info("Webhook URL: {}", webhookInfo.getUrl());
            log.info("Has Custom Certificate: {}", webhookInfo.getHasCustomCertificate());
            log.info("Pending Updates Count: {}", webhookInfo.getPendingUpdatesCount());
            log.info("Last Error Date: {}", webhookInfo.getLastErrorDate());
            log.info("Last Error Message: {}", webhookInfo.getLastErrorMessage());
        }

        return bot;
    }

}