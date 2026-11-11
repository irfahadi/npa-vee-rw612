package com.nxp.example.smartgreenhouse.views.overview;

import com.nxp.example.smartgreenhouse.models.sensor.SensorDefinition;
import com.nxp.example.smartgreenhouse.models.sensor.SensorDefinitionProvider;
import com.nxp.example.smartgreenhouse.models.sensor.SensorDisplayItem;
import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.views.HorizontalSwipeListener;
import ej.annotation.NonNullByDefault;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Painter;
import ej.microui.event.Event;
import ej.microui.event.generator.Buttons;
import ej.microui.event.generator.Pointer;
import ej.widget.container.Grid;

public class Overview extends Grid {

    private static final int COLUMN_COUNT = 3;
    private static final int SWIPE_THRESHOLD = 35;

    private int touchStartX;
    private int touchStartY;

    private final SensorCard[] sensorCards;
    private HorizontalSwipeListener horizontalSwipeListener;

    public Overview() {
        super(true, COLUMN_COUNT);
        setEnabled(true);

        int count = 0;
        for (SensorDefinition def : SensorDefinitionProvider.getAll()) {
            if (def.isVisible()) count++;
        }

        this.sensorCards = new SensorCard[count];
        for (int i = 0; i < count; i++) {
            this.sensorCards[i] = new SensorCard();
            addChild(this.sensorCards[i]);
        }
    }

    public void setOnSwipeListener(HorizontalSwipeListener horizontalSwipeListener) {
        this.horizontalSwipeListener = horizontalSwipeListener;
    }

    public void setItems(SensorDisplayItem[] items) {
        int itemCount = Math.min(items.length, this.sensorCards.length);

        for (int i = 0; i < this.sensorCards.length; i++) {
            SensorDisplayItem item = i < itemCount ? items[i] : null;

            this.sensorCards[i].setDisplayItem(item);
        }

        requestRender();
    }

    @Override
    @NonNullByDefault
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);

        super.renderContent(g, contentWidth, contentHeight);
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
            int x = pointer.getX();
            int y = pointer.getY();

            int deltaX = x - this.touchStartX;
            int deltaY = y - this.touchStartY;

            if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > SWIPE_THRESHOLD) {
                if (deltaX < 0 && this.horizontalSwipeListener != null) {
                    this.horizontalSwipeListener.onSwipeLeft();
                } else if (deltaX > 0 && this.horizontalSwipeListener != null) {
                    this.horizontalSwipeListener.onSwipeRight();
                }
                return true;
            }
        }

        return super.handleEvent(event);
    }
}