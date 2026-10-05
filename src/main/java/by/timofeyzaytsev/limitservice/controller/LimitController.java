package by.timofeyzaytsev.limitservice.controller;

import by.timofeyzaytsev.limitservice.dto.request.LimitRequest;
import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import by.timofeyzaytsev.limitservice.dto.response.PageResponse;
import by.timofeyzaytsev.limitservice.service.LimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/limits")
@RequiredArgsConstructor
@Tag(
    name = "Лимиты",
    description = "Установка месячного лимита расходов и история лимитов клиента"
)
public class LimitController {

    private final LimitService limitService;

    @Operation(
        summary = "История лимитов клиента",
        description = """
            Возвращает лимиты клиента, свежие первыми. История, а не текущее
            значение: лимит, действующий сегодня, — это последний установленный.
            Количество возвращается отдельным полем total, чтобы клиент знал,
            есть ли следующие страницы."""
    )
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница лимитов"),
        @ApiResponse(responseCode = "400", description = "Некорректные параметры запроса",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public PageResponse<LimitResponse> getAll(
        @Parameter(description = "Счёт клиента, ровно 10 цифр", example = "0000000123")
        @RequestParam @Pattern(regexp = "\\d{10}", message = "accountFrom must be 10 digits") String accountFrom,
        @Parameter(description = "Номер страницы, с нуля", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Размер страницы, от 1 до 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return limitService.findAll(accountFrom, page, size);
    }

    @Operation(
        summary = "Установить новый лимит",
        description = """
            Лимит всегда в USD, категории расхода — товары (product) или услуги
            (service). Если лимит не установлен, применяется дефолт 1000 USD.

            Дата установки проставляется сервером: клиент её не передаёт и не
            может выставить дату в прошлом или будущем. Обновление существующего
            лимита не предусмотрено — новый лимит не заменяет старый, а
            добавляется в историю.

            Новый лимит действует только на транзакции, совершённые не раньше
            даты его установки. Уже проигранные флаги limit_exceeded при этом
            не пересчитываются."""
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Лимит установлен",
            content = @Content(schema = @Schema(implementation = LimitResponse.class))),
        @ApiResponse(responseCode = "400", description = "Запрос не прошёл валидацию",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<LimitResponse> create(@Valid @RequestBody LimitRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(limitService.create(request));
    }
}