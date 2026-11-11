package com.nxp.example.smartgreenhouse.models.actuator;

public final class ActuatorDataStore {

    private final PumpData pumpData;
    private final ValveData[] valveData;

    public ActuatorDataStore() {
        this.pumpData = new PumpData();

        this.valveData = new ValveData[] {new ValveData(1, 1), new ValveData(2, 2)};
    }

    public PumpData getPumpData() {
        return this.pumpData;
    }

    public void updatePumpState(boolean active, long timestamp) {
        this.pumpData.updateState(active, timestamp);
    }

    public int getValveCount() {
        int count = 0;

        for (ValveData valveDatum : this.valveData) {
            if (valveDatum != null) {
                count++;
            }
        }

        return count;
    }

    public ValveData getValveAt(int index) {
        if (index < 0 || index >= this.valveData.length) {
            return null;
        }

        return this.valveData[index];
    }

    public ValveData getValveById(int valveId) {
        int index = findValveIndexById(valveId);

        if (index < 0) {
            return null;
        }

        return this.valveData[index];
    }

    public ValveData getValveByTrayId(int trayId) {
        for (int i = 0; i < this.valveData.length; i++) {
            ValveData valve = this.valveData[i];

            if (valve != null && valve.getTrayId() == trayId) {
                return valve;
            }
        }

        return null;
    }

    public int findValveIndexById(int valveId) {
        for (int i = 0; i < this.valveData.length; i++) {
            ValveData valve = this.valveData[i];

            if (valve != null && valve.getValveId() == valveId) {
                return i;
            }
        }

        return -1;
    }

    public int findValveIndexByTrayId(int trayId) {
        for (int i = 0; i < this.valveData.length; i++) {
            ValveData valve = this.valveData[i];

            if (valve != null && valve.getTrayId() == trayId) {
                return i;
            }
        }

        return -1;
    }

    public boolean updateValveState(int valveId, boolean open, long timestamp) {
        ValveData valve = getValveById(valveId);

        if (valve == null) {
            return false;
        }

        valve.updateState(open, timestamp);

        return true;
    }

    public int getNextValveTrayId(int currentTrayId) {
        int smallestTrayId = Integer.MAX_VALUE;
        int nextTrayId = Integer.MAX_VALUE;

        for (ValveData valveData : this.valveData) {
            if (valveData == null) {
                continue;
            }

            int trayId = valveData.getTrayId();

            if (trayId < smallestTrayId) {
                smallestTrayId = trayId;
            }

            if (trayId > currentTrayId && trayId < nextTrayId) {
                nextTrayId = trayId;
            }
        }

        if (nextTrayId != Integer.MAX_VALUE) {
            return nextTrayId;
        }

        if (smallestTrayId != Integer.MAX_VALUE) {
            return smallestTrayId;
        }

        return ActuatorDisplayItem.NO_TRAY_ID;
    }

    public int getPreviousValveTrayId(int currentTrayId) {
        int largestTrayId = Integer.MIN_VALUE;
        int previousTrayId = Integer.MIN_VALUE;

        for (ValveData valveData : this.valveData) {
            if (valveData == null) {
                continue;
            }

            int trayId = valveData.getTrayId();

            if (trayId > largestTrayId) {
                largestTrayId = trayId;
            }

            if (trayId < currentTrayId && trayId > previousTrayId) {
                previousTrayId = trayId;
            }
        }

        if (previousTrayId != Integer.MIN_VALUE) {
            return previousTrayId;
        }

        if (largestTrayId != Integer.MIN_VALUE) {
            return largestTrayId;
        }

        return ActuatorDisplayItem.NO_TRAY_ID;
    }

    public int getValveIndexByTrayId(int trayId) {
        boolean trayFound = false;
        int trayIndex = 0;

        for (ValveData valveData : this.valveData) {
            if (valveData == null) {
                continue;
            }

            int currentTrayId = valveData.getTrayId();
            if (currentTrayId == trayId) {
                trayFound = true;
            } else if (currentTrayId < trayId) {
                trayIndex++;
            }
        }

        if (!trayFound) {
            return ActuatorDisplayItem.NO_TRAY_INDEX;
        }

        return trayIndex;
    }
}