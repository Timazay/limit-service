---
name: write-test
description: Use when writing or fixing any test in the limit service. Triggers on Test, @Test, "написать тест", "добавить тест", "покрыть тестами", Mockito, @Mock, AssertJ, "замокать", Clock.fixed, Testcontainers, WireMock, @SpringBootTest, application-test.yml, "юнит-тест", "интеграционный тест".
---

# Написание теста в limit-service

Общие правила проекта: как называть, где лежит, чем проверяешь, как
работаешь со временем. Сценарии и граничные случаи для конкретной логики
живут в профильных скиллах — прежде всего в `write-limit-logic-test`.

## Правило именования — обязательное

```
<метод>_When<условие>_Should<ожидаемый результат>
```

Правило действует **только на методы `@Test`**. Служебные методы под него
не попадают: `setUp` из `@BeforeEach` и `contextLoads` называются как
обычно.

Три части, разделённые одиночным подчёркиванием. Внутри частей — только
CamelCase, без пробелов, кириллицы и знаков препинания.

| Часть | Что это | Пример |
|---|---|---|
| до `_` | имя метода прод-класса **дословно** | `record`, `resolve`, `create`, `scale` |
| `When` | состояние, из-за которого метод ведёт себя необычно | `WhenRemainingIsZero` |
| `Should` | наблюдаемый результат | `ShouldNotExceedLimit` |

Имя метода берётся дословно, потому что тест — документация к методу:
`record_...` относится к `TransactionProcessorImpl.record`, а
`monthStartOf_...` — к приватному `monthStartOf`, который проверяется
через публичный `record`.

Примеры по реальным классам проекта:

```java
scale_WhenValueHasMoreThanTwoDecimals_ShouldRoundHalfUp
divide_WhenFractionIsRepeating_ShouldRoundToTwoDecimals
record_WhenRemainingIsZero_ShouldNotExceedLimit
record_WhenSpentPlusTransactionIsAboveLimit_ShouldMarkExceeded
record_WhenMonthLimitIsNotInstalled_ShouldUseDefaultLimitSum
record_WhenLimitIdIsNull_ShouldNotReferenceAnyLimit
record_WhenTransactionIsLastDayOfMonth_ShouldResolveLimitsOfThatMonth
resolve_WhenCurrencyIsUsd_ShouldReturnRateOneAndNoStoredRate
resolve_WhenCloseIsAbsent_ShouldFallBackToPreviousClose
resolve_WhenStoredRateIsMissing_ShouldCallProviderAndStoreRate
resolve_WhenProviderReturnsNonPositiveRate_ShouldThrowBadGatewayException
create_WhenRequestHasNoDate_ShouldSetLimitDatetimeFromClock
findAll_WhenClientHasLimits_ShouldReturnNewestFirstWithTotalCount
```

Антипримеры — такие тесты не пишутся:

| Имя | Почему неверно |
|---|---|
| `testRecord()` | нет ни условия, ни результата |
| `recordTest()` | то же |
| `shouldExceed_whenLimitReached()` | `should` в начале, порядок сегментов перепутан |
| `record_ShouldMarkExceeded()` | выброшена средняя часть: нет условия, ради которого метод ведёт себя так |
| `findAll_ShouldReturnTotalCount()` | то же — при том что у `findAll` есть чем различать случаи: первая страница или не последняя |
| `record_WhenLimit_ShouldTrue()` | результат без конкретики, не читается как утверждение |
| `record1()`, `record2()` | порядковые номера ничего не сообщают о случае |

Отдельно про `Should` без `When`: соблазн короче, и правило формально
нарушается. В проекте такие имена уже вылавливались — если не можешь
сформулировать условие, скорее всего тест проверяет не одну вещь, и его
стоит разбить на два.

## Где лежит тест и как называется класс

Каталог `src/test/java` зеркалит пакеты `src/main/java` от корня
`by.timofeyzaytsev.limitservice`.

