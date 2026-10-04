package by.timofeyzaytsev.limitservice.repository;

import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LimitRepository extends JpaRepository<Limit, UUID> {

    /**
     * История лимитов клиента, свежие первыми. Страница приходит с сортировкой
     * по limitDatetime DESC, LIMIT делает база.
     */
    Page<Limit> findByAccountFrom(String accountFrom, Pageable pageable);

    /**
     * Лимит клиента, действующий на момент at: последний установленный
     * не позже этого времени. Блокировка не нужна — ссылка на лимит в
     * транзакции это снапшот, конкурентной записи в неё нет.
     */
    @Query("""
        SELECT l
        FROM Limit l
        WHERE l.accountFrom = :accountFrom
          AND l.expenseCategory = :category
          AND l.limitDatetime <= :at
        ORDER BY l.limitDatetime DESC
        limit 1
        """)
    Optional<Limit> findEffectiveLimit(
        @Param("accountFrom") String accountFrom,
        @Param("category") ExpenseCategory category,
        @Param("at") OffsetDateTime at
    );
}
