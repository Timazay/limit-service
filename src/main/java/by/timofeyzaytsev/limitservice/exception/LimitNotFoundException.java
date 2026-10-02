package by.timofeyzaytsev.limitservice.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class LimitNotFoundException extends ApiException {

    public LimitNotFoundException(UUID id) {
        super(
                HttpStatus.NOT_FOUND,
                "Limit not found",
                "Limit with id %s does not exist".formatted(id));
    }
}