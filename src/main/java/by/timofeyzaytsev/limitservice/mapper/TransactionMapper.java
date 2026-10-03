package by.timofeyzaytsev.limitservice.mapper;

import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;
import by.timofeyzaytsev.limitservice.model.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionResponse toTransactionResponse(Transaction transaction);
}
