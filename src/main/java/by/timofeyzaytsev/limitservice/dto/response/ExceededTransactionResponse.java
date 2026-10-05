package by.timofeyzaytsev.limitservice.dto.response;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Транзакция, превысившая месячный лимит, вместе с лимитом, по которому
 * превышение зафиксировано.
 *
 * <p>Поля транзакции повторяют структуру входных данных, а сверху добавляются
 * три параметра лимита. Лимит приходит тот, что действовал на момент
 * транзакции</p>
 */
@Schema(description = """
    Превысившая лимит транзакция. Последние три поля — тот лимит, который
    действовал на момент операции, а не последний установленный: переустановка
    лимита позже не меняет этот ответ.""")
public record ExceededTransactionResponse(
    @Schema(description = "Счёт клиента", example = "0000000123")
    String accountFrom,

    @Schema(description = "Счёт контрагента", example = "9999999999")
    String accountTo,

    @Schema(description = "Код валюты операции", example = "KZT")
    String currencyShortname,

    @Schema(description = "Сумма операции в валюте счёта", example = "270300.00")
    BigDecimal sum,

    @Schema(description = "Категория расхода", example = "product",
        allowableValues = {"product", "service"})
    ExpenseCategory expenseCategory,

    @Schema(description = "Момент операции в часовом поясе сервиса",
        example = "2022-01-03T12:00:00+06:00")
    OffsetDateTime datetime,

    @Schema(description = """
        Сумма превышенного лимита в USD. Пусто, если превышение считалось по
        дефолтным 1000 USD: такого лимита в таблице лимитов нет.""",
        example = "1000.00", nullable = true)
    BigDecimal limitSum,

    @Schema(description = "Дата установки этого лимита. Пусто по той же причине, что и limitSum",
        example = "2022-01-01T00:00:00+06:00", nullable = true)
    OffsetDateTime limitDatetime,

    @Schema(description = "Валюта лимита. Пусто по той же причине, что и limitSum",
        example = "USD", nullable = true)
    String limitCurrencyShortname
) {
}