package by.timofeyzaytsev.limitservice.client;

/**
 * Источник биржевых курсов.
 *
 * <p>Провайдер только ходит во внешний API: он не знает про базу и не решает,
 * брать курс из хранилища или запросить заново. Это разбирает
 * {@code ExchangeRateService}.</p>
 */
public interface ExchangeRateProvider {

    /**
     * Дневные котировки пары в направлении самой пары: для {@code KZT/USD} это
     * сколько KZT отдают за 1 USD. Провайдер отвечает за приведение курса к
     * этому направлению, даже если внешний API отдаёт пару наоборот.
     *
     * @param currencyPair пара в виде {@code AAA/BBB}
     * @param date         дата, на которую нужен курс
     */
    RateQuote fetchQuote(String currencyPair, java.time.LocalDate date);
}