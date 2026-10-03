package by.timofeyzaytsev.limitservice.repository;

import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LimitRepository extends JpaRepository<Limit, UUID> {

    /**
     * Используется для того, чтобы два параллельных
     * запроса не увидели один и тот же остаток лимита.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT l
        FROM Limit l
        WHERE l.expenseCategory = :category
          AND l.limitDatetime <= :at
        ORDER BY l.limitDatetime DESC
        LIMIT 1
        """)
    Optional<Limit> findLatestBeforeForUpdate(
        @Param("category") ExpenseCategory category,
        @Param("at") OffsetDateTime at
    );
}
