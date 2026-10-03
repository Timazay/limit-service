package by.timofeyzaytsev.limitservice.mapper;

import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import by.timofeyzaytsev.limitservice.model.Limit;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LimitMapper {

    LimitResponse toLimitResponse(Limit limit);

    List<LimitResponse> toResponseList(List<Limit> limits);
}
