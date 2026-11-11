package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorGaugeRangeProvider {

    private SensorGaugeRangeProvider() {
    }

    public static double getMinimum(int sensorId) {
        return 0;
    }

    public static double getMaximum(int sensorId) {
        switch (sensorId) {
            case SensorId.NITROGEN:
            case SensorId.FOSFOR:
                return 100;

            case SensorId.KALIUM:
                return 400;

            case SensorId.KELEMBAPAN_TANAH:
            case SensorId.KELEMBAPAN_UDARA:
                return 100;

            case SensorId.PH_TANAH:
                return 14;

            case SensorId.SUHU_TANAH:
            case SensorId.SUHU_UDARA:
                return 50;

            case SensorId.SALINITAS_TANAH:
                return 10;

            case SensorId.INTENSITAS_CAHAYA:
                return 100000;

            default:
                return 100;
        }
    }
}