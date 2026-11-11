package com.nxp.example.smartgreenhouse.views.actuator;

import com.nxp.example.smartgreenhouse.style.Images;

import ej.annotation.NonNullByDefault;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.microui.event.Event;
import ej.microui.event.generator.Buttons;
import ej.microui.event.generator.Pointer;
import ej.mwt.Widget;
import ej.mwt.util.Size;

public final class ActuatorToggle extends Widget {

    private static final int DEFAULT_WIDTH = 43;
    private static final int DEFAULT_HEIGHT = 18;

    private static final int INDICATOR_PADDING_X = 3;
    private static final int INDICATOR_PADDING_Y = 2;

    private static final int MOVE_THRESHOLD = 8;

    private static final ActuatorToggleListener EMPTY_LISTENER =
            new ActuatorToggleListener() {
                @Override
                public void onToggleRequested(boolean targetState) {
                    // Tidak melakukan apa pun.
                }
            };

    private final Image toggleFrame;
    private final Image toggleIndicator;

    private boolean checked;

    private ActuatorToggleListener toggleListener;

    private boolean pointerPressed;
    private boolean pointerMoved;

    private int pointerStartX;
    private int pointerStartY;

    public ActuatorToggle() {
        super(true);
        this.toggleFrame = Image.getImage(Images.TOGGLE_FRAME);
        this.toggleIndicator = Image.getImage(Images.TOGGLE_INDICATOR);

        this.checked = false;
        this.toggleListener = EMPTY_LISTENER;

        this.pointerPressed = false;
        this.pointerMoved = false;
    }

    public void setOnToggleRequestedListener(ActuatorToggleListener listener) {
        this.toggleListener = listener == null ? EMPTY_LISTENER : listener;
    }

    public boolean isChecked() {
        return this.checked;
    }

    public void setChecked(boolean checked) {
        if (this.checked == checked) {
            return;
        }

        this.checked = checked;
        requestRender();
    }

    @Override
    @NonNullByDefault
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        if (contentWidth <= 0 || contentHeight <= 0) {
            return;
        }

        int frameX = (contentWidth - this.toggleFrame.getWidth()) / 2;
        int frameY = (contentHeight - this.toggleFrame.getHeight()) / 2;

        Painter.drawImage(g, this.toggleFrame, frameX, frameY);

        int indicatorX;

        if (this.checked) {
            indicatorX = frameX + this.toggleFrame.getWidth() - INDICATOR_PADDING_X - this.toggleIndicator.getWidth();
        } else {
            indicatorX = frameX + INDICATOR_PADDING_X;
        }

        int indicatorY = frameY + INDICATOR_PADDING_Y;
        Painter.drawImage(g, this.toggleIndicator, indicatorX, indicatorY);
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        size.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @Override
    public boolean handleEvent(int event) {
        if (Event.getType(event) != Pointer.EVENT_TYPE) {
            return super.handleEvent(event);
        }

        Pointer pointer = (Pointer) Event.getGenerator(event);
        int action = Buttons.getAction(event);

        if (action == Buttons.PRESSED) {
            this.pointerPressed = true;
            this.pointerMoved = false;

            this.pointerStartX = pointer.getX();
            this.pointerStartY = pointer.getY();

            return true;
        }

        if (action == Pointer.DRAGGED) {
            if (!this.pointerPressed) {
                return false;
            }

            int currentX = pointer.getX();
            int currentY = pointer.getY();

            int distanceX = Math.abs(currentX - this.pointerStartX);
            int distanceY = Math.abs(currentY - this.pointerStartY);

            if (distanceX > MOVE_THRESHOLD || distanceY > MOVE_THRESHOLD) {
                this.pointerMoved = true;
            }

            return true;
        }

        if (action == Buttons.RELEASED) {
            boolean validClick = this.pointerPressed && !this.pointerMoved;

            this.pointerPressed = false;
            this.pointerMoved = false;

            if (validClick) {
                boolean targetState = !this.checked;

                this.toggleListener.onToggleRequested(targetState);
            }

            return true;
        }

        return super.handleEvent(event);
    }

    @Override
    protected void onHidden() {
        this.pointerPressed = false;
        this.pointerMoved = false;
    }
}