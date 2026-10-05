package by.timofeyzaytsev.limitservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Страница списка вместе с общим числом записей.
 *
 * <p>Само число нужно клиенту, чтобы он знал, есть ли следующие страницы:
 * {@code content} этого не показывает, а повторно запрашивать все страницы ради
 * одного {@code size} нельзя.</p>
 *
 * @param content записи текущей страницы
 * @param total   сколько записей всего по запросу, без учёта страницы
 */
@Schema(description = "Страница списка вместе с общим числом записей")
public record PageResponse<T>(
    @Schema(description = "Записи текущей страницы")
    List<T> content,

    @Schema(description = "Сколько записей всего по запросу, без учёта страницы",
        example = "42")
    long total
) {
}