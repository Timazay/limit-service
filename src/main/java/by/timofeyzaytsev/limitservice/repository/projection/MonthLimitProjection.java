package by.timofeyzaytsev.limitservice.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Состояние месячного лимита на момент транзакции, собранное одним запросом.
 *
 * @param limitSum лимит, действовавший на дату транзакции; дефолтный,
 *                 если клиент лимит не устанавливал
 * @param spent    уже израсходовано в этом месяце до текущей транзакции
 * @param limitId  установленный лимит, на который ссылается транзакция, либо
 *                 {@code null}, если применялся дефолтный
 */
public interface MonthLimitProjection {

    BigDecimal getLimitSum();

    BigDecimal getSpent();

    UUID getLimitId();
}
