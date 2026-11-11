package com.nxp.example.smartgreenhouse.views.keyboard;

import ej.microui.event.Event;
import ej.microui.event.EventGenerator;

public final class KeyboardEventGenerator extends EventGenerator {

    public static final int EVENTGENERATOR_ID = 0x10;

    @Override
    public int getEventType() {
        return EVENTGENERATOR_ID;
    }

    public void send(char character) {
        int event = Event.buildEvent(getEventType(), this, character);
        sendEvent(event);
    }

    public char getChar(int event) {
        return (char) Event.getData(event);
    }
}