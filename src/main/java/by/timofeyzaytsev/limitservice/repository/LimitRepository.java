package by.timofeyzaytsev.limitservice.repository;

import by.timofeyzaytsev.limitservice.model.Limit;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LimitRepository extends JpaRepository<Limit, UUID> {

    /**
     * История лимитов клиента, свежие первыми. Страница приходит с сортировкой
     * по limitDatetime DESC, LIMIT делает база.
     */
    Page<Limit> findByAccountFrom(String accountFrom, Pageable pageable);
}
