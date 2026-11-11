package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorHistoryStore {

    public static final int DEFAULT_MAX_POINTS_PER_NODE = 8;

    private final int maxPointsPerNode;
    private NodeHistory[] nodeHistories;

    public SensorHistoryStore() {
        this(DEFAULT_MAX_POINTS_PER_NODE);
    }

    public SensorHistoryStore(int maxPointsPerNode) {
        if (maxPointsPerNode <= 0) maxPointsPerNode = DEFAULT_MAX_POINTS_PER_NODE;

        this.maxPointsPerNode = maxPointsPerNode;
        this.nodeHistories = new NodeHistory[0];
    }

    public void add(SensorHistoryEntry entry) {
        if (entry == null || entry.getNodeId() < 0) return;

        NodeHistory nodeHistory = findNodeHistory(entry.getNodeId());

        if (nodeHistory == null) nodeHistory = createNodeHistory(entry.getNodeId());

        nodeHistory.add(entry);
    }

    public void addAll(SensorHistoryEntry[] entries) {
        if (entries == null) return;

        for (SensorHistoryEntry entry : entries) add(entry);
    }

    public SensorHistoryEntry[] getByNodeId(int nodeId) {
        NodeHistory nodeHistory = findNodeHistory(nodeId);
        if (nodeHistory == null) return new SensorHistoryEntry[0];
        return nodeHistory.toArray();
    }

    public void clear() {
        this.nodeHistories = new NodeHistory[0];
    }

    private NodeHistory findNodeHistory(int nodeId) {
        for (NodeHistory nodeHistory : this.nodeHistories) {
            if (nodeHistory.nodeId == nodeId) return nodeHistory;
        }

        return null;
    }

    private NodeHistory createNodeHistory(int nodeId) {
        NodeHistory nodeHistory = new NodeHistory(nodeId, this.maxPointsPerNode);
        NodeHistory[] updated = new NodeHistory[this.nodeHistories.length + 1];

        System.arraycopy(this.nodeHistories, 0, updated, 0, this.nodeHistories.length);

        updated[this.nodeHistories.length] = nodeHistory;
        this.nodeHistories = updated;

        return nodeHistory;
    }

    private static final class NodeHistory {

        private final int nodeId;
        private final SensorHistoryEntry[] buffer;

        private int startIndex;
        private int size;

        private NodeHistory(int nodeId, int capacity) {
            this.nodeId = nodeId;
            this.buffer = new SensorHistoryEntry[capacity];
            this.startIndex = 0;
            this.size = 0;
        }

        private void add(SensorHistoryEntry entry) {
            if (this.size < this.buffer.length) {
                int destinationIndex = (this.startIndex + this.size) % this.buffer.length;
                this.buffer[destinationIndex] = entry;
                this.size++;
                return;
            }

            this.buffer[this.startIndex] = entry;
            this.startIndex = (this.startIndex + 1) % this.buffer.length;
        }

        private SensorHistoryEntry[] toArray() {
            SensorHistoryEntry[] result = new SensorHistoryEntry[this.size];

            for (int i = 0; i < this.size; i++) {
                int sourceIndex = (this.startIndex + i) % this.buffer.length;
                result[i] = this.buffer[sourceIndex];
            }

            return result;
        }
    }
}