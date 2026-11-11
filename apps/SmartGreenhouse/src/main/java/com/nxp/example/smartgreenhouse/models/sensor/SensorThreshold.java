package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorThreshold {

    private final int sensorId;

    private final float warningMinimum;
    private final float warningMaximum;

    private final float optimalMinimum;
    private final float optimalMaximum;

    public SensorThreshold(int sensorId, float warningMinimum, float optimalMinimum, float optimalMaximum, float warningMaximum) {
        this.sensorId = sensorId;
        this.warningMinimum = warningMinimum;
        this.optimalMinimum = optimalMinimum;
        this.optimalMaximum = optimalMaximum;
        this.warningMaximum = warningMaximum;
    }

    public int getSensorId() {
        return this.sensorId;
    }

    public float getWarningMinimum() {
        return this.warningMinimum;
    }

    public float getWarningMaximum() {
        return this.warningMaximum;
    }

    public float getOptimalMinimum() {
        return this.optimalMinimum;
    }

    public float getOptimalMaximum() {
        return this.optimalMaximum;
    }

    public boolean isOptimal(float value) {
        return value >= this.optimalMinimum && value <= this.optimalMaximum;
    }

    public boolean isWarning(float value) {
        return value >= this.warningMinimum && value <= this.warningMaximum;
    }
}