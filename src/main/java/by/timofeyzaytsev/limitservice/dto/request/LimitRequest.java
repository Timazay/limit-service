package by.timofeyzaytsev.limitservice.dto.request;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record LimitRequest(
    @NotBlank(message = "accountFrom is required")
    @Pattern(regexp = "\\d{10}", message = "accountFrom must be 10 digits")
    String accountFrom,

    @NotNull(message = "expenseCategory is required")
    ExpenseCategory expenseCategory,

    @NotNull(message = "limitSum is required")
    @DecimalMin(value = "0.01", message = "limitSum must be positive")
    @Digits(integer = 17, fraction = 2, message = "limitSum must have at most 2 decimal places")
    BigDecimal limitSum
) {}
