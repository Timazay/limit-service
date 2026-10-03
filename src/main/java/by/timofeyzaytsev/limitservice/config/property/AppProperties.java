package by.timofeyzaytsev.limitservice.config.property;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    ZoneId timeZone,
    LimitProperties limit,
    ExchangeRateProperties exchangeRate
) {}
