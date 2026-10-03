package by.timofeyzaytsev.limitservice.client.impl;

import by.timofeyzaytsev.limitservice.client.ExchangeRateProvider;
import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.dto.response.TwelveDataResponse;
import by.timofeyzaytsev.limitservice.exception.BadGatewayException;
import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import by.timofeyzaytsev.limitservice.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.exchange-rate.provider",
    havingValue = "twelvedata"
)
public class TwelveDataExchangeRateProvider implements ExchangeRateProvider {

    private final ExchangeRateRepository exchangeRateRepository;
    private final RestClient twelveDataRestClient;
    private final RetryTemplate retryTemplate;
    private final AppProperties props;

    private static final String USD = "USD";

    @Override
    public BigDecimal getRate(String currency, LocalDate date) {
        if (currency.equals(USD)) {
            return BigDecimal.ONE;
        }

        String pair = currency + "/" + USD;

        return exchangeRateRepository
            .findByCurrencyPairAndRateDate(pair, date)
            .orElseGet(() -> fetchAndSave(pair, date))
            .effectiveRate();
    }

    private ExchangeRate fetchAndSave(String pair, LocalDate date) {
        try {
            TwelveDataResponse response = retryTemplate.execute(() -> {
                log.info("Fetching rate for {} on {}", pair, date);
                return twelveDataRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/exchange_rate")
                        .queryParam("symbol", pair)
                        .queryParam("date", date)
                        .queryParam("apikey", props.exchangeRate().twelvedata().apiKey())
                        .build())
                    .retrieve()
                    .body(TwelveDataResponse.class);
            });

            ExchangeRate rate = ExchangeRate.builder()
                .currencyPair(pair)
                .rateDate(date)
                .close(response.rate())
                .source("twelvedata")
                .build();

            return exchangeRateRepository.save(rate);
        } catch (RetryException ex) {
            log.error("Failed to fetch rate for {} on {}", pair, date, ex);
            Throwable cause = ex.getCause();
            log.error("Failed to fetch rate for {} on {} after retries. Cause: {}",
                pair, date, cause != null ? cause.getMessage() : "unknown", cause);
            throw new BadGatewayException("Failed to fetch rate for " + pair + " on " + date);
        }
    }
}
