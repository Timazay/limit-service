package by.timofeyzaytsev.limitservice.config.property;

import java.math.BigDecimal;

public record LimitProperties(
    BigDecimal defaultSum,
    String defaultCurrency
) {}
