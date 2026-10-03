package by.timofeyzaytsev.limitservice.dto.response;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    String accountFrom,
    String accountTo,
    String currencyShortname,
    BigDecimal sum,
    BigDecimal sumUsd,
    ExpenseCategory expenseCategory,
    OffsetDateTime datetime,
    boolean limitExceeded
) {

}
