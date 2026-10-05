package by.timofeyzaytsev.limitservice;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import by.timofeyzaytsev.limitservice.dto.response.ExceededTransactionResponse;
import by.timofeyzaytsev.limitservice.dto.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Сквозной тест: «1 случай» из таблицы задания целиком через HTTP.
 *
 * <p>Приложение поднято на реальном порту, PostgreSQL — в Testcontainers,
 * внешний API курсов — в WireMock. Лимиты ставятся прямой вставкой в базу:
 * через API {@code limit_datetime} проставился бы серверной датой «сейчас», и
 * лимит января 2022 года не нашёлся бы для транзакций того же года. Это не
 * обход проверки, а следствие требования «дата лимита выставляется сервером,
 * в прошлом выставить нельзя».</p>
 */
@DisplayName("Сквозной сценарий: превышения месячного лимита")
class ExceededTransactionsApiIT extends AbstractPostgresIT {

    private static final String ACCOUNT_FROM = "0000000123";
    private static final String ACCOUNT_TO = "9999999999";
    private static final String CATEGORY = "product";

    /**
     * Курс, который отдаёт WireMock: столько KZT за 1 USD. Суммы транзакций
     * подбираются так, чтобы в USD получились ровно значения из задания.
     */
    private static final BigDecimal KZT_PER_USD = new BigDecimal("450.50");

    private static final ZoneId ZONE = ZoneId.of("Asia/Almaty");

    /**
     * Поднимается в статическом инициализаторе, а не в {@code @BeforeAll}:
     * контекст Spring собирается до бefore-all методов, и адрес WireMock нужен
     * уже при {@code @DynamicPropertySource}.
     */
    private static final com.github.tomakehurst.wiremock.WireMockServer WIRE_MOCK =
        startWireMock();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient rest;

    @DynamicPropertySource
    static void exchangeRateProperties(DynamicPropertyRegistry registry) {
        registry.add(
            "app.exchange-rate.twelvedata.base-url",
            () -> "http://localhost:" + WIRE_MOCK.port());
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();

        jdbcTemplate.update("DELETE FROM transactions WHERE account_from = ?", ACCOUNT_FROM);
        jdbcTemplate.update("DELETE FROM limits WHERE account_from = ?", ACCOUNT_FROM);
        jdbcTemplate.update("DELETE FROM exchange_rates");
    }

    @Test
    void exceededTransactions_WhenLimitChangedInsideMonth_ShouldReturnOnlyOverflowedOnes() {
        insertLimit("2022-01-01", "1000.00");
        insertLimit("2022-01-10", "2000.00");

        postTransaction("2022-01-02", usd("500"));
        postTransaction("2022-01-03", usd("600"));
        postTransaction("2022-01-11", usd("100"));
        postTransaction("2022-01-12", usd("700"));
        postTransaction("2022-01-13", usd("100"));
        postTransaction("2022-01-13", usd("100"));

        ResponseEntity<PageResponse<ExceededTransactionResponse>> response = rest.get()
            .uri("/api/v1/transactions/exceeded?accountFrom={account}", ACCOUNT_FROM)
            .retrieve()
            .toEntity(new ParameterizedTypeReference<>() {
            });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        PageResponse<ExceededTransactionResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.total()).as("превышены ровно две транзакции").isEqualTo(2);

        List<ExceededTransactionResponse> content = body.content();
        assertThat(content).hasSize(2);

        assertExceeded(content.get(0), "2022-01-03", "1000.00", "2022-01-01");
        assertExceeded(content.get(1), "2022-01-13", "2000.00", "2022-01-10");

        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo("/time_series")));
    }

    /**
     * Даты сравниваются как моменты времени: клиентский Jackson нормализует
     * смещение при разборе в UTC, и полночь {@code 2022-01-01T00:00+06:00}
     * иначе выглядела бы как 31 декабря. Момент при этом тот же, а он и есть
     * проверяемый факт: лимит установлен ровно в полночь своего дня по
     * {@code app.time-zone}.
     */
    private void assertExceeded(
            ExceededTransactionResponse transaction,
            String expectedDate,
            String expectedLimitSum,
            String expectedLimitDate) {

        assertThat(transaction.datetime().toInstant()).isEqualTo(atNoon(expectedDate).toInstant());
        assertThat(transaction.limitSum()).isEqualByComparingTo(expectedLimitSum);
        assertThat(transaction.limitDatetime().toInstant())
            .isEqualTo(atMidnight(expectedLimitDate).toInstant());
        assertThat(transaction.limitCurrencyShortname()).isEqualTo("USD");
        assertThat(transaction.accountFrom()).isEqualTo(ACCOUNT_FROM);
    }

    private void insertLimit(String date, String sum) {
        jdbcTemplate.update(
            """
            INSERT INTO limits (id, account_from, expense_category, limit_sum,
                                limit_datetime, limit_currency_shortname)
            VALUES (gen_random_uuid(), ?, 'PRODUCT', ?, ?, 'USD')
            """,
            ACCOUNT_FROM,
            new BigDecimal(sum),
            atMidnight(date));
    }

    private void postTransaction(String date, BigDecimal sum) {
        ResponseEntity<Void> response = rest.post()
            .uri("/api/v1/transactions")
            .body(Map.of(
                "accountFrom", ACCOUNT_FROM,
                "accountTo", ACCOUNT_TO,
                "currencyShortname", "KZT",
                "sum", sum,
                "expenseCategory", CATEGORY,
                "datetime", atNoon(date).toString()))
            .retrieve()
            .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** Сумма в KZT, дающая ровно указанную сумму в USD. */
    private BigDecimal usd(String amount) {
        return new BigDecimal(amount).multiply(KZT_PER_USD);
    }

    private OffsetDateTime atMidnight(String date) {
        return LocalDate.parse(date).atStartOfDay(ZONE).toOffsetDateTime();
    }

    private OffsetDateTime atNoon(String date) {
        return LocalDate.parse(date).atTime(12, 0).atZone(ZONE).toOffsetDateTime();
    }

    private static com.github.tomakehurst.wiremock.WireMockServer startWireMock() {
        com.github.tomakehurst.wiremock.WireMockServer server =
            new com.github.tomakehurst.wiremock.WireMockServer(options().dynamicPort());
        server.start();

        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/time_series"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(timeSeriesBody())));

        return server;
    }

    /**
     * Один ответ покрывает все даты сценария: провайдер берёт последний бар,
     * не позже запрошенной даты, поэтому восьми дневным ответам не нужно.
     */
    private static String timeSeriesBody() {
        StringBuilder bars = new StringBuilder();

        for (LocalDate date = LocalDate.parse("2021-12-27");
                !date.isAfter(LocalDate.parse("2022-01-14"));
                date = date.plusDays(1)) {

            if (bars.length() > 0) {
                bars.append(',');
            }

            bars.append("""
                {"datetime":"%s","close":"%s","previous_close":"%s"}
                """.formatted(date, KZT_PER_USD.toPlainString(), KZT_PER_USD.toPlainString()));
        }

        return """
            {
              "meta": {"symbol": "USD/KZT", "interval": "1day", "currency": "USD"},
              "values": [%s],
              "status": "ok"
            }
            """.formatted(bars);
    }
}