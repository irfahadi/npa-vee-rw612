package com.nxp.example.smartgreenhouse.models.actuator;

public final class ActuatorDisplayBuilder {

    private ActuatorDisplayBuilder() {
    }

    public static ActuatorDisplayItem buildPump(PumpData pumpData, long currentTimestamp) {
        if (pumpData == null) {
            return null;
        }

        long activeDuration = pumpData.getCurrentActiveDuration(currentTimestamp);
        long lastActivatedAt = pumpData.getActiveSince();
        long lastUpdated = pumpData.getLastUpdated();
        return new ActuatorDisplayItem(
                ActuatorId.POMPA_AIR,
                ActuatorDisplayItem.NO_TRAY_ID,
                lastUpdated > 0L,
                pumpData.isActive(),
                false,
                ActuatorDisplayFormatter.formatDuration(activeDuration),
                ActuatorDisplayFormatter.formatTime(lastActivatedAt),
                ActuatorDisplayFormatter.formatDate(lastActivatedAt),
                ActuatorDisplayFormatter.formatTime(lastUpdated),
                ActuatorDisplayFormatter.formatDate(lastUpdated),
                ActuatorDisplayItem.NO_TRAY_INDEX,
                0
        );
    }

    public static ActuatorDisplayItem buildValve(ValveData valveData, int trayIndex, int trayCount) {
        if (valveData == null) {
            return null;
        }

        long lastOpenedAt = valveData.getLastOpenedAt();
        long lastUpdated = valveData.getLastUpdated();
        boolean flowAlwaysActive = true;
        return new ActuatorDisplayItem(
                ActuatorId.KATUP_AIR,
                valveData.getTrayId(),
                valveData.isAvailable(),
                valveData.isOpen(),
                flowAlwaysActive,
                "",
                ActuatorDisplayFormatter.formatTime(lastOpenedAt),
                ActuatorDisplayFormatter.formatDate(lastOpenedAt),
                ActuatorDisplayFormatter.formatTime(lastUpdated),
                ActuatorDisplayFormatter.formatDate(lastUpdated),
                trayIndex,
                trayCount
        );
    }
}