package com.nxp.example.smartgreenhouse.controllers;

import com.nxp.example.smartgreenhouse.models.actuator.*;
import com.nxp.example.smartgreenhouse.models.menu.MenuItemData;
import com.nxp.example.smartgreenhouse.services.mqtt.MqttSubscribeService;
import com.nxp.example.smartgreenhouse.state.AppState;
import com.nxp.example.smartgreenhouse.views.HorizontalSwipeListener;
import com.nxp.example.smartgreenhouse.views.MainPage;
import com.nxp.example.smartgreenhouse.views.actuator.ActuatorToggleListener;
import com.nxp.example.smartgreenhouse.views.detail.ActuatorDetail;

import ej.bon.Util;

public class ActuatorDetailController implements ActuatorDetail.onBackListener, ActuatorToggleListener, HorizontalSwipeListener {

    private final MainPage mainPage;
    private final AppState appState;
    private final ActuatorDataStore actuatorDataStore;
    private MqttSubscribeService mqttSubscribeService;

    private ActuatorDisplayItem currentDisplayItem;

    private static final int MAX_VALVE_ID = 2;
    private final boolean[] valveControlPending = new boolean[MAX_VALVE_ID + 1];
    private final boolean[] pendingTargetState = new boolean[MAX_VALVE_ID + 1];
    private final boolean[] rollbackAvailable = new boolean[MAX_VALVE_ID + 1];
    private final boolean[] rollbackOpen = new boolean[MAX_VALVE_ID + 1];
    private final long[] rollbackLastOpenedAt = new long[MAX_VALVE_ID + 1];
    private final long[] rollbackLastUpdated = new long[MAX_VALVE_ID + 1];

    public ActuatorDetailController(MainPage mainPage, AppState appState, ActuatorDataStore actuatorDataStore) {
        this.mainPage = mainPage;
        this.appState = appState;
        this.actuatorDataStore = actuatorDataStore;
        this.currentDisplayItem = null;
        this.mqttSubscribeService = null;
    }

    public void init() {
        this.mainPage.setOnActuatorDetailBackListener(this);
        this.mainPage.setOnActuatorToggleRequestedListener(this);
        this.mainPage.setOnActuatorDetailSwipeListener(this);
    }

    public void setMqttSubscribeService(MqttSubscribeService mqttSubscribeService) {
        this.mqttSubscribeService = mqttSubscribeService;
    }

    public void openSelectedActuator() {
        boolean actuatorAvailable = refreshSelectedActuator();

        if (!actuatorAvailable) {
            return;
        }

        this.mainPage.openActuatorDetail();
    }

    private boolean refreshSelectedActuator() {
        MenuItemData selectedMenuItem = this.appState.getSelectedMenuItem();

        if (selectedMenuItem == null || selectedMenuItem.isSensor()) {
            return false;
        }

        int actuatorId = selectedMenuItem.getActuatorId();
        long currentTimestamp = Util.currentTimeMillis();

        String detailTitle;
        ActuatorDisplayItem displayItem;

        if (actuatorId == ActuatorId.POMPA_AIR) {
            detailTitle = "POMPA AIR";
            displayItem = ActuatorDisplayBuilder.buildPump(this.actuatorDataStore.getPumpData(), currentTimestamp);
        } else if (actuatorId == ActuatorId.KATUP_AIR) {
            int trayId = this.appState.getSelectedNodeId();
            ValveData valveData = this.actuatorDataStore.getValveByTrayId(trayId);

            if (valveData == null) {
                return false;
            }

            detailTitle = "KATUP AIR - TRAY " + trayId;

            int trayCount = this.actuatorDataStore.getValveCount();
            int trayIndex = this.actuatorDataStore.getValveIndexByTrayId(trayId);
            displayItem = ActuatorDisplayBuilder.buildValve(valveData, trayIndex, trayCount);
        } else {
            return false;
        }

        if (displayItem == null) {
            return false;
        }

        this.currentDisplayItem = displayItem;
        this.mainPage.updateActuatorDetail(detailTitle, displayItem);

        return true;
    }

    public void refreshIfOpen() {
        if (!this.mainPage.isActuatorDetailOpen()) {
            return;
        }
        refreshSelectedActuator();
    }

