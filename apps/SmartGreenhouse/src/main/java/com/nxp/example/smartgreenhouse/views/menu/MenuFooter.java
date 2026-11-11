package com.nxp.example.smartgreenhouse.views.menu;

import com.nxp.example.smartgreenhouse.style.Images;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;

public class MenuFooter {

    private final Image dotActive;
    private final Image dotInactive;

    private static final int FOOTER_HEIGHT = 19;
    private static final int DOT_GAP = 5;

    private int currentPage;
    private int pageCount;

    public MenuFooter() {
        this.dotActive = Image.getImage(Images.DOT_ACTIVE);
        this.dotInactive = Image.getImage(Images.DOT_INACTIVE);
        this.currentPage = 0;
        this.pageCount = 1;
    }

    public int getFooterHeight() {
        return FOOTER_HEIGHT;
    }

    public void setPageCount(int pageCount) {
        if (pageCount <= 0) {
            return;
        }
        this.pageCount = pageCount;
    }

    public void setCurrentPage(int currentPage) {
        if (currentPage < 0 || currentPage >= this.pageCount) {
            return;
        }
        this.currentPage = currentPage;
    }

    public void render(GraphicsContext g, int contentWidth, int contentHeight) {
        int totalWidth = computeDotsTotalWidth();
        int x = (contentWidth - totalWidth) / 2;
        int footerY = contentHeight - FOOTER_HEIGHT;

        for (int i = 0; i < this.pageCount; i++) {
            Image dot = (i == this.currentPage) ? this.dotActive : this.dotInactive;

            int dotY = footerY + ((FOOTER_HEIGHT - dot.getHeight()) / 2);
            Painter.drawImage(g, dot, x, dotY);

            x += dot.getWidth() + DOT_GAP;
        }
    }

    private int computeDotsTotalWidth() {
        int totalWidth = 0;

        for (int i = 0; i < this.pageCount; i++) {
            Image dot = (i == this.currentPage) ? this.dotActive : this.dotInactive;
            totalWidth += dot.getWidth();

            if (i < this.pageCount - 1) {
                totalWidth += DOT_GAP;
            }
        }
        return totalWidth;
    }
}