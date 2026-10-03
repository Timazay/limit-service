package by.timofeyzaytsev.limitservice.service.impl;

import by.timofeyzaytsev.limitservice.dto.request.LimitRequest;
import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import by.timofeyzaytsev.limitservice.mapper.LimitMapper;
import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.model.enums.Currency;
import by.timofeyzaytsev.limitservice.repository.LimitRepository;
import by.timofeyzaytsev.limitservice.service.LimitService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LimitServiceImpl implements LimitService {

    private final LimitRepository limitRepository;
    private final LimitMapper limitMapper;
    private final Clock clock;

    private final static BigDecimal DEFAULT_LIMIT_SUM = BigDecimal.valueOf(1000);

    @Override
    public List<LimitResponse> findAll(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "limitDatetime");

        List<Limit> limits = limitRepository
            .findAll(PageRequest.of(page, size, sort))
            .getContent();

        return limitMapper.toResponseList(limits);
    }

    @Override
    public LimitResponse create(LimitRequest request) {
        Limit limit = Limit.builder()
            .expenseCategory(request.expenseCategory())
            .limitSum(validateLimitSum(request))
            .limitDatetime(OffsetDateTime.now(clock))
            .limitCurrencyShortname(Currency.USD)
            .build();

        return limitMapper
            .toLimitResponse(
                limitRepository
                    .save(limit)
            );
    }

    private BigDecimal validateLimitSum(LimitRequest request) {
        return request.limitSum() == null ? DEFAULT_LIMIT_SUM : request.limitSum();
    }
}
