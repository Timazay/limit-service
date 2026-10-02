package by.timofeyzaytsev.limitservice.service;

import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import java.util.List;

public interface LimitService {

    List<LimitResponse> findAll(int page, int size);
}