| Тип теста | Имя класса | Что поднимает |
|---|---|---|
| Юнит | `<Класс>Test` | ничего, только Mockito |
| Интеграционный с БД | `<Класс>IT` | `@SpringBootTest` + Testcontainers |
| Сквозной по HTTP | `<Сценарий>ApiIT` | `@SpringBootTest(RANDOM_PORT)` + HTTP-клиент |
| Валидация DTO | `<Класс>ValidationTest` | `@WebMvcTest(<Контроллер>.class)` |

Примеры: `MoneyUtilsTest`, `TransactionProcessorImplTest`,
`LimitServiceImplTest`, `TransactionRepositoryIT`, `ExceededTransactionsApiIT`.

Суффикс `IT` для интеграционных — не формальность: они требуют Docker,
и потому запускаются фазой `verify`, а не `test`.

## AssertJ и Mockito

Только AssertJ. JUnit-овские `assertEquals`, `assertTrue` и `fail` в
проекте не используются — причин несколько, и одинаковых по смыслу
сообщений об ошибке тоже.

```java
assertThat(response.limitExceeded()).isTrue();
assertThat(month.getLimitSum()).isEqualByComparingTo(new BigDecimal("1000.00"));
```

`isEqualTo` для `BigDecimal` **не подходит**: `1000.0` и `1000.00` равны
по числу, но не по `equals`. Деньги сравнивай через
`isEqualByComparingTo`, либо явно через `MoneyUtils.SCALE`.

Правила:

- **Один `@Test` — одна причина падения.** Несколько `assertThat`
  внутри одного теста допустимы, только если они описывают одно
  наблюдение: «остаток посчитан неверно» — это про `limitSum`, `spent`
  и итоговый флаг сразу.
- **Не мокай то, что не коллаборирует.** `MoneyUtils`, `BigDecimal`,
  record'ы (`TransactionRequest`, `RateQuote`, `ResolvedRate`) и enums
  создаются по-настоящему. Мок на record'е бесполезен — у него нет
  поведения, которое можно подменить.
- **Не мокай то, что тест не проверяет.** Коллаборатор, чьё значение
  не влияет на исход, мокать незачем: лишний `@Mock` создаёт
  впечатление покрытия, которого нет.
- **Репозиторий мокается всегда**, когда проверяется сервис.
  Настоящий репозиторий требует БД — это уже интеграционный тест.
- **`verify` — на конкретный вызов.** `verifyNoMoreInteractions()`
  превращает тест в хрупкий: он падает от любого безобидного нового
  вызова. Используй его, только если «больше ничего не вызывалось» —
  часть контракта.
- **`InOrder` — только там, где порядок часть контракта.** Пример:
  `lockMonth` обязан вызываться раньше `resolveMonthLimit`
  (`TransactionProcessorImpl.record`), иначе параллельные запросы
  прочтут один остаток. Порядок вызовов маппера проверять не надо.

Внедрение зависимостей в тесте:

```java
@ExtendWith(MockitoExtension.class)
class TransactionProcessorImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private LimitRepository limitRepository;
    @Mock private TransactionMapper transactionMapper;

    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessorImpl(
            transactionRepository, limitRepository, transactionMapper, props(), fixedClock());
    }
}
```

Предпочитай ручной `new` в `@BeforeEach` вместо `@InjectMocks`:
конструкторы в проекте генерирует Lombok (`@RequiredArgsConstructor`),
и `@InjectMocks` подбирает аргументы по типам — при двух зависимостях
одного типа он выберет наугад, молчаливый поломка теста вместо ошибки
компиляции.

## Время — только через Clock

**Никогда** `OffsetDateTime.now()`, `LocalDate.now()` и
`ZonedDateTime.now()` в тесте. Границы месяца считаются в
`app.time-zone`, поэтому тест с реальным временем нестабилен: он
развалится в полночь первого числа и не воспроизведёт сценарий из
задания.

Зона в тестах — `ZoneId.of("Asia/Almaty")`, дефолт из
`application.yml`.

```java
private static final ZoneId ZONE = ZoneId.of("Asia/Almaty");
Clock fixedClock = Clock.fixed(Instant.parse("2022-01-11T06:00:00Z"), ZONE);
```

### Ловушка: подмена одного `Clock` не подменяет зону

