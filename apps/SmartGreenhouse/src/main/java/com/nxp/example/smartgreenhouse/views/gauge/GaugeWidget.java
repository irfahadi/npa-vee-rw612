package com.nxp.example.smartgreenhouse.views.gauge;

import com.nxp.example.smartgreenhouse.models.sensor.SensorDisplayItem;
import com.nxp.example.smartgreenhouse.models.sensor.SensorId;
import com.nxp.example.smartgreenhouse.models.sensor.SensorStatus;
import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Fonts;
import com.nxp.example.smartgreenhouse.style.Images;

import ej.annotation.NonNullByDefault;
import ej.drawing.ShapePainter;
import ej.drawing.ShapePainter.Cap;
import ej.microui.display.Font;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.mwt.Widget;
import ej.mwt.util.Size;

public class GaugeWidget extends Widget {

    private static final int WIDGET_WIDTH = 114;
    private static final int WIDGET_HEIGHT = 114;

    private static final int ARC_X = 8;
    private static final int ARC_Y = 8;
    private static final int ARC_DIAMETER = 98;

    private static final int ARC_START = 225;
    private static final int ARC_ANGLE = -270;

    private static final int ARC_THICKNESS = 8;
    private static final int ARC_FADE = 1;

    private static final int GRADIENT_SEGMENT_COUNT = 12;

    private static final int ICON_TOP = 25;
    private static final int VALUE_CENTER_Y = 68;
    private static final int UNIT_GAP = 2;

    private static final int STATUS_TOP = 93;
    private static final int STATUS_TEXT_OFFSET_X = 0;
    private static final int STATUS_TEXT_OFFSET_Y = 1;

    private SensorDisplayItem item;

    private double minValue;
    private double maxValue;

    public GaugeWidget() {
        this.item = null;
        this.minValue = 0;
        this.maxValue = 100;
    }

    public int getGaugeWidth() {
        return WIDGET_WIDTH;
    }

    public int getGaugeHeight() {
        return WIDGET_HEIGHT;
    }

    public void setData(SensorDisplayItem item, double minValue, double maxValue) {
        this.item = item;
        this.minValue = minValue;
        this.maxValue = maxValue;
        requestRender();
    }

    public void clearData() {
        this.item = null;
        requestRender();
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        size.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
    }

