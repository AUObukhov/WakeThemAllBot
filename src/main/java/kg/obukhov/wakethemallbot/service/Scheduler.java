package kg.obukhov.wakethemallbot.service;

import kg.obukhov.wakethemallbot.bot.Bot;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Dummy scheduler to make request outside.
 * Needed to simulate activity for hosting which shuts down the application after some period or inactivity
 */
@Component
@RequiredArgsConstructor
public class Scheduler {

    private final Bot bot;

    @Scheduled(fixedRateString = "${app.scheduler.rate}")
    public void execute() throws TelegramApiException {
        bot.execute(new GetMe());
    }

}