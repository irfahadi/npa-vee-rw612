package com.nxp.example.smartgreenhouse.views;

import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Fonts;
import ej.microui.display.Font;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Painter;
import ej.mwt.Widget;
import ej.mwt.util.Size;

public class TrayLabel extends Widget {

    private String text;
    private final Font font;

    public TrayLabel() {
        this.text = "";
        this.font = Fonts.jetbrainsMonoBold12px();
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
        requestRender();
    }

    public int getTrayLabelHeight() {
        return this.font.getHeight();
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        int width = this.font.stringWidth(this.text);
        int height = this.font.getHeight();

        size.setSize(width, height);
    }

    @Override
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);

        if (this.text != null && !this.text.isEmpty()) {
            int textX = (contentWidth - this.font.stringWidth(this.text)) / 2;
            int textY = (contentHeight - this.font.getHeight()) / 2;

            g.setColor(ApplicationColors.PRIMARY_COLOR);
            Painter.drawString(g, this.text, this.font, textX, textY);
        }
    }
}
