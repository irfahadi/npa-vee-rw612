package com.nxp.example.smartgreenhouse.models.actuator;

public final class PumpData {

    private boolean active;

    private long activeSince;

    private long lastActiveDuration;

    private long lastUpdated;

    public PumpData() {
        this.active = false;
        this.activeSince = 0;
        this.lastActiveDuration = 0;
        this.lastUpdated = 0;
    }

    public boolean isActive() {
        return this.active;
    }

    public long getActiveSince() {
        return this.activeSince;
    }

    public long getLastActiveDuration() {
        return this.lastActiveDuration;
    }

    public long getLastUpdated() {
        return this.lastUpdated;
    }

    public void updateState(boolean newActive, long timestamp) {
        boolean previousActive = this.active;

        if (!previousActive && newActive) {
            this.activeSince = timestamp;
        }

        if (previousActive && !newActive) {
            if (this.activeSince > 0 && timestamp >= this.activeSince) {
                this.lastActiveDuration = timestamp - this.activeSince;
            }
        }

        this.active = newActive;
        this.lastUpdated = timestamp;
    }

    public long getCurrentActiveDuration(long currentTimestamp) {
        if (!this.active || this.activeSince <= 0 || currentTimestamp < this.activeSince) {
            return this.lastActiveDuration;
        }

        return currentTimestamp - this.activeSince;
    }
}