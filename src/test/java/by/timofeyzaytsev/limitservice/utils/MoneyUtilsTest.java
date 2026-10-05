package by.timofeyzaytsev.limitservice.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MoneyUtils")
class MoneyUtilsTest {

    @Test
    void scale_WhenValueHasMoreThanTwoDecimals_ShouldRoundHalfUp() {
        BigDecimal result = MoneyUtils.scale(new BigDecimal("10000.455"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("10000.46"));
    }

    @Test
    void scale_WhenFractionIsBelowHalf_ShouldRoundDown() {
        BigDecimal result = MoneyUtils.scale(new BigDecimal("10000.454"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("10000.45"));
    }

    @Test
    void scale_WhenValueIsWhole_ShouldAddTwoZeros() {
        BigDecimal result = MoneyUtils.scale(new BigDecimal("1000"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.scale()).isEqualTo(MoneyUtils.SCALE);
    }

    @Test
    void scale_WhenValueIsNegative_ShouldRoundAwayFromZero() {
        BigDecimal result = MoneyUtils.scale(new BigDecimal("-0.005"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("-0.01"));
    }

    @Test
    void divide_WhenFractionIsRepeating_ShouldRoundToTwoDecimals() {
        BigDecimal result = MoneyUtils.divide(new BigDecimal("10.00"), new BigDecimal("3"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("3.33"));
        assertThat(result.scale()).isEqualTo(MoneyUtils.SCALE);
    }

    @Test
    void divide_WhenFractionIsTwoThirds_ShouldRoundUp() {
        BigDecimal result = MoneyUtils.divide(new BigDecimal("2.00"), new BigDecimal("3"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.67"));
    }

    @Test
    void divide_WhenSumIsInTenge_ShouldConvertToUsd() {
        BigDecimal result = MoneyUtils.divide(new BigDecimal("10000.45"), new BigDecimal("450.50"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("22.20"));
    }

    @Test
    void divide_WhenRateIsOne_ShouldKeepScaledSum() {
        BigDecimal result = MoneyUtils.divide(new BigDecimal("10000.45"), BigDecimal.ONE);

        assertThat(result).isEqualByComparingTo(new BigDecimal("10000.45"));
    }
}