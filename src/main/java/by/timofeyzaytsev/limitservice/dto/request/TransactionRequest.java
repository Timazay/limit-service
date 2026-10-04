package by.timofeyzaytsev.limitservice.dto.request;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionRequest(
    @NotBlank(message = "accountFrom is required")
    @Pattern(regexp = "\\d{10}", message = "accountFrom must be 10 digits")
    String accountFrom,

    @NotBlank(message = "accountTo is required")
    @Pattern(regexp = "\\d{10}", message = "accountTo must be 10 digits")
    String accountTo,

    @NotBlank(message = "currencyShortname is required")
    @Size(min = 3, max = 3, message = "currencyShortname must be exactly 3 characters")
    String currencyShortname,

    @NotNull(message = "sum is required")
    @DecimalMin(value = "0.01", message = "sum must be positive")
    @Digits(integer = 17, fraction = 2, message = "sum must have at most 2 decimal places")
    BigDecimal sum,

    @NotNull(message = "expenseCategory is required")
    ExpenseCategory expenseCategory,

    @NotNull(message = "datetime is required")
    OffsetDateTime datetime
) {

}
