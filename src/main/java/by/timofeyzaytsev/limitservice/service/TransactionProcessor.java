package by.timofeyzaytsev.limitservice.service;

import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;
import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

public interface TransactionProcessor {

    /**
     * Транзакционная часть приёма: блокировка месяца, расчёт остатка
     * лимита и запись транзакции с проставленным флагом limit_exceeded.
     *
     * @param exchangeRate курс, по которому посчитана сумма в USD; может быть
     *                     {@code null} для USD, курс которого не хранится
     */
    TransactionResponse record(
        TransactionRequest request,
        BigDecimal sumUsd,
        ZonedDateTime zoned,
        ExchangeRate exchangeRate
    );
}