    @Override
    @NonNullByDefault
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);

        if (this.item == null) return;

        int offsetX = (contentWidth - WIDGET_WIDTH) / 2;
        int offsetY = (contentHeight - WIDGET_HEIGHT) / 2;

        drawGaugeBackground(g, offsetX, offsetY);
        drawGaugeProgress(g, offsetX, offsetY);
        drawSensorIcon(g, offsetX, offsetY);
        drawSensorValue(g, offsetX, offsetY);
        drawSensorStatus(g, offsetX, offsetY);
    }

    private void drawGaugeBackground(GraphicsContext g, int offsetX, int offsetY) {
        g.setColor(ApplicationColors.ARC_BACKGROUND_COLOR);
        ShapePainter.drawThickFadedCircleArc(g, offsetX + ARC_X, offsetY + ARC_Y, ARC_DIAMETER, ARC_START, ARC_ANGLE, ARC_THICKNESS, ARC_FADE, Cap.ROUNDED, Cap.ROUNDED);
    }

    private void drawGaugeProgress(GraphicsContext g, int offsetX, int offsetY) {
        double progress = computeProgress();
        if (progress <= 0) return;

        int totalProgressAngle = (int) Math.round(ARC_ANGLE * progress);
        if (totalProgressAngle == 0) return;

        int segmentCount = GRADIENT_SEGMENT_COUNT;
        int absoluteAngle = abs(totalProgressAngle);

        if (absoluteAngle < segmentCount) segmentCount = absoluteAngle;
        if (segmentCount <= 0) return;

        int drawnAngle = 0;

        for (int i = 0; i < segmentCount; i++) {
            int remainingSegments = segmentCount - i;
            int remainingAngle = totalProgressAngle - drawnAngle;
            int segmentAngle = remainingAngle / remainingSegments;

            if (segmentAngle == 0) segmentAngle = totalProgressAngle < 0 ? -1 : 1;

            double ratio;

            if (segmentCount == 1) {
                ratio = 1.0;
            } else {
                ratio = (double) i / (double) (segmentCount - 1);
            }

            int color = interpolateColor(ratio);
            int segmentStart = ARC_START + drawnAngle;
            Cap startCap = i == 0 ? Cap.ROUNDED : Cap.NONE;
            Cap endCap = i == segmentCount - 1 ? Cap.ROUNDED : Cap.NONE;

            g.setColor(color);
            ShapePainter.drawThickFadedCircleArc(g, offsetX + ARC_X, offsetY + ARC_Y, ARC_DIAMETER, segmentStart, segmentAngle, ARC_THICKNESS, ARC_FADE, startCap, endCap);
            drawnAngle += segmentAngle;
        }
    }

    private double computeProgress() {
        if (this.item == null || !this.item.isAvailable()) return 0;
        if (this.maxValue <= this.minValue) return 0;

        double value = this.item.getValue();
        double progress = (value - this.minValue) / (this.maxValue - this.minValue);

        if (progress < 0) return 0;
        if (progress > 1) return 1;

        return progress;
    }

    private int interpolateColor(double ratio) {
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;

        int startR = (ApplicationColors.ARC_PROGRESS_START_COLOR >> 16) & 0xFF;
        int startG = (ApplicationColors.ARC_PROGRESS_START_COLOR >> 8) & 0xFF;
        int startB = ApplicationColors.ARC_PROGRESS_START_COLOR & 0xFF;

        int endR = (ApplicationColors.ARC_PROGRESS_END_COLOR >> 16) & 0xFF;
        int endG = (ApplicationColors.ARC_PROGRESS_END_COLOR >> 8) & 0xFF;
        int endB = ApplicationColors.ARC_PROGRESS_END_COLOR & 0xFF;

        int resultR = startR + (int) Math.round((endR - startR) * ratio);
        int resultG = startG + (int) Math.round((endG - startG) * ratio);
        int resultB = startB + (int) Math.round((endB - startB) * ratio);

        return (resultR << 16) | (resultG << 8) | resultB;
    }

    private void drawSensorIcon(GraphicsContext g, int offsetX, int offsetY) {
        Image icon = this.item.getDefinition().getIcon();
        int iconX = offsetX + ((WIDGET_WIDTH - icon.getWidth()) / 2);
        int iconY = offsetY + ICON_TOP;
        Painter.drawImage(g, icon, iconX, iconY);
    }

    private void drawSensorValue(GraphicsContext g, int offsetX, int offsetY) {
        int sensorId = this.item.getDefinition().getSensorId();
        Font valueFont = getValueFont(sensorId);
        Font unitFont = Fonts.jetbrainsMonoBold12px();

        String valueText = this.item.getFormattedValue();
        String unit = this.item.getDefinition().getUnit();
        int valueWidth = valueFont.stringWidth(valueText);
        int unitWidth = 0;

        if (this.item.isAvailable() && unit != null && !unit.isEmpty()) unitWidth = UNIT_GAP + unitFont.stringWidth(unit);

        int totalWidth = valueWidth + unitWidth;
        int valueX = offsetX + ((WIDGET_WIDTH - totalWidth) / 2);
        int valueY = offsetY + VALUE_CENTER_Y - (valueFont.getHeight() / 2) + 7;

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, valueText, valueFont, valueX, valueY);

        if (this.item.isAvailable() && unit != null && !unit.isEmpty()) {
            int unitX = valueX + valueWidth + UNIT_GAP;
            int valueBaselineY = valueY + valueFont.getBaselinePosition();
            int unitY = valueBaselineY - unitFont.getBaselinePosition();
            Painter.drawString(g, unit, unitFont, unitX, unitY);
        }
    }

    private Font getValueFont(int sensorId) {
        if (sensorId == SensorId.INTENSITAS_CAHAYA) return Fonts.jetbrainsMonoBold12px();
        return Fonts.jetbrainsMonoBold16px();
    }

    private void drawSensorStatus(GraphicsContext g, int offsetX, int offsetY) {
        SensorStatus sensorStatus = this.item.getSensorStatus();
        Image statusImage = getStatusImage(sensorStatus);
        Font statusFont = Fonts.jetbrainsMonoRegular9px();
        String statusText = this.item.isAvailable() ? sensorStatus.name() : "-";

        int arcCenterX = offsetX + ARC_X + ((ARC_DIAMETER + 1) / 2);
        int statusX = arcCenterX - (statusImage.getWidth() / 2);
        int statusY = offsetY + STATUS_TOP;
        int statusTextWidth = statusFont.stringWidth(statusText);
        int statusTextX = statusX + ((statusImage.getWidth() - statusTextWidth) / 2) + STATUS_TEXT_OFFSET_X;
        int statusTextY = statusY + ((statusImage.getHeight() - statusFont.getHeight()) / 2) + STATUS_TEXT_OFFSET_Y;

        if (this.item.isAvailable()) Painter.drawImage(g, statusImage, statusX, statusY);

        g.setColor(this.item.isAvailable() ? getStatusColor(sensorStatus) : ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, statusText, statusFont, statusTextX, statusTextY);
    }

    private Image getStatusImage(SensorStatus sensorStatus) {
        switch (sensorStatus) {
            case WASPADA:
                return Image.getImage(Images.WASPADA_ALERT_FRAME_L);
            case BAHAYA:
                return Image.getImage(Images.BAHAYA_ALERT_FRAME_L);
            case OPTIMAL:
            default:
                return Image.getImage(Images.OPTIMAL_ALERT_FRAME_L);
        }
    }

    private int getStatusColor(SensorStatus sensorStatus) {
        switch (sensorStatus) {
            case WASPADA:
                return ApplicationColors.STATUS_WASPADA_COLOR;
            case BAHAYA:
                return ApplicationColors.STATUS_BAHAYA_COLOR;
            case OPTIMAL:
            default:
                return ApplicationColors.STATUS_OPTIMAL_COLOR;
        }
    }

    private int abs(int value) {
        return value < 0 ? -value : value;
    }
}