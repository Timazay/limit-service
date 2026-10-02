package by.timofeyzaytsev.limitservice.service.impl;

import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import by.timofeyzaytsev.limitservice.mapper.LimitMapper;
import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.repository.LimitRepository;
import by.timofeyzaytsev.limitservice.service.LimitService;
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

    @Override
    public List<LimitResponse> findAll(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "limitDatetime");

        List<Limit> limits = limitRepository
            .findAll(PageRequest.of(page, size, sort))
            .getContent();

        return limitMapper.toResponseList(limits);
    }
}
