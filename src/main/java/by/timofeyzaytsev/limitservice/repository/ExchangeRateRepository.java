package by.timofeyzaytsev.limitservice.repository;

import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    /**
     * Найти курс на конкретную дату.
     */
    Optional<ExchangeRate> findByCurrencyPairAndRateDate(String currencyPair, LocalDate rateDate);
}
