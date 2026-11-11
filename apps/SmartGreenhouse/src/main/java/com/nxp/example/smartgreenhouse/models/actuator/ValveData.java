package com.nxp.example.smartgreenhouse.models.actuator;

public final class ValveData {

    private final int valveId;
    private final int trayId;

    private boolean available;

    private boolean open;

    private long lastOpenedAt;

    private long lastUpdated;

    public ValveData(int valveId, int trayId) {
        this.valveId = valveId;
        this.trayId = trayId;

        this.available = false;
        this.open = false;
        this.lastOpenedAt = 0L;
        this.lastUpdated = 0L;
    }

    public int getValveId() {
        return this.valveId;
    }

    public int getTrayId() {
        return this.trayId;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public boolean isOpen() {
        return this.open;
    }

    public long getLastOpenedAt() {
        return this.lastOpenedAt;
    }

    public long getLastUpdated() {
        return this.lastUpdated;
    }

    public void updateState(boolean newOpen, long timestamp) {
        if ((!this.available && newOpen) || (this.available && !this.open && newOpen)) {
            this.lastOpenedAt = timestamp;
        }

        this.open = newOpen;
        this.available = true;
        this.lastUpdated = timestamp;
    }

    public void restoreState(boolean available, boolean open, long lastOpenedAt, long lastUpdated) {
        this.available = available;
        this.open = open;
        this.lastOpenedAt = lastOpenedAt;
        this.lastUpdated = lastUpdated;
    }
}