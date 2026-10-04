package by.timofeyzaytsev.limitservice.mapper;

import by.timofeyzaytsev.limitservice.dto.response.ExceededTransactionResponse;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;
import by.timofeyzaytsev.limitservice.model.Transaction;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionResponse toTransactionResponse(Transaction transaction);

    /**
     * Три лимитовых поля берутся из ссылки {@code limit} самой транзакции:
     * переустановка лимита позже не должна менять ответ на вопрос о том, какой
     * лимит был превышен тогда.
     *
     * <p>У транзакции без сохранённой ссылки на лимит превышение считалось по
     * дефолтным 1000 USD, и в базе её нет: такие поля остаются пустыми.</p>
     */
    @Mapping(target = "limitSum", source = "limit.limitSum")
    @Mapping(target = "limitDatetime", source = "limit.limitDatetime")
    @Mapping(target = "limitCurrencyShortname", source = "limit.limitCurrencyShortname")
    ExceededTransactionResponse toExceededTransactionResponse(Transaction transaction);

    List<ExceededTransactionResponse> toExceededResponseList(List<Transaction> transactions);
}
