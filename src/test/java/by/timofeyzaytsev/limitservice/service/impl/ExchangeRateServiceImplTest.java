package by.timofeyzaytsev.limitservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import by.timofeyzaytsev.limitservice.client.ExchangeRateProvider;
import by.timofeyzaytsev.limitservice.client.RateQuote;
import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.config.property.ExchangeRateProperties;
import by.timofeyzaytsev.limitservice.config.property.LimitProperties;
import by.timofeyzaytsev.limitservice.exception.BadGatewayException;
import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import by.timofeyzaytsev.limitservice.repository.ExchangeRateRepository;
import by.timofeyzaytsev.limitservice.service.ResolvedRate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRateServiceImpl.resolve: курс и его сохранение")
class ExchangeRateServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Almaty");
    private static final LocalDate DATE = LocalDate.parse("2022-01-02");
    private static final String PROVIDER = "twelvedata";

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ExchangeRateProvider exchangeRateProvider;

    private ExchangeRateServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2022-01-15T06:00:00Z"), ZONE);

        AppProperties props = new AppProperties(
            ZONE,
            new LimitProperties(new BigDecimal("1000.00"), "USD"),
            new ExchangeRateProperties(PROVIDER, null));

        service = new ExchangeRateServiceImpl(
            exchangeRateRepository, exchangeRateProvider, props, clock);
    }

    @Test
    void resolve_WhenCurrencyIsUsd_ShouldReturnRateOneAndNoStoredRate() {
        ResolvedRate resolved = service.resolve("USD", DATE);

        assertThat(resolved.rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(resolved.exchangeRate()).isNull();
        verifyNoInteractions(exchangeRateRepository, exchangeRateProvider);
    }

    @Test
    void resolve_WhenCurrencyIsLowerCase_ShouldReturnRateOneForUsdToo() {
        ResolvedRate resolved = service.resolve("usd", DATE);

        assertThat(resolved.rate()).isEqualByComparingTo(BigDecimal.ONE);
        verifyNoInteractions(exchangeRateRepository, exchangeRateProvider);
    }

    @Test
    void resolve_WhenCurrencyIsLowerCase_ShouldRequestUsdPrefixedPair() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.of(storedRate(new BigDecimal("450.50"), new BigDecimal("449.00"))));

        ResolvedRate resolved = service.resolve("kzt", DATE);

        assertThat(resolved.rate()).isEqualByComparingTo(new BigDecimal("450.50"));
        verify(exchangeRateRepository).findByCurrencyPairAndRateDate("USD/KZT", DATE);
    }

    @Test
    void resolve_WhenStoredRatePresent_ShouldNotCallExternalProvider() {
        ExchangeRate stored = storedRate(new BigDecimal("450.50"), new BigDecimal("449.00"));
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.of(stored));

        ResolvedRate resolved = service.resolve("KZT", DATE);

        assertThat(resolved.rate()).isEqualByComparingTo(new BigDecimal("450.50"));
        assertThat(resolved.exchangeRate()).isSameAs(stored);
        verifyNoInteractions(exchangeRateProvider);
    }

    @Test
    void resolve_WhenStoredCloseIsAbsent_ShouldFallBackToPreviousClose() {
        ExchangeRate stored = storedRate(null, new BigDecimal("449.00"));
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.of(stored));

        ResolvedRate resolved = service.resolve("KZT", DATE);

        assertThat(resolved.rate()).isEqualByComparingTo(new BigDecimal("449.00"));
        verifyNoInteractions(exchangeRateProvider);
    }

    @Test
    void resolve_WhenStoredRateIsNotPositive_ShouldCallExternalProvider() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/RUB", DATE))
            .thenReturn(Optional.of(storedRate("USD/RUB", new BigDecimal("0.000000"), null)));
        when(exchangeRateProvider.fetchQuote("USD/RUB", DATE))
            .thenReturn(new RateQuote(new BigDecimal("83.70"), null));
        when(exchangeRateRepository.save(any(ExchangeRate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ResolvedRate resolved = service.resolve("RUB", DATE);

        assertThat(resolved.rate()).isEqualByComparingTo(new BigDecimal("83.70"));
        verify(exchangeRateProvider).fetchQuote("USD/RUB", DATE);
    }

    @Test
    void resolve_WhenStoredRateMissing_ShouldStoreFetchedRate() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(new BigDecimal("450.50"), new BigDecimal("449.00")));
        when(exchangeRateRepository.save(any(ExchangeRate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.resolve("KZT", DATE);

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());

        ExchangeRate saved = captor.getValue();
        assertThat(saved.getCurrencyPair()).isEqualTo("USD/KZT");
        assertThat(saved.getRateDate()).isEqualTo(DATE);
        assertThat(saved.getClose()).isEqualByComparingTo(new BigDecimal("450.50"));
        assertThat(saved.getPreviousClose()).isEqualByComparingTo(new BigDecimal("449.00"));
    }

    @Test
    void resolve_WhenStoredRateMissing_ShouldSaveConfiguredProviderAsSource() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(new BigDecimal("450.50"), null));
        when(exchangeRateRepository.save(any(ExchangeRate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.resolve("KZT", DATE);

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());

        assertThat(captor.getValue().getSource()).isEqualTo(PROVIDER);
    }

    @Test
    void resolve_WhenFetchedRateHasNoClose_ShouldUsePreviousClose() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(null, new BigDecimal("449.00")));
        when(exchangeRateRepository.save(any(ExchangeRate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ResolvedRate resolved = service.resolve("KZT", DATE);

        assertThat(resolved.rate()).isEqualByComparingTo(new BigDecimal("449.00"));
    }

    @Test
    void resolve_WhenFetchedRateStored_ShouldSetCreatedAtFromClock() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(new BigDecimal("450.50"), null));
        when(exchangeRateRepository.save(any(ExchangeRate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.resolve("KZT", DATE);

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedAt())
            .isEqualTo(OffsetDateTime.parse("2022-01-15T12:00:00+06:00"));
    }

    @Test
    void resolve_WhenProviderReturnsNullQuote_ShouldThrowBadGatewayException() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE)).thenReturn(null);

        assertThatThrownBy(() -> service.resolve("KZT", DATE))
            .isInstanceOf(BadGatewayException.class)
            .hasMessageContaining("USD/KZT")
            .hasMessageContaining("2022-01-02");
    }

    @Test
    void resolve_WhenProviderReturnsZeroRate_ShouldThrowBadGatewayException() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(new BigDecimal("0.000000"), null));

        assertThatThrownBy(() -> service.resolve("KZT", DATE))
            .isInstanceOf(BadGatewayException.class);
    }

    @Test
    void resolve_WhenProviderReturnsNegativeRate_ShouldThrowBadGatewayException() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(null, new BigDecimal("-1.00")));

        assertThatThrownBy(() -> service.resolve("KZT", DATE))
            .isInstanceOf(BadGatewayException.class);
    }

    @Test
    void resolve_WhenProviderFailsWithNonPositiveRate_ShouldStoreNothing() {
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(new BigDecimal("0.000000"), null));

        assertThatThrownBy(() -> service.resolve("KZT", DATE))
            .isInstanceOf(BadGatewayException.class);

        verify(exchangeRateRepository, never()).save(any(ExchangeRate.class));
    }

    @Test
    void resolve_WhenRateStoredConcurrently_ShouldUseAlreadyStoredValue() {
        ExchangeRate concurrentlyStored = storedRate(new BigDecimal("451.00"), null);

        // первый запрос промахивается, второй — после отвергнутой вставки
        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(concurrentlyStored));
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(new BigDecimal("450.50"), null));
        when(exchangeRateRepository.save(any(ExchangeRate.class)))
            .thenThrow(new DataIntegrityViolationException("uq_exchange_rate_pair_date"));

        ResolvedRate resolved = service.resolve("KZT", DATE);

        assertThat(resolved.rate()).isEqualByComparingTo(new BigDecimal("451.00"));
        assertThat(resolved.exchangeRate()).isSameAs(concurrentlyStored);
    }

    @Test
    void resolve_WhenConcurrentInsertRejectedAndNothingStored_ShouldPropagateDatabaseError() {
        DataIntegrityViolationException failure =
            new DataIntegrityViolationException("uq_exchange_rate_pair_date");

        when(exchangeRateRepository.findByCurrencyPairAndRateDate("USD/KZT", DATE))
            .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchQuote("USD/KZT", DATE))
            .thenReturn(new RateQuote(new BigDecimal("450.50"), null));
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.resolve("KZT", DATE))
            .isSameAs(failure);
    }

    private static ExchangeRate storedRate(BigDecimal close, BigDecimal previousClose) {
        return storedRate("USD/KZT", close, previousClose);
    }

    private static ExchangeRate storedRate(
            String pair,
            BigDecimal close,
            BigDecimal previousClose) {

        return ExchangeRate.builder()
            .currencyPair(pair)
            .rateDate(DATE)
            .close(close)
            .previousClose(previousClose)
            .source(PROVIDER)
            .createdAt(OffsetDateTime.parse("2022-01-02T00:00:00+06:00"))
            .build();
    }
}