package com.nxp.example.smartgreenhouse.views.overview;

import com.nxp.example.smartgreenhouse.models.sensor.SensorDisplayItem;
import com.nxp.example.smartgreenhouse.models.sensor.SensorStatus;
import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Fonts;
import com.nxp.example.smartgreenhouse.style.Images;

import ej.annotation.NonNullByDefault;
import ej.microui.display.Font;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.mwt.Widget;
import ej.mwt.util.Size;

public class SensorCard extends Widget {

    private final Image sensorCardFrame;
    private SensorDisplayItem displayItem;

    private static final int ICON_LEFT_PADDING = 4;
    private static final int TITLE_TOP_PADDING = 9;
    private static final int TITLE_TO_VALUE_GAP = 3;
    private static final int VALUE_TO_STATUS_GAP = 1;

    public SensorCard() {
        this.sensorCardFrame = Image.getImage(Images.CARD_FRAME);
    }

    public void setDisplayItem(SensorDisplayItem item) {
        this.displayItem = item;
        requestRender();
    }

    public SensorDisplayItem getDisplayItem() {
        return this.displayItem;
    }

    public int getStatusColor() {
        if (this.displayItem == null) return ApplicationColors.STATUS_OPTIMAL_COLOR;

        switch (this.displayItem.getSensorStatus()) {
            case WASPADA: return ApplicationColors.STATUS_WASPADA_COLOR;
            case BAHAYA: return ApplicationColors.STATUS_BAHAYA_COLOR;
            default: return ApplicationColors.STATUS_OPTIMAL_COLOR;
        }
    }

    public Image getStatusFrame() {
        if (this.displayItem == null) return Image.getImage(Images.OPTIMAL_ALERT_FRAME_L);

        switch (this.displayItem.getSensorStatus()) {
            case WASPADA: return Image.getImage(Images.WASPADA_ALERT_FRAME_L);
            case BAHAYA: return Image.getImage(Images.BAHAYA_ALERT_FRAME_L);
            default: return Image.getImage(Images.OPTIMAL_ALERT_FRAME_L);
        }
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        size.setSize(this.sensorCardFrame.getWidth(), this.sensorCardFrame.getHeight());
    }

    @Override
    @NonNullByDefault
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);

        if (this.displayItem == null) return;

        Font titleFont = Fonts.jetbrainsMonoRegular10px();
        Font valueFont = Fonts.jetbrainsMonoBold24px();
        Font unitFont = Fonts.jetbrainsMonoBold14px();
        Font statusFont = Fonts.jetbrainsMonoRegular9px();

        Image icon = this.displayItem.getDefinition().getIcon();
        String title = this.displayItem.getDefinition().getTitle();
        String unit = this.displayItem.getDefinition().getUnit();
        String valueText = this.displayItem.getFormattedValue();
        SensorStatus status = this.displayItem.getSensorStatus();

        int imageX = (contentWidth - this.sensorCardFrame.getWidth()) / 2;
        int imageY = (contentHeight - this.sensorCardFrame.getHeight()) / 2;
        int imageHeight = this.sensorCardFrame.getHeight();
        Painter.drawImage(g, this.sensorCardFrame, imageX, imageY);

        int iconHeight = icon.getHeight();
        int iconX = imageX + ICON_LEFT_PADDING;
        int iconY = imageY + (imageHeight - iconHeight) / 2;
        Painter.drawImage(g, icon, iconX, iconY);

        int titleX = iconX + icon.getWidth() + 2;
        int titleY = imageY + TITLE_TOP_PADDING;
        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, title, titleFont, titleX, titleY);

        int valueX = iconX + icon.getWidth() + 7;
        int valueY = titleY + titleFont.getHeight() + TITLE_TO_VALUE_GAP;
        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, valueText, valueFont, valueX, valueY);

        if (this.displayItem.isAvailable() && unit != null && !unit.isEmpty()) {
            int valueWidth = valueFont.stringWidth(valueText);
            int unitX = valueX + valueWidth;
            int valueBaselineY = valueY + valueFont.getBaselinePosition();
            int unitY = valueBaselineY - unitFont.getBaselinePosition();
            g.setColor(ApplicationColors.SECONDARY_COLOR);
            Painter.drawString(g, unit, unitFont, unitX, unitY);
        }

        Image statusFrame = getStatusFrame();
        int statusWidth = statusFrame.getWidth();
        int statusHeight = statusFrame.getHeight();
        int statusX = iconX + icon.getWidth() + 7;
        int statusY = valueY + valueFont.getHeight() + VALUE_TO_STATUS_GAP;
        String statusText = this.displayItem.isAvailable() ? status.name() : "-";
        int statusTextWidth = statusFont.stringWidth(statusText);
        int statusTextX = statusX + ((statusWidth - statusTextWidth) / 2);
        int statusTextY = statusY + ((statusHeight - statusFont.getHeight()) / 2) + 1;

        if (this.displayItem.isAvailable()) Painter.drawImage(g, statusFrame, statusX, statusY);
        g.setColor(this.displayItem.isAvailable() ? getStatusColor() : ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, statusText, statusFont, statusTextX, statusTextY);
    }
}