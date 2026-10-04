package by.timofeyzaytsev.limitservice.service.impl;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;
import by.timofeyzaytsev.limitservice.exception.BadGatewayException;
import by.timofeyzaytsev.limitservice.service.ExchangeRateService;
import by.timofeyzaytsev.limitservice.service.TransactionProcessor;
import by.timofeyzaytsev.limitservice.service.TransactionService;
import by.timofeyzaytsev.limitservice.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Приём транзакции. Транзакции БД здесь намеренно нет: внешний запрос курса
 * идёт первым, а транзакционная часть вынесена в TransactionProcessor.
 * Иначе соединение с PostgreSQL удерживалось бы на всё время запроса во
 * внешний API вместе с его таймаутами и ретраями.
 */
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final ExchangeRateService exchangeRateService;
    private final TransactionProcessor transactionProcessor;
    private final AppProperties props;

    @Override
    public TransactionResponse create(TransactionRequest request) {
        ZonedDateTime zoned = request.datetime().atZoneSameInstant(props.timeZone());

        BigDecimal sumUsd = MoneyUtils.divide(MoneyUtils.scale(request.sum()), rate(request, zoned));

        return transactionProcessor.record(request, sumUsd, zoned);
    }

    /**
     * Курс нулевой или отсутствует — делить на него нельзя, а принять
     * транзакцию без курса нельзя: сумма в USD попала бы в базу неверной.
     */
    private BigDecimal rate(TransactionRequest request, ZonedDateTime zoned) {
        BigDecimal rate = exchangeRateService.getRate(request.currencyShortname(), zoned.toLocalDate());

        if (rate == null || rate.signum() <= 0) {
            throw new BadGatewayException(
                "Exchange rate for " + request.currencyShortname() + " is not available");
        }

        return rate;
    }
}
