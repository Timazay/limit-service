package by.timofeyzaytsev.limitservice.dto.response;

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
public record PageResponse<T>(List<T> content, long total) {
}
