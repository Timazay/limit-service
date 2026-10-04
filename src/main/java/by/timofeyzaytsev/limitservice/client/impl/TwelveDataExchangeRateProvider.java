package by.timofeyzaytsev.limitservice.client.impl;

import by.timofeyzaytsev.limitservice.client.ExchangeRateProvider;
import by.timofeyzaytsev.limitservice.client.RateQuote;
import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.dto.response.TwelveDataTimeSeriesResponse;
import by.timofeyzaytsev.limitservice.exception.BadGatewayException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Дневные курсы валют из TwelveData.
 *
 * <p>Пары запрашиваются в виде {@code USD/XXX} — так они перечислены у
 * TwelveData, и значение уже означает «сколько XXX за 1 USD», то есть
 * ровно то, что требует контракт. Валюты в обратном порядке ({@code KZT/USD})
 * TwelveData не знает и отвечает 404.</p>
 *
 * <p>Берётся интервал {@code 1day} и закрытие за день, как требует задание.
 * Запрос идёт не на один день, а на неделю назад: на выходных и в праздники
 * баров нет, и вместо ошибки пришлось бы отдельно ловить пустой ответ.</p>
 *
 * <p>Значение возвращается для запрошенной даты, даже если бар взят с
 * предыдущего торгового дня: хранить его нужно под датой запроса, иначе
 * кэш по этой дате всегда промахивался бы и каждая транзакция в выходной
 * оплачивала бы новый запрос во внешний API.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.exchange-rate.provider",
    havingValue = "twelvedata"
)
public class TwelveDataExchangeRateProvider implements ExchangeRateProvider {

    private final RestClient twelveDataRestClient;
    private final RetryTemplate retryTemplate;
    private final AppProperties props;

    private static final String INTERVAL = "1day";

    /**
     * Насколько глубоко смотрим назад за последним торговым днём. Неделя
     * перекрывает и выходные, и праздничные дни вроде Рождества.
     */
    private static final int LOOKBACK_DAYS = 7;

    /**
     * Число знаков. По умолчанию TwelveData определяет его сам, но нам нужно
     * ровно столько, сколько помещается в NUMERIC(19, 6).
     */
    private static final int DECIMAL_PLACES = 6;

    /**
     * Разумные границы курса «сколько единиц валюты за 1 USD». Вне их ответ
     * считается ошибкой направления, а не курсом: лучше 503, чем в базе
     * значения, из-за которых суммы в USD будут занижены в сотни раз.
     */
    private static final BigDecimal MIN_RATE = new BigDecimal("0.000001");
    private static final BigDecimal MAX_RATE = new BigDecimal("10000000");

