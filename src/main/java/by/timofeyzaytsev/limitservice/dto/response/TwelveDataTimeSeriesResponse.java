package by.timofeyzaytsev.limitservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Ответ {@code /time_series} для дневного интервала.
 *
 * <p>Числа приходят строками, поэтому разбираются вручную: у валютных пар
 * значащие цифры теряются, если положиться на автоматический разбор.</p>
 *
 * <p>Поля {@code code}, {@code status} и {@code message} — это тело ошибки,
 * TwelveData отдаёт её кодом 200.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwelveDataTimeSeriesResponse(
    Meta meta,
    List<Value> values,
    Integer code,
    String status,
    String message
) {

    public record Meta(String symbol, String interval, String currency) {
    }

    public record Value(
        String datetime,
        String open,
        String high,
        String low,
        String close,
        @JsonProperty("previous_close") String previousClose,
        String volume
    ) {

    }
}