    @Override
    public void onBack() {
        this.currentDisplayItem = null;
        this.mainPage.closeActuatorDetail();
    }

    @Override
    public void onToggleRequested(boolean targetState) {
        if (this.currentDisplayItem == null || !this.currentDisplayItem.isValve()) {
            return;
        }

        int trayId = this.currentDisplayItem.getTrayId();
        ValveData valveData = this.actuatorDataStore.getValveByTrayId(trayId);

        if (valveData == null) {
            refreshSelectedActuator();
            return;
        }

        int valveId = valveData.getValveId();

        if (valveId < 1 || valveId > MAX_VALVE_ID) {
            refreshSelectedActuator();
            return;
        }

        if (this.mqttSubscribeService == null) {
            refreshSelectedActuator();
            return;
        }

        if (this.valveControlPending[valveId]) {
            refreshSelectedActuator();
            return;
        }

        this.rollbackAvailable[valveId] = valveData.isAvailable();
        this.rollbackOpen[valveId] = valveData.isOpen();
        this.rollbackLastOpenedAt[valveId] = valveData.getLastOpenedAt();
        this.rollbackLastUpdated[valveId] = valveData.getLastUpdated();
        this.pendingTargetState[valveId] = targetState;
        this.valveControlPending[valveId] = true;

        valveData.updateState(targetState, Util.currentTimeMillis());
        refreshSelectedActuator();

        boolean queued = this.mqttSubscribeService.requestValveControl(valveId, targetState);
        if (!queued) {
            onValveControlPublishFailed(valveId, targetState);
        }
    }

    public void onValveControlPublished(int valveId, boolean targetState) {
        if (valveId < 1 || valveId > MAX_VALVE_ID) {
            return;
        }

        if (!this.valveControlPending[valveId]) {
            return;
        }

        if (this.pendingTargetState[valveId] != targetState) {
            return;
        }

        this.valveControlPending[valveId] = false;
    }

    public void onValveControlPublishFailed(int valveId, boolean targetState) {
        if (valveId < 1 || valveId > MAX_VALVE_ID) {
            return;
        }

        if (!this.valveControlPending[valveId]) {
            return;
        }

        if (this.pendingTargetState[valveId] != targetState) {
            return;
        }

        ValveData valveData = this.actuatorDataStore.getValveByTrayId(valveId);
        if (valveData != null) {
            valveData.restoreState(
                    this.rollbackAvailable[valveId],
                    this.rollbackOpen[valveId],
                    this.rollbackLastOpenedAt[valveId],
                    this.rollbackLastUpdated[valveId]
            );
        }

        this.valveControlPending[valveId] = false;
        refreshIfOpen();
    }

    public void onValveStatusApplied(int valveId, boolean open) {
        if (valveId < 1 || valveId > MAX_VALVE_ID) {
            return;
        }

        this.valveControlPending[valveId] = false;
    }

    @Override
    public void onSwipeLeft() {
        moveToNextTray();
    }

    @Override
    public void onSwipeRight() {
        moveToPreviousTray();
    }

    private void moveToNextTray() {
        if (this.currentDisplayItem == null || !this.currentDisplayItem.isValve()) {
            return;
        }

        int currentTrayId = this.currentDisplayItem.getTrayId();
        int targetTrayId = this.actuatorDataStore.getNextValveTrayId(currentTrayId);

        updateSelectedTray(currentTrayId, targetTrayId);
    }

    private void moveToPreviousTray() {
        if (this.currentDisplayItem == null || !this.currentDisplayItem.isValve()) {
            return;
        }

        int currentTrayId = this.currentDisplayItem.getTrayId();
        int targetTrayId = this.actuatorDataStore.getPreviousValveTrayId(currentTrayId);

        updateSelectedTray(currentTrayId, targetTrayId);
    }

    private void updateSelectedTray(int currentTrayId, int targetTrayId) {
        if (targetTrayId == ActuatorDisplayItem.NO_TRAY_ID) {
            return;
        }

        if (targetTrayId == currentTrayId) {
            return;
        }

        ValveData targetValve = this.actuatorDataStore.getValveByTrayId(targetTrayId);

        if (targetValve == null) {
            return;
        }

        this.appState.setSelectedNodeId(targetTrayId);

        refreshSelectedActuator();
    }
}
