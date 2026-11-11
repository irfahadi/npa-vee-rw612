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
import ej.mwt.util.Size;

public class FooterOverview extends Widget {

    public interface onSwipeUpListener {
        void onSwipeUp();
    }

    private static final String TITLE = "USAP KE ATAS";
    private static final int SWIPE_THRESHOLD = 35;

    private final Image image;
    private final Image icon;

    private onSwipeUpListener onSwipeUpListener;

    private int touchStartX;
    private int touchStartY;

    public FooterOverview() {
        setEnabled(true);

        this.image = Image.getImage(Images.FOOTER_FRAME);
        this.icon = Image.getImage(Icons.SWIPE_UP_ICON_24);
    }

    public int getFooterHeight() {
        return this.image.getHeight();
    }

    public void setOnSwipeUpListener(onSwipeUpListener listener) {
        this.onSwipeUpListener = listener;
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        size.setSize(this.image.getWidth(), this.image.getHeight());
    }

    @Override
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        Font titleFont = Fonts.jetbrainsMonoBold10px();

        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);

        Painter.drawImage(g, this.image, 0, 0);

        int iconX = (contentWidth - this.icon.getWidth()) / 2;
        Painter.drawImage(g, this.icon, iconX, 1);

        int textWidth = titleFont.stringWidth(TITLE);
        int textX = (contentWidth - textWidth) / 2;
        int textY = 22;

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, TITLE, titleFont, textX, textY);

    }

    @Override
    public boolean handleEvent(int event) {
        if (Event.getType(event) != Pointer.EVENT_TYPE) {
            return super.handleEvent(event);
        }

        if (Buttons.isPressed(event)) {
            Pointer pointer = (Pointer) Event.getGenerator(event);
            this.touchStartX = pointer.getX();
            this.touchStartY = pointer.getY();
            return true;
        }

        if (Buttons.isReleased(event)) {
            Pointer pointer = (Pointer) Event.getGenerator(event);
            int x =  pointer.getX();
            int y = pointer.getY();

            int deltaX = x - this.touchStartX;
            int deltaY = y - this.touchStartY;

            if (deltaY < 0 && Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > SWIPE_THRESHOLD) {
                if (this.onSwipeUpListener != null) {
                    this.onSwipeUpListener.onSwipeUp();
                }
                return true;
            }
        }

        return super.handleEvent(event);
    }
}
