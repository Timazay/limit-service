package by.timofeyzaytsev.limitservice.dto.response;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Установленный лимит")
public record LimitResponse(
    @Schema(description = "Идентификатор лимита", example = "3f1c2a44-9b0e-4a1f-8f6d-1a2b3c4d5e6f")
    UUID id,

    @Schema(description = "Счёт клиента", example = "0000000123")
    String accountFrom,

    @Schema(description = "Категория расхода", example = "product",
        allowableValues = {"product", "service"})
    ExpenseCategory expenseCategory,

    @Schema(description = "Сумма лимита в USD", example = "2000.00")
    BigDecimal limitSum,

    @Schema(description = """
        Дата установки, проставленная сервером. Клиент её не задаёт, поэтому она
        всегда в прошлом или настоящем, но не в будущем.""",
        example = "2022-01-10T00:00:00+06:00")
    OffsetDateTime limitDatetime,

    @Schema(description = "Валюта лимита. Всегда USD: по умолчанию берётся из настроек",
        example = "USD", allowableValues = {"USD"})
    String limitCurrencyShortname
) {}