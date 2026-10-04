package by.timofeyzaytsev.limitservice.repository;

import by.timofeyzaytsev.limitservice.model.Transaction;
import by.timofeyzaytsev.limitservice.repository.projection.MonthLimitProjection;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Лимит, действующий на дату транзакции, и израсходованная сумма месяца —
     * одним запросом.
     *
     * <p>Лимит берётся подзапросом с {@code LIMIT 1} и сортировкой по дате
     * установки: последний лимит, установленный не позже даты транзакции.
     * Новый лимит не должен влиять на флаги транзакций, совершённых раньше
     * него, поэтому сравнение строго {@code <=}.</p>
     *
     * <p>Сумма месяца ограничена границами месяца, поэтому новый лимит внутри
     * месяца сравнивается со всем расходом месяца, а не только с расходом после
     * его установки. Группировки здесь нет: условия {@code WHERE} и так оставляют
     * одного клиента и одну категорию, то есть агрегат всегда по одной группе.</p>
     *
     * <p>Верхняя строка фиктивная: без неё {@code JOIN} не на что вешать, так как
     * обе части опциональны. Подзапросы не коррелированы с ней — все условия
     * приходят параметрами, — поэтому {@code LATERAL} не нужен и убран: план
     * с ним и без него совпадает.</p>
     *
     * <p>Оба значения nullable при отсутствии строк, поэтому дефолтный лимит
     * и ноль расхода подставляются через {@code COALESCE} прямо в базе.</p>
     */
    @Query(value = """
        SELECT COALESCE(l.limit_sum, :defaultLimitSum) AS limit_sum,
               l.id AS limit_id,
               COALESCE(s.spent, 0) AS spent
        FROM (SELECT 1) AS dummy
        LEFT JOIN (
            SELECT li.id, li.limit_sum
            FROM limits li
            WHERE li.account_from = :accountFrom
              AND li.expense_category = :category
              AND li.limit_datetime <= :at
            ORDER BY li.limit_datetime DESC
            LIMIT 1
        ) AS l ON TRUE
        LEFT JOIN (
            SELECT SUM(t.sum_usd) AS spent
            FROM transactions t
            WHERE t.account_from = :accountFrom
              AND t.expense_category = :category
              AND t.datetime >= :monthStart
              AND t.datetime < :monthEnd
        ) AS s ON TRUE
        """, nativeQuery = true)
    MonthLimitProjection resolveMonthLimit(
        @Param("accountFrom") String accountFrom,
        @Param("category") String category,
        @Param("at") OffsetDateTime at,
        @Param("monthStart") OffsetDateTime monthStart,
        @Param("monthEnd") OffsetDateTime monthEnd,
        @Param("defaultLimitSum") BigDecimal defaultLimitSum
    );

    /**
     * Блокирует (клиент, категория, месяц) до конца текущей транзакции,
     * чтобы два параллельных запроса не увидели один и тот же остаток лимита.
     *
     * <p>Блокировка на уровне строки тут не годится: её нельзя взять на
     * агрегате, а остаток — это SUM. Advisory lock берётся на произвольном
     * ключе и снимается при commit или rollback, то есть держится ровно
     * столько, сколько длится расчёт остатка и запись транзакции.</p>
     *
     * <p>Ключ приходит строкой, поэтому коллизия хеша может лишь лишний раз
     * заблокировать чужой ряд: на корректность результата это не влияет.</p>
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))", nativeQuery = true)
    void lockMonth(@Param("key") String key);
}
