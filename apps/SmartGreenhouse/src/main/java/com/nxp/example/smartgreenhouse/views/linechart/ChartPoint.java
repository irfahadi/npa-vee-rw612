package com.nxp.example.smartgreenhouse.views.linechart;

public class ChartPoint {
    private final String name;
    private final String fullName;
    private final float value;

    private boolean selected;

    public ChartPoint(String name, String fullName, float value) {
        this.name = name;
        this.fullName = fullName;
        this.value = value;
        this.selected = false;
    }

    public String getName() {
        return this.name;
    }

    public String getFullName() {
        return this.fullName;
    }

    public float getValue() {
        return this.value;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
