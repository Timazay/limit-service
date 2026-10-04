package by.timofeyzaytsev.limitservice.service;

import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

public interface TransactionProcessor {

    /**
     * Транзакционная часть приёма: блокировка месяца, расчёт остатка
     * лимита и запись транзакции с проставленным флагом limit_exceeded.
     */
    TransactionResponse record(TransactionRequest request, BigDecimal sumUsd, ZonedDateTime zoned);
}
