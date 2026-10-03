package by.timofeyzaytsev.limitservice.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Провайдер курсов валют.
 * Возвращает курс валюты к USD на указанную дату.
 *
 * Контракт: rate = сколько единиц валюты за 1 USD.
 * Пример: KZT/USD = 450.5 → за 1 USD дают 450.5 KZT.
 */
public interface ExchangeRateProvider {

    /**
     * @param currency валюта (KZT, RUB, ...)
     * @param date     дата, на которую нужен курс
     * @return курс к USD (сколько единиц валюты за 1 USD)
     */
    BigDecimal getRate(String currency, LocalDate date);
}
