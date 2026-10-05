package by.timofeyzaytsev.limitservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.config.property.LimitProperties;
import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.ExceededTransactionResponse;
import by.timofeyzaytsev.limitservice.dto.response.PageResponse;
import by.timofeyzaytsev.limitservice.mapper.TransactionMapper;
import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import by.timofeyzaytsev.limitservice.model.Transaction;
import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import by.timofeyzaytsev.limitservice.repository.TransactionRepository;
import by.timofeyzaytsev.limitservice.service.ExchangeRateService;
import by.timofeyzaytsev.limitservice.service.ResolvedRate;
import by.timofeyzaytsev.limitservice.service.TransactionProcessor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionServiceImpl: конвертация в USD и список превышений")
class TransactionServiceImplTest {

    private static final String ACCOUNT_FROM = "0000000123";
    private static final ZoneId ZONE = ZoneId.of("Asia/Almaty");
    private static final OffsetDateTime DATETIME = OffsetDateTime.parse("2022-01-02T10:00:00+06:00");

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private TransactionProcessor transactionProcessor;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    private TransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(
            exchangeRateService,
            transactionProcessor,
            transactionRepository,
            transactionMapper,
            new AppProperties(ZONE, new LimitProperties(new BigDecimal("1000.00"), "USD"), null));
    }

    @Test
    void create_WhenRateIsKnown_ShouldConvertSumToUsd() {
        givenRate(new BigDecimal("450.50"));

        service.create(request("KZT", new BigDecimal("10000.45"), DATETIME));

        verify(transactionProcessor).record(
            eq(request("KZT", new BigDecimal("10000.45"), DATETIME)),
            eq(new BigDecimal("22.20")),
            eq(DATETIME.atZoneSameInstant(ZONE)),
            isNull());
    }

    @Test
    void create_WhenCurrencyIsUsd_ShouldConvertSumToUsdWithRateOne() {
        givenRate(BigDecimal.ONE);

        service.create(request("USD", new BigDecimal("100.00"), DATETIME));

        verify(transactionProcessor).record(
            any(TransactionRequest.class),
            eq(new BigDecimal("100.00")),
            eq(DATETIME.atZoneSameInstant(ZONE)),
            isNull());
    }

    @Test
    void create_ShouldRequestRateForLocalDateInServiceZone() {
        givenRate(new BigDecimal("83.70"));

        service.create(request("RUB", new BigDecimal("100.00"), DATETIME));

        verify(exchangeRateService).resolve("RUB", LocalDate.parse("2022-01-02"));
    }

    @Test
    void create_WhenClientZoneDiffersFromService_ShouldRequestRateForServiceZoneDate() {
        OffsetDateTime clientTime = OffsetDateTime.parse("2022-01-31T21:00:00+00:00");
        givenRate(new BigDecimal("83.70"));

        service.create(request("RUB", new BigDecimal("100.00"), clientTime));

        assertThat(clientTime.toLocalDate())
            .as("полночь UTC 31 января это 1 февраля в поясе сервиса")
            .isEqualTo(LocalDate.parse("2022-01-31"));

        verify(exchangeRateService).resolve("RUB", LocalDate.parse("2022-02-01"));
    }

    @Test
    void create_ShouldScaleSumBeforeConversion() {
        // 1.004 / 200.80 = ровно 0.005, и без предварительного округления суммы
        // до двух знаков результат округлился бы вверх до 0.01
        givenRate(new BigDecimal("200.80"));

        service.create(request("KZT", new BigDecimal("1.004"), DATETIME));

        verify(transactionProcessor).record(
            any(TransactionRequest.class),
            eq(new BigDecimal("0.00")),
            any(),
            any());
    }

    @Test
    void create_WhenRateResolved_ShouldPassStoredExchangeRateToProcessor() {
        ExchangeRate stored = ExchangeRate.builder()
            .currencyPair("USD/KZT")
            .rateDate(LocalDate.parse("2022-01-02"))
            .close(new BigDecimal("450.50"))
            .build();

        when(exchangeRateService.resolve(eq("KZT"), any())).thenReturn(new ResolvedRate(new BigDecimal("450.50"), stored));

        service.create(request("KZT", new BigDecimal("10000.45"), DATETIME));

        verify(transactionProcessor).record(any(TransactionRequest.class), any(), any(), eq(stored));
    }

    @Test
    void findExceeded_ShouldRequestPageOfTransactions() {
        when(transactionRepository.findExceededByAccount(eq(ACCOUNT_FROM), any(Pageable.class)))
            .thenReturn(List.of());
        when(transactionMapper.toExceededResponseList(any())).thenReturn(List.of());
        when(transactionRepository.countByAccountFromAndLimitExceededTrue(ACCOUNT_FROM)).thenReturn(0L);

        service.findExceeded(ACCOUNT_FROM, 1, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findExceededByAccount(eq(ACCOUNT_FROM), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void findExceeded_ShouldReturnTotalCountFromSeparateCountQuery() {
        when(transactionRepository.findExceededByAccount(eq(ACCOUNT_FROM), any(Pageable.class)))
            .thenReturn(List.of());
        when(transactionMapper.toExceededResponseList(any())).thenReturn(List.of());
        when(transactionRepository.countByAccountFromAndLimitExceededTrue(ACCOUNT_FROM)).thenReturn(42L);

        PageResponse<ExceededTransactionResponse> response = service.findExceeded(ACCOUNT_FROM, 0, 10);

        assertThat(response.total()).isEqualTo(42);
    }

    @Test
    void findExceeded_ShouldReturnMappedResponsesOfFoundTransactions() {
        Transaction transaction = Transaction.builder().id(UUID.randomUUID()).build();
        ExceededTransactionResponse mapped = new ExceededTransactionResponse(
            ACCOUNT_FROM, "9999999999", "KZT", new BigDecimal("10000.45"),
            ExpenseCategory.PRODUCT, DATETIME, new BigDecimal("1000.00"),
            OffsetDateTime.parse("2022-01-01T00:00:00+06:00"), "USD");

        when(transactionRepository.findExceededByAccount(eq(ACCOUNT_FROM), any(Pageable.class)))
            .thenReturn(List.of(transaction));
        when(transactionMapper.toExceededResponseList(any())).thenReturn(List.of(mapped));
        when(transactionRepository.countByAccountFromAndLimitExceededTrue(ACCOUNT_FROM)).thenReturn(1L);

        PageResponse<ExceededTransactionResponse> response = service.findExceeded(ACCOUNT_FROM, 0, 10);

        assertThat(response.content()).containsExactly(mapped);
    }

    private void givenRate(BigDecimal rate) {
        when(exchangeRateService.resolve(any(), any())).thenReturn(new ResolvedRate(rate, null));
    }

    private static TransactionRequest request(String currency, BigDecimal sum, OffsetDateTime datetime) {
        return new TransactionRequest(
            ACCOUNT_FROM, "9999999999", currency, sum, ExpenseCategory.PRODUCT, datetime);
    }
}