`TransactionProcessorImpl` принимает **и** `Clock`, **и** `AppProperties`,
и границы месяца считает по `props.timeZone()`
(`TransactionProcessorImpl.monthStartOf`), а не по часам. `@MockitoBean`
или `@Mock` на `Clock` двигает момент времени, но оставляет зону
`monthStartOf` системной, и на стыке месяца тест разойдётся с
намерением.

В юнит-тесте с настоящим конструктором подставляй **оба**: `Clock` для
момента и настоящий `AppProperties` с нужной зоной.

```java
private static AppProperties props() {
    return new AppProperties(
        ZoneId.of("Asia/Almaty"),
        new LimitProperties(new BigDecimal("1000.00"), "USD"),
        null);
}

private static AppProperties props(ZoneId zone, BigDecimal defaultSum) {
    return new AppProperties(
        zone,
        new LimitProperties(defaultSum, "USD"),
        null);
}
```

`AppProperties` — record, третий компонент (`exchangeRate`) в
юнит-тестах лимитов не используется, поэтому `null` достаточно.
Дефолтный лимит и валюта лимита в тестах лимитов тоже берутся отсюда,
а не из `application.yml`: иначе тест поедет, если дефолт поменяют.

`@PrePersist` во всех трёх сущностях вызывает `OffsetDateTime.now()`
напрямую. Это не мешает: сервисы сами проставляют `createdAt` до
сохранения, и `@PrePersist` проверка не срабатывает.

## Зависимости и профили

Тестовые зависимости — в `pom.xml`, `<scope>test</scope>`, версиями из
BOM `spring-boot-dependencies` (`spring-boot-starter-parent` уже его
подключает). Версию в `<version>` пиши **только** если артефакта в BOM
нет.

Управляются BOM и версию указывать не нужно:

- `org.springframework.boot:spring-boot-testcontainers`
- `org.testcontainers:testcontainers-postgresql` — обрати внимание на
  имя
- `org.testcontainers:testcontainers-junit-jupiter`

**Ловушка, из-за которой тест не компилируется:** Boot 4.1.1 управляет
Testcontainers **2.0.5**, а в 2.x модули переименованы — координаты
`org.testcontainers:postgresql` и `org.testcontainers:junit-jupiter`
больше не существуют. Префикс `testcontainers-` обязателен. Версии
1.x, на которые есть примеры в интернете, здесь не подойдут.

Не управляется BOM, версию задавай явно:

- `org.wiremock:wiremock-standalone` — 3.13.2.

**Ловушка, из-за которой падает даже старт WireMock.** С версии 3.x HTTP-сервер
вынесен из основного артефакта: голый `wiremock` не содержит реализации и
даёт `FatalStartupException` про отсутствие `HttpServerFactory`, а
`wiremock-jetty12` падает с `NoSuchMethodError` на
`Environment.ensure(String)` — Boot управляет Jetty 12.1.12, где сигнатура
`ensure(String, Class)`, а WireMock ждёт более новую. `wiremock-standalone`
шивает Jetty внутрь и не конфликтует с управлением версиями Boot. Пакет
классов при этом прежний: `com.github.tomakehurst.wiremock`.

Одна зависимость — одно объявление. В `pom.xml` не должно быть двух
блоков `spring-boot-starter-test`: Maven возьмёт первый, а второй будет
молчаливым дублем, который при добавлении чего-то в один из них даст
непонятную ошибку.

## Фазы сборки

`maven-failsafe-plugin` с `<includes>**/*IT.java</includes>` — суффикс `IT`
уезжает в фазу `verify`. Поэтому:

- `./mvnw test` — только юниты, Docker не нужен, секунды
- `./mvnw verify` — плюс тесты с БД и внешним API

Это не формальность, а требование задания: полный набор должен запускаться
стандартной командой сборки. Разносить по фазам выгодно ещё и тем, что
быстрый цикл обратной связи не ждёт контейнер.

## Поднятие БД в тестах

