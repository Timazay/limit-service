---
name: add-exchange-rate-provider
description: Use when adding a new exchange rate source or currency provider to the limit service. Triggers on ExchangeRateProvider, RateQuote, fetchQuote, "add exchange rate provider", "new currency source", app.exchange-rate.provider, "подключить новый провайдер курсов".
---

# Добавление провайдера курсов валют

Провайдер отвечает только за поход во внешний API. Он не знает про базу,
не решает, брать курс из хранилища или запросить заново, и не занимается
конвертацией. За всё это отвечает `ExchangeRateServiceImpl`.

## Контракт

Интерфейс — `src/main/java/by/timofeyzaytsev/limitservice/client/ExchangeRateProvider.java`:

```java
RateQuote fetchQuote(String currencyPair, java.time.LocalDate date);
```

Возвращается `RateQuote(BigDecimal close, BigDecimal previousClose)`
(`client/RateQuote.java`). Метод `rate()` у record'а сам выбирает `close`,
а если его нет — `previousClose`. **Не сочиняй третье поле:** по заданию
курс считается по закрытию, а если закрытия за текущий день нет, берётся
предыдущее.

## Направление пары — главное, что легко перепутать

Провайдер отвечает за приведение курса к направлению самой пары. Для
`USD/KZT` это «сколько KZT за 1 USD».

При этом `ExchangeRateServiceImpl.resolve` **всегда** строит пару как
`USD + "/" + валюта` (строка 60), то есть запрашивает всегда `USD/XXX` и
получает ответ в том же направлении. Инверсию в коде сервиса делать не надо.

Историческая справка: `TwelveDataExchangeRateProvider` запрашивает
`USD/XXX`, потому что TwelveData отвечает по биржевой конвенции
(`AAA/BBB` = сколько `BBB` за 1 `AAA`) и пары `KZT/USD` просто не знает.
Если твой внешний API, наоборот, отдаёт пару в обратном направлении —
инверсию делаешь ты внутри своей реализации, но наружу всё равно отдаёшь
направление пары.

## Порядок работы

1. **Создай класс** в `client/impl/<Имя>ExchangeRateProvider.java` (для
   внешнего API) или `client/stub/<Имя>ExchangeRateProvider.java` (для
   заглушки).

2. **Отметь бин условной аннотацией** — так выбирается активный провайдер:

   ```java
   @Component
   @ConditionalOnProperty(name = "app.exchange-rate.provider", havingValue = "<имя>")
   @RequiredArgsConstructor
   @Slf4j
   public class <Имя>ExchangeRateProvider implements ExchangeRateProvider {
   ```

   Точный образец — `TwelveDataExchangeRateProvider.java:42`.

   **Внимание:** у `StubExchangeRateProvider.java:19` стоит
   `matchIfMissing = true`, поэтому заглушка включается при отсутствии
   свойства вообще. Новый провайдер сработает только при явном значении
   `app.exchange-rate.provider=<имя>`, и это надо сказать пользователю.

   **Вторая ловушка, реальная:** `RestClientConfig.java:17` создаёт бин
   `twelveDataRestClient` **безусловно** — без `@ConditionalOnProperty`.
   Он читает `props.exchangeRate().twelvedata()`, а `api-key` в
   `application.yml` задан как `${TWELVEDATA_API_KEY}` без дефолта. Поэтому
   даже в режиме заглушки, где провайдер TwelveData выключен, контекст не
   поднимется без переменной окружения. Если добавляешь свой `RestClient`,
   сделай его бин условным либо задай дефолт, иначе проблема повторится
   и для твоего провайдера.

3. **Добавь блок конфигурации** в `application.yml` рядом с существующим
   `twelvedata`. Ключи `TWELVEDATA_API_KEY` и подобные секреты оформляй
   через переменную окружения с дефолтом: `${MY_API_KEY:}` — иначе
   приложение не стартует в режиме заглушки.

4. **Расширь properties**, если у провайдера есть свои настройки:
   `config/property/ExchangeRateProperties.java` — добавить вложенный
   record рядом с `TwelveDataProperties`. Читай через
   `props.exchangeRate().<имя>()`, как это делает
   `TwelveDataExchangeRateProvider` для `props.exchangeRate().twelvedata().apiKey()`.

5. **Значение `source` в базе** берётся из `props.exchangeRate().provider()`
   автоматически в `ExchangeRateServiceImpl.fetchAndStore` — руками его
   задавать не надо.

## Обработка сбоев

При любом сбое внешнего API кидай `BadGatewayException`
(`exception/BadGatewayException.java`) — `GlobalExceptionHandler` отдаёт по
нему 502 с корректным ProblemDetail. Не отдавай клиенту нулевой или
отрицательный курс: `ExchangeRateServiceImpl.fetchAndStore` это проверит и
ответит 502, но лучше не доводить до этого.

Если внешний API медленный, используй `RetryTemplate` из `RetryConfig` —
он уже настроен на `RestClientException` и исключает 404. Не добавляй
своих таймаутов в логике метода: они задаются в `RestClientConfig` через
`SimpleClientHttpRequestFactory`.

## Что проверить перед сдачей

- `mvn -q compile` проходит
- Приложение стартует в режиме заглушки без ключа нового провайдера:
  `mvn spring-boot:run -Dspring-boot.run.arguments=--app.exchange-rate.provider=stub`
- Запуск с новым провайдером: `--app.exchange-rate.provider=<имя>`
- Строка появилась в `exchange_rates` с правильным `source`, а
  `transactions.exchange_rate_id` перестал быть NULL

## Чего делать не надо

- Не трогай `ExchangeRateServiceImpl` — он работает через интерфейс и
  ничего не знает про конкретные источники
- Не добавляй логику кэширования в провайдера: это работа
  `ExchangeRateServiceImpl`, который сначала читает `exchange_rates` и
  только при промахе вызывает `fetchQuote`
- Не хардкодь пары валют. Валюты приходят из запроса клиента, и все
  незнакомые коды должны так или иначе отдавать курс
