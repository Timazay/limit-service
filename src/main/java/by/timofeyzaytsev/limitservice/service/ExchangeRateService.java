package by.timofeyzaytsev.limitservice.service;

import java.time.LocalDate;

public interface ExchangeRateService {

    /**
     * Курс валюты к USD на дату: сколько единиц валюты отдают за 1 USD.
     * Сначала берётся сохранённый курс, и только если его нет — из внешнего API.
     *
     * <p>Возвращается вместе с сохранённой строкой курса, чтобы транзакция могла
     * сослаться на тот курс, по которому она была посчитана: по одной сумме
     * в USD восстановить его однозначно нельзя.</p>
     */
    ResolvedRate resolve(String currency, LocalDate date);
}
