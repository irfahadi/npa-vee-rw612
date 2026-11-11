package com.nxp.example.smartgreenhouse.state;

import com.nxp.example.smartgreenhouse.models.menu.MenuItemData;

public final class AppState {

    private static final int NO_NODE_SELECTED = -1;

    private int selectedNodeId;
    private MenuItemData selectedMenuItem;

    public AppState() {
        this.selectedNodeId = NO_NODE_SELECTED;
        this.selectedMenuItem = null;
    }

    public int getSelectedNodeId() {
        return this.selectedNodeId;
    }

    public void setSelectedNodeId(int selectedNodeId) {
        this.selectedNodeId = selectedNodeId;
    }

    public boolean hasSelectedNode() {
        return this.selectedNodeId != NO_NODE_SELECTED;
    }

    public MenuItemData getSelectedMenuItem() {
        return this.selectedMenuItem;
    }

    public void setSelectedMenuItem(MenuItemData selectedMenuItem) {
        this.selectedMenuItem = selectedMenuItem;
    }

    public boolean hasSelectedMenuItem() {
        return this.selectedMenuItem != null;
    }

    public void clearSelectedMenuItem() {
        this.selectedMenuItem = null;
    }
}