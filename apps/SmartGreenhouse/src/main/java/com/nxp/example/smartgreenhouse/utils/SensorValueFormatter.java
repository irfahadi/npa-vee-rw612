package com.nxp.example.smartgreenhouse.utils;

import com.nxp.example.smartgreenhouse.models.sensor.SensorDefinition;

public final class SensorValueFormatter {

    private SensorValueFormatter() {}

    public static String format(SensorDefinition definition, float value) {
        if (definition == null) {
            return String.valueOf(value);
        }

        int decimalPlace =
                definition.getDecimalPlace();

        if (decimalPlace <= 0) {
            return String.valueOf(Math.round(value));
        }

        int multiplier = powerOfTen(decimalPlace);
        int scaledValue = Math.round(value * multiplier);
        int integerPart = scaledValue / multiplier;
        int decimalPart = Math.abs(scaledValue % multiplier);

        StringBuilder decimalText = new StringBuilder(String.valueOf(decimalPart));
        while (decimalText.length() < decimalPlace) {
            decimalText.insert(0, "0");
        }

        if (scaledValue < 0 && integerPart == 0) {
            return "-0." + decimalText;
        }

        return integerPart + "." + decimalText;
    }

    private static int powerOfTen(int exponent) {
        int result = 1;

        for (int i = 0; i < exponent; i++) {
            result *= 10;
        }

        return result;
    }
}