package by.timofeyzaytsev.limitservice.dto.request;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionRequest(
    @NotNull(message = "accountFrom is required")
    UUID accountFrom,

    @NotNull(message = "accountTo is required")
    UUID accountTo,

    @NotNull(message = "currencyShortname is required")
    @Size(min = 3, max = 3, message = "currencyShortname must be exactly 3 characters")
    String currencyShortname,

    @NotNull(message = "sum is required")
    @DecimalMin(value = "0.01", message = "sum must be positive")
    BigDecimal sum,

    @NotNull(message = "expenseCategory is required")
    ExpenseCategory expenseCategory,

    @NotNull(message = "datetime is required")
    OffsetDateTime datetime
) {

}
