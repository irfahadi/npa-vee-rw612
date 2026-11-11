package com.nxp.example.smartgreenhouse.models.actuator;

public final class SampleActuatorData {

    private static final long MINUTE_MILLIS = 60_000L;

    private SampleActuatorData() {}

    public static void load(ActuatorDataStore actuatorDataStore) {
        long currentTimestamp = System.currentTimeMillis();

        actuatorDataStore.updatePumpState(true, currentTimestamp - (15L * MINUTE_MILLIS));
        actuatorDataStore.updatePumpState(true, currentTimestamp);

        actuatorDataStore.updateValveState(1, true, currentTimestamp - (30L * MINUTE_MILLIS));
        actuatorDataStore.updateValveState(1, false, currentTimestamp - (20L * MINUTE_MILLIS));

        actuatorDataStore.updateValveState(1, false, currentTimestamp);
        actuatorDataStore.updateValveState(2, true, currentTimestamp - (8L * MINUTE_MILLIS));
        actuatorDataStore.updateValveState(2, true, currentTimestamp);
    }
}