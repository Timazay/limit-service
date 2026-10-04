package by.timofeyzaytsev.limitservice.service;

import by.timofeyzaytsev.limitservice.dto.request.LimitRequest;
import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import by.timofeyzaytsev.limitservice.dto.response.PageResponse;

public interface LimitService {

    PageResponse<LimitResponse> findAll(String accountFrom, int page, int size);

    LimitResponse create(LimitRequest request);
}
