---
name: create-db-migration
description: Use when adding or changing a database table, column, index, or foreign key. Triggers on changelog, changeset, Liquibase, "новая миграция", "создать таблицу", db.changelog-master, v1.0/NNN, preConditions, ddl-auto, "изменить схему БД".
---

# Новая миграция базы данных

Схемой владеет Liquibase. Hibernate только проверяет её соответствие
сущности и ничего не создаёт:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Отсюда правило: **миграция и `@Column` в сущности пишутся в одном
коммите.** Расхождение не поймает ни один тест в момент записи — оно
всплывёт только на старте приложения, когда Hibernate сравнит схему.

## Порядок работы

1. **Создай файл** `src/main/resources/db/changelog/v1.0/NNN-<описание>.yml`.
   Номер — следующий по порядку: сейчас последний `003-create-transactions`,
   значит твой будет `004-...`. Имя файла начисто с прошлых не
   переиспользуй: changeset уже применён, Liquibase сверяет по `id`.

2. **Добавь include** в `db/changelog/db.changelog-master.yml` — в конец
   списка, в том же формате:

   ```yaml
   databaseChangeLog:
     - include:
         file: db/changelog/v1.0/001-create-exchange-rates.yml
     - include:
         file: db/changelog/v1.0/002-create-limits.yml
     - include:
         file: db/changelog/v1.0/003-create-transactions.yml
     - include:
         file: db/changelog/v1.0/004-описание-таблицы.yml
   ```

Забытый include — самая частая ошибка: файл лежит, миграция
не выполняется, и схема в базе остаётся старой. При этом приложение
не «запустится на старой схеме», а **упадёт на старте**: `ddl-auto:
validate` сравнивает схему с сущностями и завершит запуск ошибкой о
недостающей колонке. Отказ громкий и указывающий на сущность, а не на
changelog, поэтому искать причину придётся в `.yml`, а не в `@Column`.

3. **Опиши changeset** по образцу `002-create-limits.yml`:

   ```yaml
   databaseChangeLog:
     - changeSet:
         id: 004-create-таблица
         author: Timofey Zaytsev
         changes:
           - createTable:
   ```

   `id` обязан совпадать с именем файля. `author` — твой, формат
   `Имя Фамилия`.

## Ловушка, которую скопируют молча

Сейчас во всех трёх существующих changeset стоит:

```yaml
preConditions:
  - onFail: MARK_RAN
```

**Это пустышка.** У `preConditions` должен быть реальный критерий, иначе
условие не проверяет ничего и `MARK_RAN` не срабатывает по назначению. В
новой миграции пиши настоящее условие:

```yaml
preConditions:
  - onFail: MARK_RAN
    tableExists:
      tableName: transactions
```

Аналогично работают `columnExists`, `indexExists`, `not tableExists`,
`onSql` с `sqlCheck`. Для `addColumn` уместно
`not columnExists: { tableName: transactions, columnName: new_col }`.

Это защита от падения, когда таблицу создали вручную вне Liquibase.

## Именования — бери с существующих файлов

| Что | Формат | Пример |
|---|---|---|
| Индекс | `idx_<таблица>_<колонки>` | `idx_transactions_account_period` |
| Внешний ключ | `fk_<таблица>_<на что>` | `fk_tx_limit` |
| Уникальность | `uq_<таблица>_<колонки>` | `uq_exchange_rate_pair_date` |

## Типы и значения по умолчанию

- Время — всегда `TIMESTAMPTZ`, никогда `TIMESTAMP`: приложение работает
  в часовом поясе `app.time-zone` и хранит `OffsetDateTime`
- `created_at` — `defaultValueComputed: now()` плюс
  `constraints: nullable: false`
- `id` — `UUID` с `primaryKey: true`
- Деньги — `NUMERIC(19,2)`, курсы — `NUMERIC(19,6)`
- Валюта — `VARCHAR(3)`, счёт клиента — `VARCHAR(10)`
- Значения по умолчанию задаются в БД, а не только в коде: у
  `limits.limit_currency_shortname` стоит `defaultValue: USD`, потому
  что лимит всегда в долларах и колонка не должна принимать пустое
  значение даже при вставке в обход сервиса
- Уникальность создаётся через `addUniqueConstraint` с
  `constraintName: uq_...`, а не через `createUniqueIndex`
- Категория расхода — `VARCHAR(20)` и хранится **верхним регистром**
  (`PRODUCT`, `SERVICE`), хотя API принимает и отдаёт нижний. Это делает
  `@Enumerated(EnumType.STRING)`, см. `model/enums/ExpenseCategory.java`.
  Не меняй регистр в БД без изменения enum

## Синхронизация с сущностью

После миграции поправь `@Column` в `@Entity`:

```java
@Column(name = "limit_sum", nullable = false, precision = 19, scale = 2)
private BigDecimal limitSum;
```

`length` тоже нужен: он совпадает с `VARCHAR(n)` в changelog, и Hibernate
сверяет его при `validate`.

## Что не надо делать

- Не используй `dropColumn` без явной необходимости: `transactions` уже
  хранит ссылку на лимит (`limit_id`), и её удаление ломает требование
  «указать лимит, который был превышен»
- Не добавляй `rollback`-блоки, если в остальных миграциях их нет —
  лишнее расхождение с проектом
- Не меняй уже применённый changeset. Добавляй новый: правка старого
  вызовет ошибку контроля сумм у всех, кто уже применил миграцию
- Не создавай индексы на FK-колонки без нужды: в проекте их нет,
  а удалений по `limits` и `exchange_rates` не бывает, так что
  автоматической индексации от PostgreSQL не требуется

## Проверка перед сдачей

- `mvn -q compile` проходит
- Приложение стартует: `validate` не ругается на схему
- `SELECT` к новым колонкам возвращает ожидаемые значения
