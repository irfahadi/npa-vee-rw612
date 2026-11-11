package com.nxp.example.smartgreenhouse.views.keyboard;

import ej.annotation.Nullable;
import ej.bon.Timer;
import ej.bon.TimerTask;
import ej.microui.event.Event;
import ej.microui.event.generator.Buttons;
import ej.microui.event.generator.Pointer;
import ej.mwt.event.DesktopEventGenerator;
import ej.mwt.event.PointerEventDispatcher;
import ej.widget.basic.Label;
import ej.widget.basic.OnClickListener;


public final class Key extends Label {

    public static final int ACTIVE = 1;

    private static final int REPEAT_DELAY = 600;
    private static final int REPEAT_PERIOD = 60;

    private final KeyboardEventGenerator keyboardEventGenerator;
    private final Timer timer;

    private @Nullable OnClickListener onClickListener;
    private @Nullable TimerTask repeatTask;

    private boolean repeatable;
    private boolean pressed;

    public Key(KeyboardEventGenerator keyboardEventGenerator, Timer timer) {
        this.keyboardEventGenerator = keyboardEventGenerator;
        this.timer = timer;

        this.onClickListener = null;
        this.repeatTask = null;

        this.repeatable = false;
        this.pressed = false;
    }

    public void setStandard(char character) {
        setStandard(String.valueOf(character), character);
    }

    public void setStandard(String displayedText, final char character) {
        setStandard(displayedText, character, true);
    }

    void setStandard(String displayedText, final char character, boolean requestUpdate) {
        setEnabled(true);

        setText(displayedText);

        this.onClickListener =
                new OnClickListener() {
                    @Override
                    public void onClick() {
                        Key.this.keyboardEventGenerator.send(character);
                    }
                };

        this.repeatable = true;

        removeAllClassSelectors();

        requestUpdate(requestUpdate);
    }

    public void setStandard(String displayedText, char character, int classSelector) {
        setStandard(displayedText, character, classSelector, true);
    }

    void setStandard(String displayedText, char character, int classSelector, boolean requestUpdate) {

        setStandard(displayedText, character, false);

        addClassSelector(classSelector);

        requestUpdate(requestUpdate);
    }

    public void setSpecial(String displayedText, OnClickListener listener) {
        setSpecial(displayedText, listener, true);
    }

    void setSpecial(String displayedText, OnClickListener listener, boolean requestUpdate) {
        setEnabled(true);

        setText(displayedText);

        this.onClickListener = listener;
        this.repeatable = false;

        removeAllClassSelectors();

        requestUpdate(requestUpdate);
    }

    public void setSpecial(String displayedText, OnClickListener listener, int classSelector) {
        setSpecial(displayedText, listener, classSelector, true);
    }

    void setSpecial(String displayedText, OnClickListener listener, int classSelector, boolean requestUpdate) {
        setSpecial(displayedText, listener, false);
        addClassSelector(classSelector);
        requestUpdate(requestUpdate);
    }

    public void setBlank() {
        setBlank(true);
    }

    void setBlank(boolean requestUpdate) {
        stopRepeatTask();

        this.pressed = false;
        this.onClickListener = null;
        this.repeatable = false;

        setEnabled(false);
        setText("");

        removeAllClassSelectors();

        updateStyle();

        requestUpdate(requestUpdate);
    }

    private void requestUpdate(boolean requestUpdate) {
        if (requestUpdate) {
            requestLayOut();
        }
    }

    @Override
    public boolean isInState(int state) {
        return (this.pressed && state == ACTIVE) || super.isInState(state);
    }

    @Override
    public boolean handleEvent(int event) {
        switch (Event.getType(event)) {
            case Pointer.EVENT_TYPE:
                int action = Buttons.getAction(event);
                if (action == Buttons.PRESSED) {
                    setPressed(true);

                    OnClickListener listener = this.onClickListener;

                    if (listener != null) {
                        listener.onClick();
                    }

                    startRepeatTask();
                } else if (action == Buttons.RELEASED && this.pressed) {
                    setPressed(false);
                    stopRepeatTask();

                    return true;
                }
                break;

            case DesktopEventGenerator.EVENT_TYPE:
                int desktopAction = DesktopEventGenerator.getAction(event);
                if (desktopAction == PointerEventDispatcher.EXITED && this.pressed) {
                    setPressed(false);
                    stopRepeatTask();
                }
                break;
            default:
                break;
        }

        return super.handleEvent(event);
    }

    private void setPressed(boolean pressed) {
        this.pressed = pressed;

        updateStyle();
        requestRender();
    }

    private void startRepeatTask() {
        if (!this.repeatable || this.onClickListener == null) {
            return;
        }

        TimerTask task =
                new TimerTask() {
                    @Override
                    public void run() {
                        OnClickListener listener = Key.this.onClickListener;
                        if (listener != null) {
                            listener.onClick();
                        }
                    }
                };

        this.repeatTask = task;

        this.timer.schedule(task, REPEAT_DELAY, REPEAT_PERIOD);
    }

    private void stopRepeatTask() {
        TimerTask task = this.repeatTask;

        if (task != null) {
            task.cancel();
            this.repeatTask = null;
        }
    }
}