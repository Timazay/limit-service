package by.timofeyzaytsev.limitservice.service;

import by.timofeyzaytsev.limitservice.dto.request.LimitRequest;
import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import java.util.List;

public interface LimitService {

    List<LimitResponse> findAll(String accountFrom, int page, int size);

    LimitResponse create(LimitRequest request);
}
