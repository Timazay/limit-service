package by.timofeyzaytsev.limitservice.exception;

import org.springframework.http.HttpStatus;

public class BadGatewayException extends ApiException {

  public BadGatewayException(String message) {
    super(
        HttpStatus.BAD_GATEWAY,
        "Something goes wrong with external service",
        message);
  }
}
