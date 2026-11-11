package com.nxp.example.smartgreenhouse.controllers;

import com.nxp.example.smartgreenhouse.models.menu.MenuItemData;
import com.nxp.example.smartgreenhouse.models.sensor.*;
import com.nxp.example.smartgreenhouse.state.AppState;
import com.nxp.example.smartgreenhouse.views.HorizontalSwipeListener;
import com.nxp.example.smartgreenhouse.views.MainPage;
import com.nxp.example.smartgreenhouse.views.detail.SensorDetail;
import com.nxp.example.smartgreenhouse.views.linechart.ChartPoint;

public class SensorDetailController implements SensorDetail.onBackListener, HorizontalSwipeListener {

    private final MainPage mainPage;
    private final AppState appState;
    private final SensorDataStore sensorDataStore;
    private final SensorHistoryStore sensorHistoryStore;

    private int currentNodeIndex;
    private int selectedSensorId;

    public SensorDetailController(MainPage mainPage, AppState appState, SensorDataStore sensorDataStore, SensorHistoryStore sensorHistoryStore) {
        this.mainPage = mainPage;
        this.appState = appState;
        this.sensorDataStore = sensorDataStore;
        this.sensorHistoryStore = sensorHistoryStore;

        this.currentNodeIndex = -1;
        this.selectedSensorId = -1;
    }

    public void init() {
        this.mainPage.setOnSensorDetailBackListener(this);
        this.mainPage.setOnSensorDetailSwipeListener(this);
    }

    public void refreshIfOpen() {
        if (!this.mainPage.isSensorDetailOpen()) {
            return;
        }

        int selectedNodeId = this.appState.getSelectedNodeId();

        int selectedNodeIndex = this.sensorDataStore.findNodeIndexById(selectedNodeId);

        if (selectedNodeIndex < 0) {
            return;
        }

        this.currentNodeIndex = selectedNodeIndex;

        refreshSelectedSensor();
    }

    public void openSelectedSensor() {
        MenuItemData selectedMenuItem = this.appState.getSelectedMenuItem();

        if (selectedMenuItem == null || !selectedMenuItem.isSensor()) {
            return;
        }

        int nodeId = this.appState.getSelectedNodeId();

        if (nodeId < 0) {
            this.mainPage.clearSensorDetail();
            return;
        }

        this.selectedSensorId = selectedMenuItem.getSensorId();
        this.currentNodeIndex = this.sensorDataStore.findNodeIndexById(nodeId);

        if (this.currentNodeIndex < 0) {
            this.mainPage.clearSensorDetail();
            return;
        }

        refreshSelectedSensor();
        this.mainPage.openSensorDetail();
    }

    private void refreshSelectedSensor() {
        if (this.currentNodeIndex < 0 || this.selectedSensorId < 0) {
            this.mainPage.clearSensorDetail();
            return;
        }

        SensorData currentSensorData = this.sensorDataStore.getNodeAt(this.currentNodeIndex);

        if (currentSensorData == null) {
            this.mainPage.clearSensorDetail();
            return;
        }

        int nodeId = currentSensorData.getNodeId();
        int sensorId = this.selectedSensorId;

        SensorDefinition definition = SensorDefinitionProvider.getById(sensorId);

        if (definition == null) {
            this.mainPage.clearSensorDetail();
            return;
        }

        SensorDisplayItem currentDisplayItem = SensorDisplayBuilder.build(definition, currentSensorData);

        if (currentDisplayItem == null) {
            this.mainPage.clearSensorDetail();
            return;
        }

        double gaugeMinimum = SensorGaugeRangeProvider.getMinimum(sensorId);
        double gaugeMaximum = SensorGaugeRangeProvider.getMaximum(sensorId);

        SensorHistoryEntry[] historyEntries = this.sensorHistoryStore.getByNodeId(nodeId);

        ChartPoint[] historyPoints = buildChartPoints(historyEntries, definition);

        SensorHistorySummary historySummary = SensorHistorySummaryCalculate.calculate(historyEntries, definition);

        SensorThreshold sensorThreshold = SensorThresholdProvider.getBySensorId(sensorId);

        if (sensorThreshold == null) {
            this.mainPage.clearSensorDetail();
            return;
        }

        String title = definition.getTitle() + " - TRAY " + nodeId;

        int nodeCount = this.sensorDataStore.getNodeCount();
        this.mainPage.updateSensorDetail(title, currentDisplayItem, gaugeMinimum, gaugeMaximum, historyPoints, historySummary, sensorThreshold, nodeCount, this.currentNodeIndex);
    }

    private ChartPoint[] buildChartPoints(SensorHistoryEntry[] historyEntries, SensorDefinition definition) {
        if (historyEntries == null || historyEntries.length == 0 || definition == null) {
            return new ChartPoint[0];
        }

        ChartPoint[] chartPoints = new ChartPoint[historyEntries.length];

        for (int i = 0; i < historyEntries.length; i++) {
            SensorHistoryEntry historyEntry = historyEntries[i];

            if (historyEntry == null || historyEntry.getSensorData() == null) {
                chartPoints[i] = new ChartPoint("", "", -1);
                continue;
            }

            SensorDisplayItem historyDisplayItem = SensorDisplayBuilder.build(definition, historyEntry.getSensorData());

            if (historyDisplayItem == null) {
                chartPoints[i] = new ChartPoint(historyEntry.getShortTime(), historyEntry.getFullTime(), -1);
                continue;
            }

            chartPoints[i] = new ChartPoint(historyEntry.getShortTime(), historyEntry.getFullTime(), historyDisplayItem.getValue());
        }

        return chartPoints;
    }

    @Override
    public void onBack() {
        this.mainPage.closeSensorDetail();
    }

    @Override
    public void onSwipeLeft() {
        moveToNode(this.currentNodeIndex + 1);
    }

    @Override
    public void onSwipeRight() {
        moveToNode(this.currentNodeIndex - 1);
    }

    private void moveToNode(int targetNodeIndex) {
        int nodeCount = this.sensorDataStore.getNodeCount();

        if (targetNodeIndex < 0 || targetNodeIndex >= nodeCount) {
            return;
        }

        if (targetNodeIndex == this.currentNodeIndex) {
            return;
        }

        SensorData targetNode = this.sensorDataStore.getNodeAt(targetNodeIndex);

        if (targetNode == null) {
            return;
        }

        this.currentNodeIndex = targetNodeIndex;

        this.appState.setSelectedNodeId(targetNode.getNodeId());

        refreshSelectedSensor();
    }
}
