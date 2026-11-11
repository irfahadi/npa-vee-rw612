package com.nxp.example.smartgreenhouse.controllers;

import com.nxp.example.smartgreenhouse.models.sensor.*;
import com.nxp.example.smartgreenhouse.state.AppState;
import com.nxp.example.smartgreenhouse.models.sensor.SensorDataStore;
import com.nxp.example.smartgreenhouse.views.MainPage;
import com.nxp.example.smartgreenhouse.views.HorizontalSwipeListener;
import com.nxp.example.smartgreenhouse.views.menu.MenuContainer;
import com.nxp.example.smartgreenhouse.views.overview.FooterOverview;

import java.util.ArrayList;
import java.util.List;

public class OverviewController implements HorizontalSwipeListener, FooterOverview.onSwipeUpListener, MenuContainer.onSwipeDownListener {

    private final MainPage mainPage;
    private final AppState appState;
    private final SensorDataStore sensorDataStore;

    private final SensorDefinition[] sensorDefinitions;

    private int currentIndex;

    public OverviewController(MainPage mainPage, AppState appState, SensorDataStore sensorDataStore) {
        this.mainPage = mainPage;
        this.appState = appState;
        this.sensorDataStore = sensorDataStore;
        this.sensorDefinitions = SensorDefinitionProvider.getAll();
    }

    public void init() {
        mainPage.setOnOverviewSwipeListener(this);
        mainPage.setOnFooterSwipeListener(this);
        mainPage.setOnMenuContainerSwipeListener(this);
    }

    public void refresh() {
        updateDisplay();
    }

    public void setSensorNodes(SensorData[] nodes) {
        int selectedNodeId = getCurrentNodeId();

        this.sensorDataStore.replaceAll(nodes);

        if (this.sensorDataStore.getNodeCount() == 0) {
            clearSensorDisplay();
            return;
        }

        if (selectedNodeId >= 0) {
            int selectedIndex = this.sensorDataStore.findNodeIndexById(selectedNodeId);
            this.currentIndex = Math.max(selectedIndex, 0);
        } else {
            this.currentIndex = 0;
        }

        updateDisplay();
    }

    private void clearSensorDisplay() {
        this.currentIndex = 0;

        this.mainPage.updateTrayLabel("MENUNGGU DATA");
        this.mainPage.updateSensorCards(null);
        this.mainPage.updateTotalIndicator(0);
        this.mainPage.updateSelectedIndicator(0);
    }

    @Override
    public void onSwipeLeft() {
        int nodeCount = this.sensorDataStore.getNodeCount();

        int nextIndex = this.currentIndex + 1;

        if (nextIndex < nodeCount) {
            this.currentIndex = nextIndex;
            updateDisplay();
        }
    }

    @Override
    public void onSwipeRight() {
        int previousIndex = this.currentIndex - 1;

        if (previousIndex >= 0) {
            this.currentIndex = previousIndex;
            updateDisplay();
        }
    }

    @Override
    public void onSwipeUp() {
        this.mainPage.openMenu();
    }

    @Override
    public void onSwipeDown() {
        this.mainPage.closeMenu();
    }

    private void updateDisplay() {
        int nodeCount =
                this.sensorDataStore.getNodeCount();

        if (nodeCount == 0) {
            clearSensorDisplay();
            return;
        }

        if (this.currentIndex < 0) {
            this.currentIndex = 0;
        } else if (this.currentIndex >= nodeCount) {
            this.currentIndex = nodeCount - 1;
        }

        SensorData currentData = this.sensorDataStore.getNodeAt(this.currentIndex);

        if (currentData == null) {
            clearSensorDisplay();
            return;
        }

        this.appState.setSelectedNodeId(currentData.getNodeId());

        String trayText = "TRAY " + currentData.getNodeId();
        this.mainPage.updateTrayLabel(trayText);
        updateSensorData(currentData);
        this.mainPage.updateTotalIndicator(nodeCount);
        this.mainPage.updateSelectedIndicator(this.currentIndex);
    }

    public void updateSensorData(SensorData data) {
        List<SensorDisplayItem> items = new ArrayList<>();

        for (SensorDefinition def : this.sensorDefinitions) {
            if (def.isVisible()) {
                items.add(SensorDisplayBuilder.build(def, data));
            }
        }

        SensorDisplayItem[] result = new SensorDisplayItem[items.size()];
        items.toArray(result);

        mainPage.updateSensorCards(result);
    }

    private int getCurrentNodeId() {
        SensorData currentData = this.sensorDataStore.getNodeAt(this.currentIndex);

        if (currentData == null) {
            return -1;
        }

        return currentData.getNodeId();
    }
}