package by.timofeyzaytsev.limitservice.repository;

import by.timofeyzaytsev.limitservice.model.Transaction;
import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
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
     * Сумма расходов клиента по категории за месяц (в USD).
     */
    @Query("""
        SELECT COALESCE(SUM(t.sumUsd), 0)
        FROM Transaction t
        WHERE t.accountFrom = :accountFrom
          AND t.expenseCategory = :category
          AND t.datetime >= :from
          AND t.datetime < :to
        """)
    BigDecimal sumExpensesForPeriod(
        @Param("accountFrom") String accountFrom,
        @Param("category") ExpenseCategory category,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );
}
