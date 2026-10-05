package by.timofeyzaytsev.limitservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exchange_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "currency_pair", nullable = false, length = 7)
    private String currencyPair;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "close", precision = 19, scale = 6)
    private BigDecimal close;

    @Column(name = "previous_close", precision = 19, scale = 6)
    private BigDecimal previousClose;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Страховка для путей, где {@code createdAt} не проставлен сервисом: колонка
     * в базе NOT NULL, и забытый timestamp лучше дозаполнить здесь, чем упасть
     * на вставке. Время в приложении не читается иначе, поэтому сервисы всегда
     * задают поле сами из бина {@code Clock}, и в приложении этот метод не
     * срабатывает. Тогда timestamp будет в зоне сервиса, а не JVM.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public BigDecimal effectiveRate() {
        return close != null ? close : previousClose;
    }
}
