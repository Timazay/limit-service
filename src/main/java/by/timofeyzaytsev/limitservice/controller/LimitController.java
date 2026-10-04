package by.timofeyzaytsev.limitservice.controller;

import by.timofeyzaytsev.limitservice.dto.request.LimitRequest;
import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import by.timofeyzaytsev.limitservice.service.LimitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class LimitController {

    private final LimitService limitService;

    @GetMapping
    public List<LimitResponse> getAll(
        @RequestParam @Pattern(regexp = "\\d{10}", message = "accountFrom must be 10 digits") String accountFrom,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return limitService.findAll(accountFrom, page, size);
    }

    @PostMapping
    public ResponseEntity<LimitResponse> create(@Valid @RequestBody LimitRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(limitService.create(request));
    }
}