    @Override
    public RateQuote fetchQuote(String currencyPair, LocalDate date) {
        try {
            TwelveDataTimeSeriesResponse response = retryTemplate.execute(() -> {
                log.info("Fetching {} close for {} on {}", INTERVAL, currencyPair, date);
                return twelveDataRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/time_series")
                        .queryParam("symbol", currencyPair)
                        .queryParam("interval", INTERVAL)
                        .queryParam("start_date", date.minusDays(LOOKBACK_DAYS))
                        .queryParam("end_date", date)
                        .queryParam("previous_close", true)
                        .queryParam("order", "asc")
                        .queryParam("outputsize", LOOKBACK_DAYS + 1)
                        .queryParam("dp", DECIMAL_PLACES)
                        .queryParam("apikey", props.exchangeRate().twelvedata().apiKey())
                        .build())
                    .retrieve()
                    .body(TwelveDataTimeSeriesResponse.class);
            });

            validate(response, currencyPair, date);

            TwelveDataTimeSeriesResponse.Value bar = latestBar(response, date);

            log.debug("Using close of {} for requested date {}", barDate(bar), date);

            return toContractRate(currencyPair, bar);
        } catch (RetryException ex) {
            log.error("Failed to fetch rate for {} on {} after retries", currencyPair, date, ex);
            throw new BadGatewayException("Failed to fetch rate for " + currencyPair + " on " + date);
        }
    }

    /**
     * Последний торговый день, не позже запрошенного. Значения приходят по
     * возрастанию, но ориентируемся на дату самого бара: биржевое время в
     * ответе может не совпадать с календарной датой запроса.
     */
    private TwelveDataTimeSeriesResponse.Value latestBar(
            TwelveDataTimeSeriesResponse response,
            LocalDate date) {

        List<TwelveDataTimeSeriesResponse.Value> bars = response.values();

        if (bars == null || bars.isEmpty()) {
            throw new BadGatewayException("External API returned no bars for " + date);
        }

        return bars.stream()
            .filter(bar -> barDate(bar).isAfter(date) == false)
            .max(Comparator.comparing(this::barDate))
            .orElseThrow(() -> new BadGatewayException(
                "External API returned no bars on or before " + date));
    }

    /**
     * Дата бара. Для дневного интервала TwelveData отдаёт время без времени
     * ({@code 2026-09-27}), поэтому разбираются оба формата.
     */
    private LocalDate barDate(TwelveDataTimeSeriesResponse.Value bar) {
        String datetime = bar.datetime();

        if (datetime == null || datetime.isBlank()) {
            throw new BadGatewayException("External API returned bar without date");
        }

        try {
            return LocalDateTime.parse(datetime).toLocalDate();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(datetime);
            } catch (DateTimeParseException ex) {
                throw new BadGatewayException("External API returned bar with unexpected date " + datetime);
            }
        }
    }

    /**
     * TwelveData отвечает по биржевой конвенции: {@code AAA/BBB} — это сколько
     * BBB за 1 AAA. Мы просим {@code AAA/USD} и получаем USD за одну единицу
     * AAA, а контракт сервиса требует обратного — сколько AAA за 1 USD. Ответ
     * всегда нужно инвертировать; определять это по symbol из ответа нельзя,
     * TwelveData возвращает его ровно таким, каким его запросили.
     */
    private RateQuote toContractRate(String currencyPair, TwelveDataTimeSeriesResponse.Value bar) {
        BigDecimal close = checked(currencyPair, bar.close());
        BigDecimal previousClose = checked(currencyPair, bar.previousClose());

        if ((close == null && previousClose == null)
                || (close != null && close.signum() <= 0)
                || (previousClose != null && previousClose.signum() <= 0)) {

            throw new BadGatewayException("External API returned no close for " + currencyPair);
        }

        return new RateQuote(close, previousClose);
    }

    /**
     * Курс приходит строкой, потому что у валютных пар значащие цифры теряются
     * при разборе как double. Значение вне разумных границ означает неверное
     * направление пары: лучше 503, чем суммы в USD, заниженные в сотни раз.
     */
    private BigDecimal checked(String currencyPair, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        BigDecimal value = new BigDecimal(rawValue.trim());

        if (value.compareTo(MIN_RATE) < 0 || value.compareTo(MAX_RATE) > 0) {
            throw new BadGatewayException(
                "External API returned implausible rate for " + currencyPair + ": " + rawValue);
        }

        return value;
    }

    /**
     * Ошибка приходит в теле ответа кодом 200, поэтому пустой или ошибочный
     * ответ нельзя пропустить дальше: иначе в хранилище попадёт сломанный курс.
     */
    private void validate(TwelveDataTimeSeriesResponse response, String currencyPair, LocalDate date) {
        if (response == null) {
            throw new BadGatewayException("Empty response for rate " + currencyPair + " on " + date);
        }

        if (response.status() != null && !"ok".equalsIgnoreCase(response.status())
                && !"success".equalsIgnoreCase(response.status())) {

            throw new BadGatewayException(
                "External API returned " + response.code() + " " + response.message()
                    + " for rate " + currencyPair + " on " + date);
        }
    }
}