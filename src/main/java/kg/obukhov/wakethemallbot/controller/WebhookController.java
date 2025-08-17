package kg.obukhov.wakethemallbot.controller;

import kg.obukhov.wakethemallbot.bot.Bot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final Bot bot;

    @PostMapping("/callback/bot-webhook")
    public void onUpdateReceived(@RequestBody Update update) {
        log.debug("Webhook update received: {}", update);
        bot.onWebhookUpdateReceived(update);
    }

}
