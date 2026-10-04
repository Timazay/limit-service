package by.timofeyzaytsev.limitservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExchangeRateService {

    /**
     * Курс валюты к USD на дату: сколько единиц валюты отдают за 1 USD.
     * Сначала берётся сохранённый курс, и только если его нет — из внешнего API.
     */
    BigDecimal getRate(String currency, LocalDate date);
}
