package by.timofeyzaytsev.limitservice.service;

import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;

public interface TransactionService {

    TransactionResponse create(TransactionRequest request);
}
