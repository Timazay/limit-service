package by.timofeyzaytsev.limitservice.dto.request;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Расходная операция в валюте счёта")
public record TransactionRequest(

    @Schema(description = "Счёт клиента, ровно 10 цифр", example = "0000000123")
    @NotBlank(message = "accountFrom is required")
    @Pattern(regexp = "\\d{10}", message = "accountFrom must be 10 digits")
    String accountFrom,

    @Schema(description = "Счёт контрагента, ровно 10 цифр", example = "9999999999")
    @NotBlank(message = "accountTo is required")
    @Pattern(regexp = "\\d{10}", message = "accountTo must be 10 digits")
    String accountTo,

    @Schema(description = "Код валюты по ISO 4217, три заглавные буквы",
        example = "KZT", allowableValues = {"KZT", "RUB", "USD"})
    @NotBlank(message = "currencyShortname is required")
    @Size(min = 3, max = 3, message = "currencyShortname must be exactly 3 characters")
    String currencyShortname,

    @Schema(description = """
        Сумма операции в валюте счёта, два знака после точки.
        В USD она пересчитывается по курсу закрытия за день операции, поэтому
        limit_exceeded считается именно от суммы в USD, а не от этой суммы.""",
        example = "10000.45")
    @NotNull(message = "sum is required")
    @DecimalMin(value = "0.01", message = "sum must be positive")
    @Digits(integer = 17, fraction = 2, message = "sum must have at most 2 decimal places")
    BigDecimal sum,

    @Schema(description = """
        Категория расхода. В API принимается и возвращается в нижнем регистре,
        в базу записывается в верхнем. На лимит влияет раздельно:
        у товаров и услуг независимые остатки.""",
        example = "product", allowableValues = {"product", "service"})
    @NotNull(message = "expenseCategory is required")
    ExpenseCategory expenseCategory,

    @Schema(description = """
        Момент операции со смещением часового пояса. Месяц определяется не по
        этому смещению, а по часовому поясу сервиса (Asia/Almaty по умолчанию):
        2022-02-01T00:00:00+06:00 попадёт в февраль, а 2022-01-31T23:59:59+06:00
        — в январь.""",
        example = "2022-01-30T00:00:00+06:00")
    @NotNull(message = "datetime is required")
    OffsetDateTime datetime

) {

}