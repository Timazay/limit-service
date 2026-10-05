package by.timofeyzaytsev.limitservice.exception;

import by.timofeyzaytsev.limitservice.exception.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodParameterValidation(
            HandlerMethodValidationException ex,
            WebRequest request) {

        Map<String, List<String>> violations = new LinkedHashMap<>();
        ex.visitResults(new ViolationCollector(violations));

        List<String> crossParameterMessages = ex.getCrossParameterValidationResults().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .toList();
        if (!crossParameterMessages.isEmpty()) {
            violations.put("request", crossParameterMessages);
        }

        return validationProblem(violations, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleRequestBodyValidation(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        Map<String, List<String>> violations = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> violations
                .computeIfAbsent(error.getField(), key -> new ArrayList<>())
                .add(error.getDefaultMessage()));

        ex.getBindingResult().getGlobalErrors().forEach(error -> violations
                .computeIfAbsent(error.getObjectName(), key -> new ArrayList<>())
                .add(error.getDefaultMessage()));

        return validationProblem(violations, request);
    }

    private ResponseEntity<ProblemDetail> validationProblem(
            Map<String, List<String>> violations,
            WebRequest request) {

        String message = violations.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + String.join(" ", entry.getValue()))
                .collect(Collectors.joining("; "));

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation failed");
        problemDetail.setDetail(message);
        problemDetail.setProperty("errors", new ErrorResponseDto(message));
        problemDetail.setInstance(instanceUri(request));

        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            WebRequest request) {

        String detail = unreadableDetail(ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setTitle("Malformed request body");
        problemDetail.setProperty("errors", new ErrorResponseDto(detail));
        problemDetail.setInstance(instanceUri(request));

        return ResponseEntity.badRequest().body(problemDetail);
    }

    private static String unreadableDetail(HttpMessageNotReadableException ex) {
        String cause = ex.getMostSpecificCause().getMessage();

        return StringUtils.hasText(cause) ? cause : "Required request body is missing or malformed";
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail problemDetail = ex.getBody();
        problemDetail.setInstance(instanceUri(request));

        if (problemDetail.getStatus() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Business failure on {}", request.getDescription(false), ex);
        } else {
            log.debug("Business failure on {}: {}", request.getDescription(false), problemDetail.getDetail());
        }

        return ResponseEntity.status(ex.getStatusCode()).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            ProblemDetail problemDetail = errorResponse.getBody();
            problemDetail.setInstance(URI.create(request.getRequestURI()));

            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(problemDetail);
        }

        String traceId = UUID.randomUUID().toString();

        log.error("Unhandled exception, traceId={}", traceId, ex);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle("Internal server error");
        problemDetail.setDetail("Unexpected error occurred, contact support with trace id %s".formatted(traceId));
        problemDetail.setProperty("traceId", traceId);
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    private static final class ViolationCollector implements HandlerMethodValidationException.Visitor {

        private final Map<String, List<String>> violations;

        private ViolationCollector(Map<String, List<String>> violations) {
            this.violations = violations;
        }

        @Override
        public void cookieValue(CookieValue cookieValue, ParameterValidationResult result) {
            collect(cookieValue.name(), cookieValue.value(), result);
        }

        @Override
        public void matrixVariable(MatrixVariable matrixVariable, ParameterValidationResult result) {
            collect(matrixVariable.name(), matrixVariable.value(), result);
        }

        @Override
        public void pathVariable(PathVariable pathVariable, ParameterValidationResult result) {
            collect(pathVariable.name(), pathVariable.value(), result);
        }

        @Override
        public void requestHeader(RequestHeader requestHeader, ParameterValidationResult result) {
            collect(requestHeader.name(), requestHeader.value(), result);
        }

        @Override
        public void requestParam(RequestParam requestParam, ParameterValidationResult result) {
            collect(requestParam.name(), requestParam.value(), result);
        }

        @Override
        public void other(ParameterValidationResult result) {
            collect(null, null, result);
        }

        @Override
        public void modelAttribute(ModelAttribute modelAttribute, ParameterErrors errors) {
            errors.getAllErrors().forEach(error -> violations
                    .computeIfAbsent(errors.getObjectName(), key -> new ArrayList<>())
                    .add(error.getDefaultMessage()));
        }

        @Override
        public void requestBody(RequestBody requestBody, ParameterErrors errors) {
            modelAttribute(null, errors);
        }

        @Override
        public void requestPart(RequestPart requestPart, ParameterErrors errors) {
            String name = resolveName(requestPart.name(), null, errors.getMethodParameter());
            errors.getAllErrors().forEach(error -> violations
                    .computeIfAbsent(name, key -> new ArrayList<>())
                    .add(error.getDefaultMessage()));
        }

        @Override
        public void requestBodyValidationResult(RequestBody requestBody, ParameterValidationResult result) {
            collect(null, null, result);
        }

        private void collect(String annotationName, String annotationValue, ParameterValidationResult result) {
            String name = resolveName(annotationName, annotationValue, result.getMethodParameter());
            result.getResolvableErrors().forEach(error -> violations
                    .computeIfAbsent(name, key -> new ArrayList<>())
                    .add(error.getDefaultMessage()));
        }

        private static String resolveName(
                String annotationName,
                String annotationValue,
                MethodParameter parameter) {

            if (StringUtils.hasText(annotationName)) {
                return annotationName;
            }
            if (StringUtils.hasText(annotationValue)) {
                return annotationValue;
            }
            return parameter.getParameterName();
        }
    }

    private URI instanceUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return URI.create(servletWebRequest.getRequest().getRequestURI());
        }
        return URI.create("about:blank");
    }
}