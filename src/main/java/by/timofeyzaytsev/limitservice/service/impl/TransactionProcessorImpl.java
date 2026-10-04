package by.timofeyzaytsev.limitservice.service.impl;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;
import by.timofeyzaytsev.limitservice.mapper.TransactionMapper;
import by.timofeyzaytsev.limitservice.model.ExchangeRate;
import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.model.Transaction;
import by.timofeyzaytsev.limitservice.repository.LimitRepository;
import by.timofeyzaytsev.limitservice.repository.TransactionRepository;
import by.timofeyzaytsev.limitservice.repository.projection.MonthLimitProjection;
import by.timofeyzaytsev.limitservice.service.TransactionProcessor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionProcessorImpl implements TransactionProcessor {

    private final TransactionRepository transactionRepository;
    private final LimitRepository limitRepository;
    private final TransactionMapper transactionMapper;
    private final AppProperties props;
    private final Clock clock;

    /**
     * Порядок шагов обязателен: сначала блокировка месяца, и только потом
     * чтение остатка. Обратный порядок оставил бы окно, в котором второй
     * параллельный запрос прочитал бы ту же сумму, что и первый.
     */
    @Override
    @Transactional
    public TransactionResponse record(
            TransactionRequest request,
            BigDecimal sumUsd,
            ZonedDateTime zoned,
            ExchangeRate exchangeRate) {

        OffsetDateTime monthStart = monthStartOf(zoned);

        transactionRepository.lockMonth(lockKey(request, monthStart));

        MonthLimitProjection month = transactionRepository.resolveMonthLimit(
            request.accountFrom(),
            request.expenseCategory().name(),
            request.datetime(),
            monthStart,
            monthStart.plusMonths(1),
            props.limit().defaultSum()
        );

        boolean exceeded = month.getSpent().add(sumUsd).compareTo(month.getLimitSum()) > 0;

        Transaction transaction = buildTransaction(
            request, sumUsd, limit(month.getLimitId()), exchangeRate, exceeded);

        return transactionMapper.toTransactionResponse(transactionRepository.save(transaction));
    }

    /**
     * Первый день месяца в часовом поясе сервиса: транзакция на границе
     * полуночи иначе может попасть в соседний месяц.
     */
    private OffsetDateTime monthStartOf(ZonedDateTime zoned) {
        return zoned
            .with(TemporalAdjusters.firstDayOfMonth())
            .toLocalDate()
            .atStartOfDay(props.timeZone())
            .toOffsetDateTime();
    }

    /**
     * Остаток лимита общий для (клиент, категория, месяц), поэтому сериализуем
     * только этот ключ: параллельные транзакции других клиентов ждать не будут.
     */
    private String lockKey(TransactionRequest request, OffsetDateTime monthStart) {
        return request.accountFrom() + ":" + request.expenseCategory() + ":" + monthStart.toLocalDate();
    }

    /**
     * Ссылка на лимит достаётся как прокси по идентификатору: колонка
     * {@code limit_id} заполняется сама, а сам лимит при этом не читается —
     * в ответе он не участвует. Действующего лимита в базе может не оказаться
     * (установленная строка удалена), тогда ссылка остаётся пустой.
     */
    private Limit limit(UUID limitId) {
        return limitId == null ? null : limitRepository.getReferenceById(limitId);
    }

    private Transaction buildTransaction(
        TransactionRequest request,
        BigDecimal sumUsd,
        Limit limit,
        ExchangeRate exchangeRate,
        boolean exceeded
    ) {

        return Transaction.builder()
            .accountFrom(request.accountFrom())
            .accountTo(request.accountTo())
            .currencyShortname(request.currencyShortname())
            .sum(request.sum())
            .sumUsd(sumUsd)
            .expenseCategory(request.expenseCategory())
            .datetime(request.datetime())
            .limitExceeded(exceeded)
            .limit(limit)
            .exchangeRate(exchangeRate)
            .createdAt(OffsetDateTime.now(clock))
            .build();
    }
}
