package by.timofeyzaytsev.limitservice.dto.request;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

@Schema(description = """
    Запрос на установку лимита. Даты в нём нет: она проставляется сервером,
    и клиент не может выставить дату в прошлом или будущем.""")
public record LimitRequest(

    @Schema(description = "Счёт клиента, ровно 10 цифр", example = "0000000123")
    @NotBlank(message = "accountFrom is required")
    @Pattern(regexp = "\\d{10}", message = "accountFrom must be 10 digits")
    String accountFrom,

    @Schema(description = """
        Категория расхода, к которой относится лимит. У товаров и услуг лимиты
        и остатки считаются раздельно.""",
        example = "service", allowableValues = {"product", "service"})
    @NotNull(message = "expenseCategory is required")
    ExpenseCategory expenseCategory,

    @Schema(description = """
        Сумма лимита в USD. Валюта лимита всегда доллар и в запросе не
        указывается. Если лимит не установлен, применяется дефолт 1000 USD.""",
        example = "2000.00")
    @NotNull(message = "limitSum is required")
    @DecimalMin(value = "0.01", message = "limitSum must be positive")
    @Digits(integer = 17, fraction = 2, message = "limitSum must have at most 2 decimal places")
    BigDecimal limitSum

) {}