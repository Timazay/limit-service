package by.timofeyzaytsev.limitservice.client.stub;

import by.timofeyzaytsev.limitservice.client.ExchangeRateProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Заглушка провайдера курсов.
 * Возвращает фиксированные курсы для тестов и разработки.
 * Активируется, если app.exchange-rate.provider=stub (по умолчанию).
 */
@Component
@ConditionalOnProperty(
    name = "app.exchange-rate.provider",
    havingValue = "stub",
    matchIfMissing = true
)
public class StubExchangeRateProvider implements ExchangeRateProvider {

    private static final BigDecimal KZT_RATE = new BigDecimal("450.50");
    private static final BigDecimal RUB_RATE = new BigDecimal("90.25");
    private static final BigDecimal USD_RATE = BigDecimal.ONE;

    @Override
    public BigDecimal getRate(String currency, LocalDate date) {
        return switch (currency) {
            case "USD" -> USD_RATE;
            case "KZT" -> KZT_RATE;
            case "RUB" -> RUB_RATE;
            default -> new BigDecimal("40.33");
        };
    }
}
