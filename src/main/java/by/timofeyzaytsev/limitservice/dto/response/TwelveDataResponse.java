package by.timofeyzaytsev.limitservice.dto.response;

import java.math.BigDecimal;

public record TwelveDataResponse(
    String symbol,
    BigDecimal rate,
    Long timestamp
) {

}
