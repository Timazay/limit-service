package by.timofeyzaytsev.limitservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.config.property.LimitProperties;
import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.mapper.TransactionMapper;
import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.model.Transaction;
import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import by.timofeyzaytsev.limitservice.repository.LimitRepository;
import by.timofeyzaytsev.limitservice.repository.TransactionRepository;
import by.timofeyzaytsev.limitservice.repository.projection.MonthLimitProjection;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionProcessorImpl.record: флаг limit_exceeded")
class TransactionProcessorImplTest {

    private static final String ACCOUNT_FROM = "0000000123";
    private static final String ACCOUNT_TO = "9999999999";
    private static final ZoneId ZONE = ZoneId.of("Asia/Almaty");
    private static final BigDecimal DEFAULT_LIMIT_SUM = new BigDecimal("1000.00");
    private static final OffsetDateTime JANUARY_2 = OffsetDateTime.parse("2022-01-02T10:00:00+06:00");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LimitRepository limitRepository;

    @Mock
    private TransactionMapper transactionMapper;

    private TransactionProcessorImpl processor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2022-01-15T06:00:00Z"), ZONE);

        processor = new TransactionProcessorImpl(
            transactionRepository,
            limitRepository,
            transactionMapper,
            new AppProperties(ZONE, new LimitProperties(DEFAULT_LIMIT_SUM, "USD"), null),
            clock);
    }

    @Test
    void record_WhenRemainingIsZero_ShouldNotExceedLimit() {
        Transaction transaction = recordTransaction(JANUARY_2, new BigDecimal("100.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("900.00"), null));

        assertThat(transaction.isLimitExceeded()).isFalse();
    }

    @Test
    void record_WhenSpentPlusSumEqualsLimit_ShouldNotExceedLimit() {
        Transaction transaction = recordTransaction(JANUARY_2, new BigDecimal("600.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("400.00"), null));

        assertThat(transaction.isLimitExceeded()).isFalse();
    }

    @Test
    void record_WhenSpentPlusSumExceedsLimit_ShouldMarkExceeded() {
        Transaction transaction = recordTransaction(JANUARY_2, new BigDecimal("600.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("500.00"), null));

        assertThat(transaction.isLimitExceeded()).isTrue();
    }

    @Test
    void record_WhenLimitIsNotInstalled_ShouldEvaluateAgainstDefaultLimitSum() {
        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(DEFAULT_LIMIT_SUM, new BigDecimal("400.00"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(JANUARY_2, ExpenseCategory.PRODUCT), new BigDecimal("600.00"),
            JANUARY_2.atZoneSameInstant(ZONE), null);

        verify(transactionRepository).resolveMonthLimit(
            eq(ACCOUNT_FROM), eq("PRODUCT"), eq(JANUARY_2), any(), any(), eq(DEFAULT_LIMIT_SUM));
    }

    @Test
    void record_WhenLimitIdIsNull_ShouldNotReferenceAnyLimit() {
        recordTransaction(JANUARY_2, new BigDecimal("100.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("900.00"), null));

        verifyNoInteractions(limitRepository);
    }

    @Test
    void record_WhenLimitIdIsPresent_ShouldReferenceLimitByThatId() {
        UUID limitId = UUID.randomUUID();
        Limit limit = Limit.builder()
            .id(limitId)
            .accountFrom(ACCOUNT_FROM)
            .expenseCategory(ExpenseCategory.PRODUCT)
            .limitSum(new BigDecimal("1000.00"))
            .limitDatetime(OffsetDateTime.parse("2022-01-01T00:00:00+06:00"))
            .limitCurrencyShortname("USD")
            .build();

        when(limitRepository.getReferenceById(limitId)).thenReturn(limit);

        Transaction transaction = recordTransaction(JANUARY_2, new BigDecimal("100.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("900.00"), limitId));

        verify(limitRepository).getReferenceById(limitId);
        assertThat(transaction.getLimit()).isSameAs(limit);
    }

    @Test
    void record_WhenSameClientAndMonth_ShouldLockByAccountCategoryAndMonth() {
        recordTransaction(JANUARY_2, new BigDecimal("100.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("900.00"), null));

        verify(transactionRepository).lockMonth("0000000123:PRODUCT:2022-01-01");
    }

    @Test
    void record_WhenCategoryIsService_ShouldLockSeparatelyFromProduct() {
        OffsetDateTime datetime = JANUARY_2;

        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(new BigDecimal("1000.00"), new BigDecimal("0"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(datetime, ExpenseCategory.SERVICE), new BigDecimal("100.00"),
            datetime.atZoneSameInstant(ZONE), null);

        verify(transactionRepository).lockMonth("0000000123:SERVICE:2022-01-01");
    }

    @Test
    void record_ShouldLockMonthBeforeReadingSpent() {
        recordTransaction(JANUARY_2, new BigDecimal("100.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("900.00"), null));

        InOrder order = inOrder(transactionRepository);
        order.verify(transactionRepository).lockMonth("0000000123:PRODUCT:2022-01-01");
        order.verify(transactionRepository).resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void record_WhenTransactionIsInMarch_ShouldQueryMarchBoundaries() {
        OffsetDateTime march = OffsetDateTime.parse("2022-03-10T09:00:00+06:00");

        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(new BigDecimal("1000.00"), new BigDecimal("0"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(march, ExpenseCategory.PRODUCT), new BigDecimal("100.00"),
            march.atZoneSameInstant(ZONE), null);

        ArgumentCaptor<OffsetDateTime> monthStart = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> monthEnd = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(transactionRepository).resolveMonthLimit(anyString(), anyString(), eq(march),
            monthStart.capture(), monthEnd.capture(), any());

        assertThat(monthStart.getValue()).isEqualTo(OffsetDateTime.parse("2022-03-01T00:00:00+06:00"));
        assertThat(monthEnd.getValue()).isEqualTo(OffsetDateTime.parse("2022-04-01T00:00:00+06:00"));
    }

    @Test
    void record_WhenTransactionIsFirstDayOfMonthAtMidnight_ShouldCountItIntoThatMonth() {
        OffsetDateTime midnight = OffsetDateTime.parse("2022-02-01T00:00:00+06:00");

        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(new BigDecimal("1000.00"), new BigDecimal("0"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(midnight, ExpenseCategory.PRODUCT), new BigDecimal("100.00"),
            midnight.atZoneSameInstant(ZONE), null);

        ArgumentCaptor<OffsetDateTime> monthStart = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> monthEnd = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(transactionRepository).resolveMonthLimit(anyString(), anyString(), eq(midnight),
            monthStart.capture(), monthEnd.capture(), any());

        assertThat(monthStart.getValue()).isEqualTo(OffsetDateTime.parse("2022-02-01T00:00:00+06:00"));
        assertThat(monthEnd.getValue()).isEqualTo(OffsetDateTime.parse("2022-03-01T00:00:00+06:00"));
    }

    @Test
    void record_WhenTransactionIsLastDayOfMonth_ShouldCountItIntoSameMonth() {
        OffsetDateTime lastDay = OffsetDateTime.parse("2022-01-31T23:59:59+06:00");

        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(new BigDecimal("1000.00"), new BigDecimal("0"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(lastDay, ExpenseCategory.PRODUCT), new BigDecimal("100.00"),
            lastDay.atZoneSameInstant(ZONE), null);

        ArgumentCaptor<OffsetDateTime> monthStart = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(transactionRepository).resolveMonthLimit(anyString(), anyString(), eq(lastDay),
            monthStart.capture(), any(), any());

        assertThat(monthStart.getValue()).isEqualTo(OffsetDateTime.parse("2022-01-01T00:00:00+06:00"));
    }

    @Test
    void record_WhenTransactionsFallInSameMonth_ShouldUseSameLockKeyForWholeMonth() {
        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(new BigDecimal("1000.00"), new BigDecimal("0"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(OffsetDateTime.parse("2022-01-05T09:00:00+06:00"),
            ExpenseCategory.PRODUCT), new BigDecimal("100.00"),
            OffsetDateTime.parse("2022-01-05T09:00:00+06:00").atZoneSameInstant(ZONE), null);

        processor.record(request(OffsetDateTime.parse("2022-01-31T23:59:59+06:00"),
            ExpenseCategory.PRODUCT), new BigDecimal("100.00"),
            OffsetDateTime.parse("2022-01-31T23:59:59+06:00").atZoneSameInstant(ZONE), null);

        ArgumentCaptor<String> lockKey = ArgumentCaptor.forClass(String.class);
        verify(transactionRepository, times(2)).lockMonth(lockKey.capture());

        assertThat(lockKey.getAllValues())
            .as("весь месяц сериализуется одним ключом, иначе параллельные запросы разойдутся")
            .containsExactly("0000000123:PRODUCT:2022-01-01", "0000000123:PRODUCT:2022-01-01");
    }

    @Test
    void record_WhenTransactionComesInDifferentZone_ShouldResolveMonthInServiceZone() {
        OffsetDateTime astanaMidnight = OffsetDateTime.parse("2022-02-01T00:00:00+05:00");

        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(new BigDecimal("1000.00"), new BigDecimal("0"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(astanaMidnight, ExpenseCategory.PRODUCT), new BigDecimal("100.00"),
            astanaMidnight.atZoneSameInstant(ZONE), null);

        assertThat(astanaMidnight.atZoneSameInstant(ZONE).getDayOfMonth())
            .as("полночь +05:00 это уже 1 февраля в поясе сервиса +06:00")
            .isEqualTo(1);

        verify(transactionRepository).lockMonth("0000000123:PRODUCT:2022-02-01");
    }

    @Test
    void record_WhenStored_ShouldKeepOriginalAmountAndConvertedSumUsd() {
        Transaction transaction = recordTransaction(JANUARY_2, new BigDecimal("22.20"),
            month(new BigDecimal("1000.00"), new BigDecimal("0"), null));

        assertThat(transaction.getAccountFrom()).isEqualTo(ACCOUNT_FROM);
        assertThat(transaction.getAccountTo()).isEqualTo(ACCOUNT_TO);
        assertThat(transaction.getCurrencyShortname()).isEqualTo("KZT");
        assertThat(transaction.getSum()).isEqualByComparingTo(new BigDecimal("10000.45"));
        assertThat(transaction.getSumUsd()).isEqualByComparingTo(new BigDecimal("22.20"));
        assertThat(transaction.getExpenseCategory()).isEqualTo(ExpenseCategory.PRODUCT);
        assertThat(transaction.getDatetime()).isEqualTo(JANUARY_2);
    }

    @Test
    void record_WhenStored_ShouldSetCreatedAtFromClock() {
        Transaction transaction = recordTransaction(JANUARY_2, new BigDecimal("100.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("0"), null));

        assertThat(transaction.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2022-01-15T12:00:00+06:00"));
    }

    @Test
    void record_WhenExchangeRateResolved_ShouldReferenceStoredRate() {
        ExchangeRate exchangeRate = ExchangeRate.builder()
            .currencyPair("USD/KZT")
            .rateDate(JANUARY_2.toLocalDate())
            .close(new BigDecimal("450.50"))
            .build();

        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(new BigDecimal("1000.00"), new BigDecimal("0"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(JANUARY_2, ExpenseCategory.PRODUCT), new BigDecimal("22.20"),
            JANUARY_2.atZoneSameInstant(ZONE), exchangeRate);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        assertThat(captor.getValue().getExchangeRate()).isSameAs(exchangeRate);
    }

    @Test
    void record_WhenTransactionIsInUsd_ShouldNotReferenceAnyExchangeRate() {
        OffsetDateTime datetime = JANUARY_2;
        TransactionRequest request = new TransactionRequest(
            ACCOUNT_FROM, ACCOUNT_TO, "USD", new BigDecimal("100.00"), ExpenseCategory.PRODUCT, datetime);

        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month(new BigDecimal("1000.00"), new BigDecimal("0"), null));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request, new BigDecimal("100.00"), datetime.atZoneSameInstant(ZONE), null);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        assertThat(captor.getValue().getExchangeRate()).isNull();
    }

    @Test
    void record_WhenSpentIsAlreadyAboveLimit_ShouldMarkExceeded() {
        Transaction transaction = recordTransaction(JANUARY_2, new BigDecimal("100.00"),
            month(new BigDecimal("1000.00"), new BigDecimal("1200.00"), null));

        assertThat(transaction.isLimitExceeded()).isTrue();
    }

    @Test
    void record_WhenLimitSumIsZero_ShouldMarkPositiveTransactionAsExceeded() {
        Transaction transaction = recordTransaction(JANUARY_2, new BigDecimal("0.01"),
            month(new BigDecimal("0.00"), new BigDecimal("0.00"), null));

        assertThat(transaction.isLimitExceeded()).isTrue();
    }

    private Transaction recordTransaction(
            OffsetDateTime datetime,
            BigDecimal sumUsd,
            MonthLimitProjection month) {

        when(transactionRepository.resolveMonthLimit(
            anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(month);
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.record(request(datetime, ExpenseCategory.PRODUCT), sumUsd,
            datetime.atZoneSameInstant(ZONE), null);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        return captor.getValue();
    }

    private static TransactionRequest request(OffsetDateTime datetime, ExpenseCategory category) {
        return new TransactionRequest(
            ACCOUNT_FROM, ACCOUNT_TO, "KZT", new BigDecimal("10000.45"), category, datetime);
    }

    private static MonthLimitProjection month(BigDecimal limitSum, BigDecimal spent, UUID limitId) {
        return new MonthLimitProjection() {
            @Override
            public BigDecimal getLimitSum() {
                return limitSum;
            }

            @Override
            public BigDecimal getSpent() {
                return spent;
            }

            @Override
            public UUID getLimitId() {
                return limitId;
            }
        };
    }
}