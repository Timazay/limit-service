package by.timofeyzaytsev.limitservice.service;

import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import java.math.BigDecimal;

/**
 * Курс, по которому пересчитывается транзакция, и сохранённая строка курса.
 *
 * @param rate         курс к USD, всегда присутствует и больше нуля
 * @param exchangeRate строка {@code exchange_rates}, по которой курс взят, либо
 *                     {@code null} для USD: он не запрашивается и не хранится
 */
public record ResolvedRate(BigDecimal rate, ExchangeRate exchangeRate) {

    /**
     * Курс уже сохранён, поэтому сумма в USD считается по нему же, что и раньше.
     */
    public static ResolvedRate fromStored(ExchangeRate stored) {
        return new ResolvedRate(stored.effectiveRate(), stored);
    }
}
