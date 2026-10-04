package by.timofeyzaytsev.limitservice.service;

import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.ExceededTransactionResponse;
import by.timofeyzaytsev.limitservice.dto.response.PageResponse;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;

public interface TransactionService {

    TransactionResponse create(TransactionRequest request);

    /**
     * Транзакции клиента, превысившие месячный лимит, с указанием лимита,
     * который был превышен.
     */
    PageResponse<ExceededTransactionResponse> findExceeded(String accountFrom, int page, int size);
}
