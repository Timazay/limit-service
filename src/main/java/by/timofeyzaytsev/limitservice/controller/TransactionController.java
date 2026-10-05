package by.timofeyzaytsev.limitservice.controller;

import by.timofeyzaytsev.limitservice.dto.request.TransactionRequest;
import by.timofeyzaytsev.limitservice.dto.response.ExceededTransactionResponse;
import by.timofeyzaytsev.limitservice.dto.response.PageResponse;
import by.timofeyzaytsev.limitservice.dto.response.TransactionResponse;
import by.timofeyzaytsev.limitservice.service.TransactionService;
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
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(
    name = "Транзакции",
    description = "Приём расходных операций и список превышений месячного лимита"
)
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
        summary = "Принять транзакцию",
        description = """
            Сумма пересчитывается в USD по курсу закрытия за день операции и
            сравнивается с остатком месячного лимита этой категории расхода.
            Транзакция помечается признаком limit_exceeded, если с учётом суммы
            остаток стал отрицательным.

            Границы месяца считаются в часовом поясе сервиса, а не в поясе
            клиента. Остаток защищён advisory-блокировкой на ключ
            «клиент + категория + месяц», поэтому параллельные запросы по
            одному месяцу не видят один и тот же остаток.

            Транзакции принимаются параллельно; при недоступности внешнего API
            курсов возвращается 502 и данные не теряются — повторная отправка
            безопасна, сумма в USD пересчитается заново."""
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Транзакция принята и сохранена",
            content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Запрос не прошёл валидацию",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "502", description = "Внешний API курсов недоступен или вернул некорректный курс",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(request));
    }

    /**
     * Клиентский запрос: какие транзакции превысили месячный лимит и по какому
     * именно лимиту.
     */
    @Operation(
        summary = "Транзакции, превысившие лимит",
        description = """
            Возвращает транзакции клиента с признаком limit_exceeded вместе с
            лимитом, который действовал на момент каждой из них. Благодаря
            сохранённой ссылке переустановка лимита позже не искажает ответ:
            для транзакций января показывается январский лимит, а не последний.

            Транзакции, превысившие дефолтные 1000 USD, в выдаче имеют пустые
            лимитовые поля: превышение считалось по значению по умолчанию,
            которого нет в таблице лимитов."""
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница превышений"),
        @ApiResponse(responseCode = "400", description = "Некорректные параметры запроса",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/exceeded")
    public PageResponse<ExceededTransactionResponse> getExceeded(
        @Parameter(description = "Счёт клиента, ровно 10 цифр", example = "0000000123")
        @RequestParam
        @Pattern(regexp = "\\d{10}", message = "accountFrom must be 10 digits") String accountFrom,
        @Parameter(description = "Номер страницы, с нуля", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Размер страницы, от 1 до 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return transactionService.findExceeded(accountFrom, page, size);
    }
}