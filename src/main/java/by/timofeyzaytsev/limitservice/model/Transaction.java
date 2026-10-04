package by.timofeyzaytsev.limitservice.model;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "transactions"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_from", nullable = false, length = 10)
    private String accountFrom;

    @Column(name = "account_to", nullable = false, length = 10)
    private String accountTo;

    @Column(name = "currency_shortname", nullable = false, length = 3)
    private String currencyShortname;

    @Column(name = "sum", nullable = false, precision = 19, scale = 2)
    private BigDecimal sum;

    @Column(name = "sum_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal sumUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", nullable = false, length = 20)
    private ExpenseCategory expenseCategory;

    @Column(name = "datetime", nullable = false)
    private OffsetDateTime datetime;

    @Column(name = "limit_exceeded", nullable = false)
    private boolean limitExceeded;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "limit_id")
    private Limit limit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_rate_id")
    private ExchangeRate exchangeRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
