package by.timofeyzaytsev.limitservice.dto.response;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LimitResponse(
    UUID id,
    String accountFrom,
    ExpenseCategory expenseCategory,
    BigDecimal limitSum,
    OffsetDateTime limitDatetime,
    String limitCurrencyShortname
) {}
