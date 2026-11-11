package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorDataStore {

    private SensorData[] nodes;

    public SensorDataStore() {
        this.nodes = new SensorData[0];
    }

    public void replaceAll(SensorData[] source) {
        if (source == null || source.length == 0) {
            this.nodes = new SensorData[0];
            return;
        }

        int validCount = 0;

        for (SensorData data : source) {
            if (data != null) {
                validCount++;
            }
        }

        if (validCount == 0) {
            this.nodes = new SensorData[0];
            return;
        }

        SensorData[] validNodes = new SensorData[validCount];
        int destinationIndex = 0;

        for (SensorData data : source) {
            if (data != null) {
                validNodes[destinationIndex] = data;
                destinationIndex++;
            }
        }

        this.nodes = validNodes;
    }

    public void upsert(SensorData data) {
        if (data == null) {
            return;
        }

        int existingIndex = findNodeIndexById(data.getNodeId());

        if (existingIndex >= 0) {
            this.nodes[existingIndex] = data;
            return;
        }

        SensorData[] updatedNodes = new SensorData[this.nodes.length + 1];

        System.arraycopy(this.nodes, 0, updatedNodes, 0, this.nodes.length);

        updatedNodes[this.nodes.length] = data;

        this.nodes = updatedNodes;
    }

    public int getNodeCount() {
        return this.nodes.length;
    }

    public SensorData getNodeAt(int index) {
        if (index < 0 || index >= this.nodes.length) {
            return null;
        }

        return this.nodes[index];
    }

    public SensorData getNodeById(int nodeId) {
        int index = findNodeIndexById(nodeId);

        if (index < 0) {
            return null;
        }

        return this.nodes[index];
    }

    public int findNodeIndexById(int nodeId) {
        for (int i = 0; i < this.nodes.length; i++) {
            if (this.nodes[i].getNodeId() == nodeId) {
                return i;
            }
        }

        return -1;
    }

    public void clear() {
        this.nodes = new SensorData[0];
    }
}