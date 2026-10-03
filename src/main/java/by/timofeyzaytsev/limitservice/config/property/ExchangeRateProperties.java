package by.timofeyzaytsev.limitservice.config.property;

import java.time.Duration;

public record ExchangeRateProperties(
    String provider,
    TwelveDataProperties twelvedata
) {

    public record TwelveDataProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetries,
        Duration retryDelay,
        double retryMultiplier,
        Duration retryMaxDelay
    ) {

    }
}
