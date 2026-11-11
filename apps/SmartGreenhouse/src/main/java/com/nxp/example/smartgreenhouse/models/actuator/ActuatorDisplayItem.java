package com.nxp.example.smartgreenhouse.models.actuator;

public final class ActuatorDisplayItem {

    public static final int NO_TRAY_ID = -1;
    public static final int NO_TRAY_INDEX = -1;

    private final int trayIndex;
    private final int trayCount;

    private final int actuatorId;
    private final int trayId;

    private final boolean dataAvailable;
    private final boolean active;
    private final boolean flowActive;

    private final String durationText;

    private final String lastActivatedTimeText;
    private final String lastActivatedDateText;

    private final String lastUpdatedTimeText;
    private final String lastUpdatedDateText;

    public ActuatorDisplayItem(
            int actuatorId,
            int trayId,
            boolean dataAvailable,
            boolean active,
            boolean flowActive,
            String durationText,
            String lastActivatedTimeText,
            String lastActivatedDateText,
            String lastUpdatedTimeText,
            String lastUpdatedDateText,
            int trayIndex,
            int trayCount
    ) {
        this.actuatorId = actuatorId;
        this.trayId = trayId;
        this.dataAvailable = dataAvailable;
        this.active = active;
        this.flowActive = flowActive;
        this.durationText = durationText == null ? "" : durationText;

        this.lastActivatedTimeText = lastActivatedTimeText == null ? "-" : lastActivatedTimeText;
        this.lastActivatedDateText = lastActivatedDateText == null ? "-" : lastActivatedDateText;
        this.lastUpdatedTimeText = lastUpdatedTimeText == null ? "-" : lastUpdatedTimeText;
        this.lastUpdatedDateText = lastUpdatedDateText == null ? "-" : lastUpdatedDateText;
        this.trayIndex = trayIndex;
        this.trayCount = trayCount;
    }

    public int getActuatorId() {
        return this.actuatorId;
    }

    public int getTrayId() {
        return this.trayId;
    }

    public boolean hasTray() {
        return this.trayId != NO_TRAY_ID;
    }

    public boolean isDataAvailable() {
        return this.dataAvailable;
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean isFlowActive() {
        return this.flowActive;
    }

    public String getDurationText() {
        return this.durationText;
    }

    public String getLastActivatedTimeText() {
        return this.lastActivatedTimeText;
    }

    public String getLastActivatedDateText() {
        return this.lastActivatedDateText;
    }

    public String getLastUpdatedTimeText() {
        return this.lastUpdatedTimeText;
    }

    public String getLastUpdatedDateText() {
        return this.lastUpdatedDateText;
    }

    public boolean isPump() {
        return this.actuatorId == ActuatorId.POMPA_AIR;
    }

    public boolean isValve() {
        return this.actuatorId == ActuatorId.KATUP_AIR;
    }

    public int getTrayIndex() {
        return this.trayIndex;
    }

    public int getTrayCount() {
        return this.trayCount;
    }
}