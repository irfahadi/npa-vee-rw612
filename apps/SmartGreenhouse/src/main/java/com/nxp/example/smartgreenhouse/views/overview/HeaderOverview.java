package com.nxp.example.smartgreenhouse.views.overview;

import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Fonts;
import com.nxp.example.smartgreenhouse.style.Icons;
import com.nxp.example.smartgreenhouse.style.Images;
import ej.microui.display.Font;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.microui.event.Event;
import ej.microui.event.generator.Buttons;
import ej.microui.event.generator.Pointer;
import ej.mwt.Widget;
import ej.mwt.style.Style;
import ej.mwt.util.Alignment;
import ej.mwt.util.Size;

public class HeaderOverview extends Widget {

    public interface onWifiClickListener {
        void onClicked();
    }

    private onWifiClickListener listener;

    private final Image headerFrame;

    private final Image wifiConnectedIcon;
    private final Image wifiDisconnectedIcon;

    private boolean wifiConnected;

    private static final String APP = "SMART GREENHOUSE";
    private static final String ICON_TEXT = "WiFi";

    private String time = "--:--";

    private static final int titleX = 22;
    private static final int titleY = 6;
    private static final int offsetClockX = 10;

    private int wifiAreaX;
    private int wifiAreaY;
    private int wifiAreaWidth;
    private int wifiAreaHeight;

    public HeaderOverview() {
        this.headerFrame = Image.getImage(Images.HEADER_FRAME);
        this.wifiConnectedIcon = Image.getImage(Icons.WIFI_ICON_16);
        this.wifiDisconnectedIcon = Image.getImage(Icons.WIFI_SLASH_ICON_16);
        this.wifiConnected = false;
        setEnabled(true);
    }

    public int getHeaderHeight() {
        return this.headerFrame.getHeight();
    }

    public void setTime(String time) {
        String safeTime = time == null ? "--:--" : time;

        if (safeTime.equals(this.time)) {
            return;
        }

        this.time = safeTime;
        requestRender();
    }

    public void setWifiConnected(boolean connected) {
        if (this.wifiConnected == connected) {
            return;
        }

        this.wifiConnected = connected;

        requestRender();
    }

    public void setOnWifiClickListener(onWifiClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        size.setSize(this.headerFrame.getWidth(), this.headerFrame.getHeight());
    }

    @Override
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        Style style = getStyle();

        Font appFont = Fonts.jetbrainsMonoBold12px();
        Font textFont = Fonts.jetbrainsMonoBold10px();

        int imageX = Alignment.computeLeftX(this.headerFrame.getWidth(), 0, contentWidth, style.getHorizontalAlignment());
        int imageY = Alignment.computeTopY(this.headerFrame.getHeight(), 0, contentHeight, style.getVerticalAlignment());
        int rightX = contentWidth - 14;

        Image currentWifiIcon = this.wifiConnected ? this.wifiConnectedIcon : this.wifiDisconnectedIcon;
        int wifiIconColor = this.wifiConnected ? ApplicationColors.PRIMARY_COLOR : ApplicationColors.THIRD_COLOR;
        int wifiTextColor = this.wifiConnected ? ApplicationColors.SECONDARY_COLOR : ApplicationColors.THIRD_COLOR;

        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);

        Painter.drawImage(g, this.headerFrame, imageX, imageY);

        g.setColor(ApplicationColors.PRIMARY_COLOR);
        Painter.drawString(g, APP, appFont, titleX, titleY);

        int iconTitleWidth = textFont.stringWidth(ICON_TEXT);
        int startRightContent = contentWidth - (contentWidth * 22 / 100) - iconTitleWidth;

        int titleBaselineY = titleY + textFont.getBaselinePosition();
        int iconY = titleY + (textFont.getHeight() - currentWifiIcon.getHeight()) / 2 - 1;

        g.setColor(wifiIconColor);
        Painter.drawImage(g, currentWifiIcon, startRightContent, iconY);

        int iconWidth = currentWifiIcon.getWidth();
        int gap = iconWidth + 3;

        int iconTitleY = titleBaselineY - textFont.getBaselinePosition();

        g.setColor(wifiTextColor);
        Painter.drawString(g, ICON_TEXT, textFont, startRightContent + gap, iconTitleY);

        this.wifiAreaX = startRightContent;
        this.wifiAreaY = Math.min(iconY, iconTitleY);
        this.wifiAreaWidth = iconWidth + 3 + iconTitleWidth;

        int iconBottom = iconY + currentWifiIcon.getHeight();
        int textBottom = iconTitleY + textFont.getHeight();

        this.wifiAreaHeight = Math.max(iconBottom, textBottom) - this.wifiAreaY;

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        int clockWidth = textFont.stringWidth(this.time);

        rightX -= clockWidth + offsetClockX;

        int timeY = titleBaselineY - textFont.getBaselinePosition();

        Painter.drawString(g, this.time, textFont, rightX, timeY);
    }

    @Override
    public boolean handleEvent(int event) {
        if (Event.getType(event) != Pointer.EVENT_TYPE) {
            return super.handleEvent(event);
        }

        if (Buttons.isReleased(event)) {
            Pointer pointer = (Pointer) Event.getGenerator(event);
            int x = pointer.getX();
            int y = pointer.getY();

            if (isWifiClicked(x, y)) {
                if (this.listener != null) {
                    this.listener.onClicked();
                }
                return true;
            }
        }

        return super.handleEvent(event);
    }

    private boolean isWifiClicked(int x, int y) {
        return x >= this.wifiAreaX
                && x <= this.wifiAreaX + this.wifiAreaWidth
                && y >= this.wifiAreaY
                && y <= this.wifiAreaY + this.wifiAreaHeight;
    }
}