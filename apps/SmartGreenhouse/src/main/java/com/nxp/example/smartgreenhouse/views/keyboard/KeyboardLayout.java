package com.nxp.example.smartgreenhouse.views.keyboard;

public final class KeyboardLayout {

    public static final int LOWER_CASE_LAYOUT_INDEX = 0;
    public static final int UPPER_CASE_LAYOUT_INDEX = 1;
    public static final int NUMERIC_LAYOUT_INDEX = 2;
    public static final int SYMBOL_LAYOUT_INDEX = 3;

    private static final String[] LOWER_CASE_LAYOUT = {
            "qwertyuiop",
            "asdfghjkl" + ControlCharacters.BACK_SPACE,
            "zxcvbnm"
    };

    private static final String[] UPPER_CASE_LAYOUT = {
            "QWERTYUIOP",
            "ASDFGHJKL" + ControlCharacters.BACK_SPACE,
            "ZXCVBNM"
    };

    private static final String[] NUMERIC_LAYOUT = {
            "1234567890",
            "-/:;()$&@" + ControlCharacters.BACK_SPACE,
            ".,?!'_+"
    };

    private static final String[] SYMBOL_LAYOUT = {
            "[]{}#%^*+=",
            "_\\|~<>?!#" + ControlCharacters.BACK_SPACE,
            ".,?!'_-"
    };

    private int currentLayout;

    public KeyboardLayout() {
        this.currentLayout = LOWER_CASE_LAYOUT_INDEX;
    }

    public String getFirstRow() {
        return getLayoutRowContent(0);
    }

    public String getSecondRow() {
        return getLayoutRowContent(1);
    }

    public String getThirdRow() {
        return getLayoutRowContent(2);
    }

    public int getCurrentLayout() {
        return this.currentLayout;
    }

    public void setCurrentLayout(int layout) {
        if (layout < LOWER_CASE_LAYOUT_INDEX) {
            layout = LOWER_CASE_LAYOUT_INDEX;
        } else if (layout > SYMBOL_LAYOUT_INDEX) {
            layout = SYMBOL_LAYOUT_INDEX;
        }

        this.currentLayout = layout;
    }

    private String getLayoutRowContent(int row) {
        switch (this.currentLayout) {
            case LOWER_CASE_LAYOUT_INDEX:
                return LOWER_CASE_LAYOUT[row];
            case UPPER_CASE_LAYOUT_INDEX:
                return UPPER_CASE_LAYOUT[row];
            case NUMERIC_LAYOUT_INDEX:
                return NUMERIC_LAYOUT[row];
            default:
                return SYMBOL_LAYOUT[row];
        }
    }
}