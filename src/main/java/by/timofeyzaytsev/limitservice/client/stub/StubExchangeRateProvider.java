package by.timofeyzaytsev.limitservice.client.stub;

import by.timofeyzaytsev.limitservice.client.ExchangeRateProvider;
import by.timofeyzaytsev.limitservice.client.RateQuote;
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
    private static final BigDecimal RUB_RATE = new BigDecimal("83.70");
    private static final BigDecimal BYN_RATE = new BigDecimal("3.01");
    private static final BigDecimal OTHER_RATE = new BigDecimal("40.33");

    @Override
    public RateQuote fetchQuote(String currencyPair, LocalDate date) {
        return new RateQuote(rateFor(quote(currencyPair)), null);
    }

    /**
     * Курс запрашивается в направлении пары, поэтому у пары {@code USD/XXX}
     * ответ зависит только от её котируемой валюты.
     */
    private BigDecimal rateFor(String quote) {
        return switch (quote) {
            case "USD" -> BigDecimal.ONE;
            case "KZT" -> KZT_RATE;
            case "RUB" -> RUB_RATE;
            case "BYN" -> BYN_RATE;
            default -> OTHER_RATE;
        };
    }

    private String quote(String currencyPair) {
        return currencyPair.substring(currencyPair.indexOf('/') + 1);
    }
}