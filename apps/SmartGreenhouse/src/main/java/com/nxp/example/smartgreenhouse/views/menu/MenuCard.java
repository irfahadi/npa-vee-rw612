package com.nxp.example.smartgreenhouse.views.menu;

import com.nxp.example.smartgreenhouse.models.menu.MenuItemData;
import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Fonts;
import com.nxp.example.smartgreenhouse.style.Images;
import ej.annotation.NonNullByDefault;
import ej.microui.display.Font;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.microui.event.Event;
import ej.microui.event.generator.Buttons;
import ej.microui.event.generator.Pointer;
import ej.mwt.Widget;
import ej.mwt.util.Size;

public class MenuCard extends Widget {

    private static final int TAP_THRESHOLD = 10;

    private int touchStartX;
    private int touchStartY;

    public interface OnCardClickListener {
        void onCardClicked(MenuItemData item);
    }

    private final Image menuFrame;
    private MenuItemData displayItem;
    private OnCardClickListener clickListener;


    public MenuCard() {
        setEnabled(true);
        this.menuFrame = Image.getImage(Images.CARD_MENU_FRAME);
    }

    public void setOnCardClickListener(OnCardClickListener listener) {
        this.clickListener = listener;
    }

    public void setDisplayItem(MenuItemData item) {
        this.displayItem = item;
        setEnabled(item != null);
        requestRender();
    }

    public MenuItemData getDisplayItem() {
        return this.displayItem;
    }

    public int getCardWidth() {
        return this.menuFrame.getWidth();
    }

    public int getCardHeight() {
        return this.menuFrame.getHeight();
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        size.setSize(this.menuFrame.getWidth(), this.menuFrame.getHeight());
    }

    @Override
    @NonNullByDefault
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);

        if (this.displayItem == null) {
            return;
        }

        Font titleFont = Fonts.jetbrainsMonoRegular12px();
        Image icon = this.displayItem.getIcon();
        String title = this.displayItem.getTitle();

        int imageX = (contentWidth - this.menuFrame.getWidth()) / 2;
        int imageY = (contentHeight - this.menuFrame.getHeight()) / 2;
        Painter.drawImage(g, this.menuFrame, imageX, imageY);

        int iconHeight = icon.getHeight();
        int titleHeight = titleFont.getHeight();
        int iconToTitleGap = 6;
        int totalContentHeight = iconHeight + iconToTitleGap + titleHeight;

        int contentStartY = imageY + (this.menuFrame.getHeight() - totalContentHeight) / 2;

        int iconX = imageX + (this.menuFrame.getWidth() - icon.getWidth()) / 2;
        Painter.drawImage(g, icon, iconX, contentStartY);

        int titleX = imageX + (this.menuFrame.getWidth() - titleFont.stringWidth(title)) / 2;
        int titleY = contentStartY + iconHeight + iconToTitleGap;
        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, title, titleFont, titleX, titleY);
    }

    @Override
    public boolean handleEvent(int event) {
        if (Event.getType(event) != Pointer.EVENT_TYPE) {
            return super.handleEvent(event);
        }

        Pointer pointer = (Pointer) Event.getGenerator(event);

        if (Buttons.isPressed(event)) {
            this.touchStartX = pointer.getX();
            this.touchStartY = pointer.getY();

            return false;
        }

        if (Buttons.isReleased(event)) {
            int deltaX = pointer.getX() - this.touchStartX;
            int deltaY = pointer.getY() - this.touchStartY;

            boolean isTap = Math.abs(deltaX) <= TAP_THRESHOLD && Math.abs(deltaY) <= TAP_THRESHOLD;

            if (isTap && this.clickListener != null && this.displayItem != null) {
                this.clickListener.onCardClicked(this.displayItem);
                return true;
            }

            return false;
        }

        return false;
    }
}