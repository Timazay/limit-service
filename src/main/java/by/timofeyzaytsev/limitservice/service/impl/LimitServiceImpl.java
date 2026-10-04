package by.timofeyzaytsev.limitservice.service.impl;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.dto.request.LimitRequest;
import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import by.timofeyzaytsev.limitservice.dto.response.PageResponse;
import by.timofeyzaytsev.limitservice.mapper.LimitMapper;
import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.repository.LimitRepository;
import by.timofeyzaytsev.limitservice.service.LimitService;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LimitServiceImpl implements LimitService {

    private final LimitRepository limitRepository;
    private final LimitMapper limitMapper;
    private final Clock clock;
    private final AppProperties appProperties;

    @Override
    public PageResponse<LimitResponse> findAll(String accountFrom, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "limitDatetime");

        Page<Limit> found = limitRepository.findByAccountFrom(accountFrom, PageRequest.of(page, size, sort));

        return new PageResponse<>(limitMapper.toResponseList(found.getContent()), found.getTotalElements());
    }

    @Override
    public LimitResponse create(LimitRequest request) {
        Limit limit = Limit.builder()
            .accountFrom(request.accountFrom())
            .expenseCategory(request.expenseCategory())
            .limitSum(request.limitSum())
            .limitDatetime(OffsetDateTime.now(clock))
            .limitCurrencyShortname(appProperties.limit().defaultCurrency())
            .createdAt(OffsetDateTime.now(clock))
            .build();

        return limitMapper
            .toLimitResponse(
                limitRepository
                    .save(limit)
            );
    }
}
