package by.timofeyzaytsev.limitservice.dto.response;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Принятая транзакция")
public record TransactionResponse(
    @Schema(description = "Идентификатор транзакции", example = "7a3b1c90-2d4e-4f6a-8b1c-3d5e7f9a1b2c")
    UUID id,

    @Schema(description = "Счёт клиента", example = "0000000123")
    String accountFrom,

    @Schema(description = "Счёт контрагента", example = "9999999999")
    String accountTo,

    @Schema(description = "Код валюты операции", example = "KZT")
    String currencyShortname,

    @Schema(description = "Сумма операции в валюте счёта", example = "10000.45")
    BigDecimal sum,

    @Schema(description = "Сумма операции в USD по курсу закрытия за день операции",
        example = "22.20")
    BigDecimal sumUsd,

    @Schema(description = "Категория расхода", example = "product",
        allowableValues = {"product", "service"})
    ExpenseCategory expenseCategory,

    @Schema(description = """
        Превышен ли месячный лимит на момент этой транзации. Считается от суммы
        в USD с учётом всех предыдущих транзакций месяца, включая те, что
        лимит уже превысили: остаток при этом уходит в минус.""",
        example = "true")
    boolean limitExceeded
) {

}