Общий контейнер живёт в абстрактном базовом классе — один на прогон,
Spring переиспользует один `ApplicationContext`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");
}
```

`@ServiceConnection` подставляет `datasource.url` сам — ручной
`application-test.yml` с адресом БД не нужен и был бы неправдой. Схему
накатывает Liquibase при старте контекста.

**Ловушка, ломающая сборку с нуля:** в `pom.xml` у executions компилятора
объявлен `annotationProcessorPaths`, и без `<proc>full</proc>` процессоры на
JDK 21 не запускаются. MapStruct молча не создаёт `*MapperImpl`, сборка
проходит, а контекст падает на `No qualifying bean of type 'LimitMapper'`.
Ошибка выглядит как проблема бина, хотя это проблема сборки.

## Внешний API в тестах

WireMock поднимай **в статическом инициализаторе**, а не в `@BeforeAll`:
контекст Spring собирается до before-all методов, а адрес сервера нужен уже
в `@DynamicPropertySource`.

```java
private static final WireMockServer WIRE_MOCK = startWireMock();

@DynamicPropertySource
static void rateProperties(DynamicPropertyRegistry registry) {
    registry.add("app.exchange-rate.twelvedata.base-url",
        () -> "http://localhost:" + WIRE_MOCK.port());
}
```

Один стаб покрывает много дат: провайдер берёт последний бар, не позже
запрошенной даты, поэтому ответ сразу содержит весь нужный диапазон, и
каждая транзакция не требует своего стаба.

Провайдер в тесте остаётся `twelvedata`, а не `stub` — иначе замоканный
внешний API не будет вызван и тест ничего не проверит. `api-key` при этом
обязателен: `RestClientConfig` создаёт бин без условия и читает это поле при
старте контекста (см. `add-exchange-rate-provider`).

## Обращение к своему API из теста

`TestRestTemplate` в Spring Boot 4 **отсутствует**. Рабочий вариант —
`RestClient`, он уже в зависимостях:

```java
rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
```

Ответ лучше разбирать типизированным:

```java
rest.get().uri("/api/v1/transactions/exceeded?accountFrom={a}", ACCOUNT_FROM)
    .retrieve()
    .toEntity(new ParameterizedTypeReference<PageResponse<ExceededTransactionResponse>>() {});
```

Не разбирай ответ в `Map`: JSON-число `1000.00` придёт как `Double`, и
`isEqualTo(new BigDecimal("1000.00"))` упадёт при верном ответе сервера.

**Ловушка на датах:** Jackson клиента нормализует смещение при разборе
`OffsetDateTime` в UTC, и полночь `2022-01-01T00:00+06:00` станет 31
декабря. Сравнивай моменты, а не локальные даты:

```java
assertThat(response.limitDatetime().toInstant())
    .isEqualTo(expectedOffsetDateTime.toInstant());
```

Смещение в JSON при этом остаётся правильным — расходится именно разбор на
клиенте.

Тестовые профили:

- `src/test/resources/application-test.yml` — профиль `test`, подключается
  аннотацией `@ActiveProfiles("test")`. Провайдера курсов в нём оставляем
  `twelvedata`, а не `stub`, если тест проверяет работу с внешним API.
- Ключ `api-key` в тестовом профиле обязателен в любом случае: бины
  конфигурации читают его при старте контекста, даже когда провайдер
  выключен, и без него контекст не поднимется (см.
  `add-exchange-rate-provider`).

## Чего делать не надо

- **Не поднимай `@SpringBootTest` ради арифметики.** `MoneyUtils` и
  расчёт флага проверяются юнитом за миллисекунды; контекст Spring
  поднимается секундами и тянет БД.
- **Не тестируй Spring-бин ради Spring-бина.** `ClockConfig` и
  `ValidationConfig` проверяются фактом работы в интеграционном тесте;
  отдельного `ClockConfigTest` не нужно.
- **Не дублируй `write-limit-logic-test`.** Сценарии из таблицы задания,
  четыре края лимита и моки `MonthLimitProjection` описаны там. Здесь
  общие правила.
- **Не пиши тест на `equals`/`hashCode` сущностей.** В проекте они
  генерируются Lombok и не несут доменной логики; ценность теста
  здесь нулевая.
- **Не подставляй `datetime` через `LocalDateTime` без зоны.**
  `TransactionRequest.datetime` — `OffsetDateTime`, и расчёт месяца
  зависит от зоны: `2022-02-01T00:00:00+06:00` в Алматы это
  31 января.