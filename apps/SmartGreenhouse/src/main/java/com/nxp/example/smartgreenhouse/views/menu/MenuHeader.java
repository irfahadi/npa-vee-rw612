package com.nxp.example.smartgreenhouse.views.menu;

import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Fonts;
import com.nxp.example.smartgreenhouse.style.Icons;
import ej.microui.display.Font;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;

public class MenuHeader {
    private final Image icon;
    private final String title;

    private static final int HEADER_HEIGHT = 45;
    private static final int ICON_TITLE_GAP = 2;
    private static final int HEADER_OFFSET_Y = 1;

    public MenuHeader() {
        this.icon = Image.getImage(Icons.SWIPE_UP_ICON_24);
        this.title = "MENU";
    }

    public int getMenuHeaderHeight() {
        return HEADER_HEIGHT;
    }

    public void render(GraphicsContext g, int contentWidth) {
        Font titleFont = Fonts.jetbrainsMonoBold10px();

        int iconX = (contentWidth - this.icon.getWidth()) / 2;
        int iconY = ((HEADER_HEIGHT - this.icon.getHeight()) / 2) + HEADER_OFFSET_Y;
        Painter.drawImage(g, this.icon, iconX, iconY);

        int titleX = (contentWidth - titleFont.stringWidth(this.title)) / 2;
        int titleY = ((HEADER_HEIGHT - titleFont.getHeight())) - ICON_TITLE_GAP;
        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, this.title, titleFont, titleX, titleY);
    }
}
