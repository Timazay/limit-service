package by.timofeyzaytsev.limitservice.client;

import java.math.BigDecimal;

/**
 * Дневная котировка валютной пары в направлении самой пары.
 *
 * @param close         закрытие за день; на выходных и праздниках отсутствует
 * @param previousClose закрытие предыдущего торгового дня
 */
public record RateQuote(BigDecimal close, BigDecimal previousClose) {

    /**
     * ТЗ требует считать по закрытию, а если закрытия за текущий день нет —
     * по последнему доступному закрытию.
     */
    public BigDecimal rate() {
        return close != null ? close : previousClose;
    }
}