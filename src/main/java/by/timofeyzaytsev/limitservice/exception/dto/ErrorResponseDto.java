package by.timofeyzaytsev.limitservice.exception.dto;

/**
 * Body of the {@code 400 Bad Request} problem returned when a request fails validation.
 *
 * <p>Carries a single human readable message rather than a per-field breakdown, which keeps the
 * contract small: a client only has to show one string. The same text is repeated in the
 * standard {@code detail} member of the problem document, so clients that speak RFC 9457 can
 * ignore this extension entirely.</p>
 *
 * @param errorMessage validation failure description, for example
 *                     {@code "page: must be greater than or equal to 0"}
 */
public record ErrorResponseDto(String errorMessage) {

    public ErrorResponseDto {
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }
    }
}