package by.timofeyzaytsev.limitservice.service.impl;

import by.timofeyzaytsev.limitservice.client.ExchangeRateProvider;
import by.timofeyzaytsev.limitservice.client.RateQuote;
import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.exception.BadGatewayException;
import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import by.timofeyzaytsev.limitservice.repository.ExchangeRateRepository;
import by.timofeyzaytsev.limitservice.service.ExchangeRateService;
import by.timofeyzaytsev.limitservice.service.ResolvedRate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Курсы валют: сначала сохранённые, и только при их отсутствии — внешний API.
 *
 * <p>Хранилище курсов — таблица {@code exchange_rates} в той же PostgreSQL,
 * отдельного кэша в проекте нет. Внешний API платный, поэтому повторный запрос
 * за уже известным курсом не делается.</p>
 *
 * <p>Ключ строки — пара и дата, за которую запросили курс, в том числе
 * выходной или праздник: в такие дни в строке лежит закрытие последнего
 * торгового дня. Ключ по дате самого бара приводил бы к постоянным промахам
 * кэша и новым платным запросам на каждой транзакции в выходной.</p>
 *
 * <p>Сервис вызывается до открытия транзакции приёма транзакции, поэтому
 * внешний запрос не удерживает ни соединение с базой, ни блокировки. Запись
 * курса идёт в своей транзакции репозитория: если бы она делила транзакцию
 * приёма, её откат унёс бы свежий курс, и следующий запрос снова пошёл бы во
 * внешний API за теми же данными.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private static final String USD = "USD";

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateProvider exchangeRateProvider;
    private final AppProperties props;
    private final Clock clock;

    @Override
    public ResolvedRate resolve(String currency, LocalDate date) {
        String normalized = currency.toUpperCase(Locale.ROOT);

        if (USD.equals(normalized)) {
            return new ResolvedRate(BigDecimal.ONE, null);
        }

        String pair = USD + "/" + normalized;

        return storedRate(pair, date)
            .map(ResolvedRate::fromStored)
            .orElseGet(() -> fetchAndStore(pair, date));
    }

    /**
     * Внешний запрос выполняется до открытия транзакции приёма транзакции:
     * он медленный и платный, и держать соединение с базой на его время
     * незачем.
     */
    private ResolvedRate fetchAndStore(String pair, LocalDate date) {
        RateQuote quote = exchangeRateProvider.fetchQuote(pair, date);

        if (quote == null || quote.rate() == null || quote.rate().signum() <= 0) {
            throw new BadGatewayException("Exchange rate for " + pair + " on " + date + " is not available");
        }

        ExchangeRate rate = ExchangeRate.builder()
            .currencyPair(pair)
            .rateDate(date)
            .close(quote.close())
            .previousClose(quote.previousClose())
            .source(props.exchangeRate().provider())
            .createdAt(OffsetDateTime.now(clock))
            .build();

        ExchangeRate stored = store(pair, date, rate);

        return new ResolvedRate(stored.effectiveRate(), stored);
    }

    /**
     * Два параллельных запроса за одним курсом оба попадают во внешний API и
     * оба пытаются вставить: уникальный индекс по (currency_pair, rate_date)
     * отвергнет второй, и он возьмёт уже сохранённое значение вместо ошибки.
     */
    private ExchangeRate store(String pair, LocalDate date, ExchangeRate rate) {
        try {
            return exchangeRateRepository.save(rate);
        } catch (DataIntegrityViolationException ex) {
            log.info("Rate for {} on {} was stored concurrently, using stored value", pair, date);

            return exchangeRateRepository
                .findByCurrencyPairAndRateDate(pair, date)
                .orElseThrow(() -> ex);
        }
    }

    private Optional<ExchangeRate> storedRate(String pair, LocalDate date) {
        return exchangeRateRepository
            .findByCurrencyPairAndRateDate(pair, date)
            .filter(stored -> stored.effectiveRate() != null && stored.effectiveRate().signum() > 0);
    }
}
