---
name: write-limit-logic-test
description: Use when writing or fixing a test for the limit_exceeded flag, monthly limit remaining, or month boundaries. Triggers on limit_exceeded, TransactionProcessorImpl, "тест на лимиты", "тест логики лимитов", limitExceeded, sumExpensesForPeriod, "сценарий из ТЗ", Clock, "смена лимита внутри месяца".
---

# Тест на логику выставления флага limit_exceeded

Здесь только сценарии лимита и граничные случаи. Общие правила проекта —
нейминг `метод_WhenУсловие_ShouldРезультат`, AssertJ и Mockito,
расположение тестов, тестовые зависимости — в скилле `write-test`.
Имена из примеров ниже уже соответствуют этому правилу.

Логика распределена между тремя слоями, и тест должен бить по
`TransactionProcessorImpl` — это единственное место, где принимается
решение о флаге.

## Что вызывать

```java
transactionProcessor.record(request, sumUsd, zoned, exchangeRate);
```

Сигнатура в `service/TransactionProcessor.java`. Обрати внимание на
**четвёртый параметр** — `ExchangeRate exchangeRate`. Он может быть `null`
(для USD курс не хранится), но сам вызов без него не соберётся.

Три слоя и их роль:

- `ExchangeRateServiceImpl.resolve` — курс и ссылка на строку `exchange_rates`
- `TransactionServiceImpl.create` — конвертация в USD, **вне транзакции БД**
- `TransactionProcessorImpl.record` — `@Transactional`, блокировка, флаг

Тестируй нижний слой. Если нужен сквозной сценарий — верхний, но тогда
мокай `ExchangeRateService`.

## Время — только через Clock

**Никогда** не вызывай `OffsetDateTime.now()` в тесте. Границы месяца
считаются в часовом поясе сервиса (`app.time-zone`), поэтому тест с
реальным временем нестабилен: он развалится на смене месяца и не
воспроизведёт сценарий из задания.

**Подменить один `Clock` мало.** Границы месяца считает
`TransactionProcessorImpl.monthStartOf`, и она берёт зону из
`props.timeZone()`, а не из часов. Замена `Clock` двигает момент, но
оставляет зону `monthStartOf` системной, и края «граница полуночи» и
«смена месяца» разойдутся с намерением. Подставляй оба аргумента:

```java
private static final ZoneId ZONE = ZoneId.of("Asia/Almaty");

// Способ 1: собрать сервис вручную, момент и зона заданы вместе
Clock fixed = Clock.fixed(Instant.parse("2022-01-11T06:00:00Z"), ZONE);
AppProperties props = new AppProperties(ZONE, new LimitProperties(new BigDecimal("1000.00"), "USD"), null);
new TransactionProcessorImpl(
    transactionRepository, limitRepository, transactionMapper, props, fixed);

// Способ 2: подменить бин Clock, но AppProperties всё равно задать вручную
@Mock Clock clock;
@BeforeEach void setUp() {
    when(clock.instant()).thenReturn(Instant.parse("2022-01-11T06:00:00Z"));
    when(clock.getZone()).thenReturn(ZONE);
}
```

Порядок полей конструктора менять нельзя — он задан
`@RequiredArgsConstructor` на полях класса: репозиторий транзакций,
репозиторий лимитов, маппер, `props`, `clock`. В Spring-тесте достаточно
`@MockitoBean` на `Clock`, потому что `AppProperties` там приходит из
контекста и уже содержит `app.time-zone`.

Дефолтный лимит 1000 бери из `AppProperties.limit().defaultSum()`, а не
зашивай в тест константой: он приходит в запрос как параметр, и тест
проверяет проброс, а не конкретное число.

## Суммы в USD

`sumUsd` в тесте считай так же, как сервис:

```java
BigDecimal sumUsd = MoneyUtils.divide(MoneyUtils.scale(request.sum()), rate);
```

`MoneyUtils.SCALE = 2`, `ROUNDING = HALF_UP` (`utils/MoneyUtils.java`).
Если подставляешь готовые значения — ставь их напрямую в `sumUsd` и не
заморачивайся с курсом, это изолирует тест логики лимита от логики
конвертации.

## Сценарий из задания — обязательный минимум

Лимит 1000 USD от 01.01.2022, категория `PRODUCT`:

| дата | сумма USD | limit_exceeded |
|---|---|---|
| 02.01 | 500 | false |
| 03.01 | 600 | **true** |

Затем лимит меняется на 2000 USD 10.01, расход месяца уже 1100:

| дата | сумма USD | limit_exceeded |
|---|---|---|
| 11.01 | 100 | false |
| 12.01 | 700 | false |
| 13.01 | 100 | false |
| 13.01 | 100 | **true** |

Лимит ставится напрямую в базу (`INSERT` в `limits`), а не через API:
через API `limit_datetime` проставится серверной датой «сейчас», и лимит
не найдётся для транзакций 2022 года. Это не баг — по заданию действует
`limit_datetime <= datetime` строго.

## Четыре края, которые обязан покрыть тест

**1. Остаток ровно 0 не является превышением.** Сравнение строгое:
`spent.add(sumUsd).compareTo(limitSum) > 0` в
`TransactionProcessorImpl.record`. При лимите 1000 и расходе 900
транзакция на 100 даёт `false`. Стоит проверить и `false`, и следующую
транзакцию на 100 — та уже `true`.

**2. Новый лимит внутри месяца сравнивается со всем расходом месяца**, а
не только с расходом после его установки. В сценарии выше новый лимит
2000 выставляется 10.01, а к этому моменту уже потрачено 1100. Значит
остаток 900, а не 2000. Проверь это отдельным тестом: лимит 2000 от
05.01, транзакция 1000 от 02.01, транзакция 1500 от 06.01 → превышение.

**3. Смена месяца обнуляет остаток.** Расход января не должен учитываться
в феврале. Две транзакции по 800 при лимите 1000: первая в январе
`false`, вторая в феврале тоже `false`. Если суммируется по всем месяцам —
вторая станет `true`, и это будет ошибка.

**4. Граница полуночи в поясе сервиса.** Месяц определяется по тому, на какое
число транзакция попадает **в `app.time-zone`**, а не в зоне клиента:

- `2022-01-31T23:59:59+06:00` — январь
- `2022-02-01T00:00:00+06:00` — февраль
- `2022-02-01T00:00:00+05:00` — в Алматы это `2022-02-01T01:00`, то есть
  тоже февраль: зона клиента не должна сдвигать дату назад

Проверь все три. Ошибка «сдвинули месяц назад» даёт транзакцию 1 февраля
в январский расход, а это ровно тот случай, который ломает историю
лимитов.

## Ключ блокировки — первое число месяца, а не дата транзакции

`lockKey` собирается как `accountFrom + ":" + expenseCategory + ":" +
monthStart.toLocalDate()`, а `monthStart` — первый день месяца. Поэтому
две транзакции 5 и 31 января дают **один и тот же** ключ
`0000000123:PRODUCT:2022-01-01`.

Это и есть смысл блокировки: сериализуется весь месяц клиента и
категории, а не отдельные транзакции. Если тест ждёт в ключе дату
транзакции, он неверно понял замысел и упадёт на реальном коде.

Проверяется двумя вызовами `record` в одном месяце и сравнением обоих
аргументов `lockMonth` — они должны совпасть. Различаться ключ должен
тогда и только тогда, когда отличается клиент, категория или месяц.

## Порядок вызовов, который тест не должен нарушать

Внутри `record` сначала берётся advisory-лог, потом читается остаток:

```java
transactionRepository.lockMonth(lockKey(request, monthStart));
MonthLimitProjection month = transactionRepository.resolveMonthLimit(...);
```

Обратный порядок оставил бы окно, в котором второй параллельный запрос
прочитал бы ту же сумму. Мокая репозиторий, проверяй, что
`resolveMonthLimit` вызван **после** `lockMonth` — иначе тест закрепит
неверное поведение.

## Моки репозиториев

`resolveMonthLimit` возвращает `MonthLimitProjection`
(`repository/projection/MonthLimitProjection.java`) с тремя полями:
`getLimitSum()`, `getSpent()`, `getLimitId()`.

- `getSpent()` — уже израсходовано в месяце **до** текущей транзакции, в USD
- `getLimitSum()` — действующий лимит; верни `1000.00`, если тестируешь дефолт
- `getLimitId()` — `UUID` установленного лимита либо `null`; при `null`
  `Transaction.limit` останется пустым, поле `limit_id` — NULL

`limitRepository.getReferenceById(...)` вызывается только при непустом id,
его мокать не нужно.

## Что не надо тестировать здесь

Конвертацию в USD — это `MoneyUtils` и `TransactionServiceImpl`,
у неё отдельный тест. Кэширование курса — это
`ExchangeRateServiceImpl`. Валидацию запроса — это Bean Validation на
DTO, она проверяется через `@WebMvcTest`.
