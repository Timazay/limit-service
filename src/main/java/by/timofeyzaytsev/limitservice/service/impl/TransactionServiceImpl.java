package by.timofeyzaytsev.limitservice.service.impl;

import by.timofeyzaytsev.limitservice.client.ExchangeRateProvider;
import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;
import by.timofeyzaytsev.limitservice.mapper.TransactionMapper;
import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.model.Transaction;
import by.timofeyzaytsev.limitservice.repository.LimitRepository;
import by.timofeyzaytsev.limitservice.repository.TransactionRepository;
import by.timofeyzaytsev.limitservice.service.TransactionService;
import by.timofeyzaytsev.limitservice.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final LimitRepository limitRepository;
    private final ExchangeRateProvider exchangeRateProvider;
    private final AppProperties props;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        BigDecimal rate = exchangeRateProvider.getRate(
            request.currencyShortname(),
            request.datetime().atZoneSameInstant(props.timeZone()).toLocalDate()
        );
        BigDecimal sumUsd = request.sum()
            .divide(rate, MoneyUtils.SCALE, MoneyUtils.ROUNDING);

        ZonedDateTime zoned = request.datetime().atZoneSameInstant(props.timeZone());
        OffsetDateTime monthStart = monthStartOf(zoned);
        OffsetDateTime monthEnd = monthStart.plusMonths(1);

        transactionRepository.lockMonth(lockKey(request, monthStart));

        Limit limit = limitRepository
            .findEffectiveLimit(
                request.accountFrom(),
                request.expenseCategory(),
                request.datetime()
            )
            .orElse(null);

        BigDecimal limitSum = limit != null
            ? limit.getLimitSum()
            : props.limit().defaultSum();

        BigDecimal spent = transactionRepository.sumExpensesForPeriod(
            request.accountFrom(), request.expenseCategory(), monthStart, monthEnd
        );

        boolean exceeded = spent.add(sumUsd).compareTo(limitSum) > 0;

        Transaction transaction = buildTransaction(request, sumUsd, limit, exceeded);

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

    private Transaction buildTransaction(
        TransactionRequest request,
        BigDecimal sumUsd,
        Limit limit,
        boolean exceeded) {

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
            .build();
    }
}
