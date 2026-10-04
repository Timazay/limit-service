package by.timofeyzaytsev.limitservice.dto.response;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Транзакция, превысившая месячный лимит, вместе с лимитом, по которому
 * превышение зафиксировано.
 *
 * <p>Поля транзакции повторяют структуру входных данных, а сверху добавляются
 * три параметра лимита. Лимит приходит тот, что действовал на момент
 * транзакции</p>
 */
public record ExceededTransactionResponse(
    String accountFrom,
    String accountTo,
    String currencyShortname,
    BigDecimal sum,
    ExpenseCategory expenseCategory,
    OffsetDateTime datetime,
    BigDecimal limitSum,
    OffsetDateTime limitDatetime,
    String limitCurrencyShortname
) {
}
