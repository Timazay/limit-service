package by.timofeyzaytsev.limitservice.model;

import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "limits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Limit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_from", nullable = false, length = 10)
    private String accountFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", nullable = false, length = 20)
    private ExpenseCategory expenseCategory;

    @Column(name = "limit_sum", nullable = false, precision = 19, scale = 2)
    private BigDecimal limitSum;

    @Column(name = "limit_datetime", nullable = false)
    private OffsetDateTime limitDatetime;

    @Column(name = "limit_currency_shortname", nullable = false, length = 3)
    private String limitCurrencyShortname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

