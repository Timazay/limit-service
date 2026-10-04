package by.timofeyzaytsev.limitservice.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Категория расхода.
 *
 * <p>В API принимается и возвращается в виде {@code product} / {@code service},
 * как в задании, а в базу пишется верхом в верхнем регистре
 * ({@code EnumType.STRING} + {@code VARCHAR(20)}).</p>
 */
public enum ExpenseCategory {
    PRODUCT,
    SERVICE;

    @JsonCreator
    public static ExpenseCategory from(String value) {
        if (value == null) {
            return null;
        }

        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PRODUCT" -> PRODUCT;
            case "SERVICE" -> SERVICE;
            default -> throw new IllegalArgumentException("Unknown expense category: " + value);
        };
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}