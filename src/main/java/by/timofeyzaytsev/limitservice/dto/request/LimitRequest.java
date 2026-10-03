package by.timofeyzaytsev.limitservice.dto.request;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LimitRequest(
    @NotNull(message = "expenseCategory is required")
    ExpenseCategory expenseCategory,

    @DecimalMin(value = "0.01", message = "limitSum must be positive")
    BigDecimal limitSum
) {}
