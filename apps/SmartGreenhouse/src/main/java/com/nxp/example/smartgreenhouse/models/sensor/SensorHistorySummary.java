package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorHistorySummary {

    private final boolean hasData;
    private final float minimum;
    private final float maximum;
    private final float average;
    private final String lastUpdate;

    public SensorHistorySummary(boolean hasData, float minimum, float maximum, float average, String lastUpdate) {
        this.hasData = hasData;
        this.minimum = minimum;
        this.maximum = maximum;
        this.average = average;
        this.lastUpdate = lastUpdate == null ? "" : lastUpdate;
    }

    public static SensorHistorySummary empty() {
        return new SensorHistorySummary(false, 0, 0, 0, "");
    }

    public boolean hasData() {
        return this.hasData;
    }

    public float getMinimum() {
        return this.minimum;
    }

    public float getMaximum() {
        return this.maximum;
    }

    public float getAverage() {
        return this.average;
    }

    public String getLastUpdate() {
        return this.lastUpdate;
    }
}