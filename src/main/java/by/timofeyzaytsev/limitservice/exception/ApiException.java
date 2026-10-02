package by.timofeyzaytsev.limitservice.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

/**
 * Base class for business exceptions that can be translated into an RFC 9457 problem response.
 *
 * <p>Implementing {@link ErrorResponse} is enough for Spring MVC to render a proper
 * problem document, so subclasses only have to describe what went wrong.</p>
 *
 * <p>No {@code type} URI is set: there is no published documentation page to point at,
 * and RFC 9457 treats an absent {@code type} as {@code about:blank}. Clients should rely
 * on {@code title}, {@code detail} and {@code status}.</p>
 */
public abstract class ApiException extends RuntimeException implements ErrorResponse {

    private final transient ProblemDetail problemDetail;

    protected ApiException(HttpStatusCode status, String title, String detail) {
        this(status, title, detail, Map.of(), null);
    }

    protected ApiException(
            HttpStatusCode status,
            String title,
            String detail,
            Map<String, Object> properties,
            Throwable cause) {
        super(detail, cause);
        this.problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        this.problemDetail.setTitle(title);
        properties.forEach(this.problemDetail::setProperty);
    }

    @Override
    public ProblemDetail getBody() {
        return problemDetail;
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return HttpStatus.valueOf(problemDetail.getStatus());
    }
}