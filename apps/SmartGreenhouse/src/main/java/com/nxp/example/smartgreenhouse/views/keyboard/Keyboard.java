package com.nxp.example.smartgreenhouse.views.keyboard;

import java.util.ArrayList;
import java.util.List;

import ej.bon.Timer;
import ej.mwt.Container;
import ej.mwt.Widget;
import ej.mwt.util.Size;
import ej.widget.basic.OnClickListener;

public final class Keyboard extends Container {

    private enum Mapping {
        ABC("ABC"),
        NUMERIC("123"),
        SYMBOL("#+=");

        private final String text;

        Mapping(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }
    }

    private static final class Cell {

        private final Key key;
        private final int startColumn;
        private final int colspan;

        Cell(Key key, int startColumn, int colspan) {
            this.key = key;
            this.startColumn = startColumn;
            this.colspan = colspan;
        }
    }

    private static final class Row {

        private final int length;
        private final List<Cell> cells;

        Row(int length) {
            this.length = length;
            this.cells = new ArrayList<Cell>(length);
        }

        public Key getKey(int index) {
            return this.cells.get(index).key;
        }
    }

    private static final int ROW_SIZE = 10;
    private static final int ROWS_COUNT = 4;

    private static final int FIRST_ROW = 0;
    private static final int SECOND_ROW = 1;
    private static final int THIRD_ROW = 2;
    private static final int FOURTH_ROW = 3;

    private static final int BLANK_KEY_INDEX = 8;

    private static final int LEFT_KEY_INDEX = 1;
    private static final int SPACE_KEY_INDEX = 2;
    private static final int RIGHT_KEY_INDEX = 3;
    private static final int SPECIAL_KEY_INDEX = 4;

    private static final int SPACE_KEY_COLSPAN = 5;

    private final Timer timer;
    private final Row[] rows;
    private final KeyboardLayout layout;
    private final KeyboardEventGenerator keyboardEvents;

    private final int spaceKeySelector;
    private final int shiftKeyInactiveSelector;
    private final int shiftKeyActiveSelector;
    private final int switchMappingKeySelector;

    private boolean active;
    private boolean registered;

    public Keyboard(Timer timer, int spaceKeySelector, int shiftKeyInactiveSelector, int shiftKeyActiveSelector, int switchMappingKeySelector) {
        this.timer = timer;

        this.spaceKeySelector = spaceKeySelector;
        this.shiftKeyInactiveSelector = shiftKeyInactiveSelector;
        this.shiftKeyActiveSelector = shiftKeyActiveSelector;
        this.switchMappingKeySelector = switchMappingKeySelector;

        this.keyboardEvents = new KeyboardEventGenerator();
        this.rows = new Row[ROWS_COUNT];

        for (int i = 0; i < this.rows.length; i++) {
            this.rows[i] = new Row(ROW_SIZE);
        }

        this.layout = new KeyboardLayout();

        this.active = false;
        this.registered = false;

        createKeys();
        setLowerCaseMapping();
    }

    public void activate() {
        this.active = true;
        registerEventGenerator();
    }

    public void deactivate() {
        this.active = false;
        unregisterEventGenerator();
    }

    @Override
    protected void onShown() {
        super.onShown();
        registerEventGenerator();
    }

    @Override
    protected void onHidden() {
        unregisterEventGenerator();
        super.onHidden();
    }

    private void registerEventGenerator() {
        if (!this.active || this.registered) {
            return;
        }

        this.keyboardEvents.addToSystemPool();
        this.registered = true;
    }

    private void unregisterEventGenerator() {
        if (!this.registered) {
            return;
        }

        this.keyboardEvents.removeFromSystemPool();
        this.registered = false;
    }

    private void createKeys() {
        createFullRow(FIRST_ROW, ROW_SIZE, new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1});

        createFullRow(SECOND_ROW, ROW_SIZE, new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1});

        createFullRow(THIRD_ROW, ROW_SIZE - 1, new int[]{1, 1, 1, 1, 1, 1, 1, 1, 2});

        createFullRow(FOURTH_ROW, ROW_SIZE - 1, new int[]{1, 1, SPACE_KEY_COLSPAN, 0, 0, 0, 0, 1, 2});
    }

    private void createFullRow(int rowIndex, int size, int[] cellWrap) {
        Row row = this.rows[rowIndex];

        KeyboardEventGenerator eventGenerator = this.keyboardEvents;

        for (int i = 0; i < size; i++) {
            int colspan = cellWrap[i];

            if (colspan > 0) {
                addKey(new Key(eventGenerator, this.timer), row, i, colspan);
            }
        }
    }

    private void addKey(Key key, Row row, int column, int colspan) {
        row.cells.add(new Cell(key, column, colspan));

        super.addChild(key);
    }

    private void setLowerCaseMapping() {
        setMapping(KeyboardLayout.LOWER_CASE_LAYOUT_INDEX);

        setShiftKey(THIRD_ROW, 0, false);

        setMappingKey(FOURTH_ROW, 0, Mapping.NUMERIC);

        requestLayOut();
    }

    private void setUpperCaseMapping() {
        setMapping(KeyboardLayout.UPPER_CASE_LAYOUT_INDEX);

        setShiftKey(THIRD_ROW, 0, true);

        setMappingKey(FOURTH_ROW, 0, Mapping.NUMERIC);

        requestLayOut();
    }

    private void setNumericMapping() {
        setMapping(KeyboardLayout.NUMERIC_LAYOUT_INDEX);

        setMappingKey(THIRD_ROW, 0, Mapping.SYMBOL);

        setMappingKey(FOURTH_ROW, 0, Mapping.ABC);

        requestLayOut();
    }

    private void setSymbolMapping() {
        setMapping(KeyboardLayout.SYMBOL_LAYOUT_INDEX);

        setMappingKey(THIRD_ROW, 0, Mapping.NUMERIC);

        setMappingKey(FOURTH_ROW, 0, Mapping.ABC);

        requestLayOut();
    }

    private void setMapping(int layoutId) {
        this.layout.setCurrentLayout(layoutId);

        String firstRowChars = this.layout.getFirstRow();
        for (int i = 0; i < ROW_SIZE; i++) {
            setStandardKey(FIRST_ROW, i, firstRowChars.charAt(i));
        }

        String secondRowChars = this.layout.getSecondRow();
        for (int i = 0; i < ROW_SIZE; i++) {
            setStandardKey(SECOND_ROW, i, secondRowChars.charAt(i));
        }

        String thirdRowChars = this.layout.getThirdRow();
        for (int i = 0; i < thirdRowChars.length(); i++) {
            setStandardKey(THIRD_ROW, i + 1, thirdRowChars.charAt(i));
        }

        setBlankKey(THIRD_ROW, BLANK_KEY_INDEX);

        setStandardKey(FOURTH_ROW, LEFT_KEY_INDEX, "<", ControlCharacters.VK_LEFT);

        setSpaceKey(FOURTH_ROW, SPACE_KEY_INDEX);

        setStandardKey(FOURTH_ROW, RIGHT_KEY_INDEX, ">", ControlCharacters.VK_RIGHT);

        setBlankKey(FOURTH_ROW, SPECIAL_KEY_INDEX);
    }

    private Key getKey(int row, int index) {
        return this.rows[row].getKey(index);
    }

    private void setStandardKey(int row, int index, char character) {
        if (character == ControlCharacters.NULL) {
            setBlankKey(row, index);

            return;
        }

        if (character == ControlCharacters.BACK_SPACE) {

            setStandardKey(row, index, "<-", character);

            return;
        }

        setStandardKey(row, index, String.valueOf(character), character);
    }

    private void setStandardKey(int row, int index, String displayedText, char character) {
        getKey(row, index).setStandard(displayedText, character, false);
    }

    private void setBlankKey(int row, int index) {
        getKey(row, index).setBlank(false);
    }

    private void setSpaceKey(int row, int index) {
        getKey(row, index).setStandard(" ", ControlCharacters.SPACE, this.spaceKeySelector, false);
    }

    private void setShiftKey(int row, int index, final boolean activeShift) {
        OnClickListener listener =
                new OnClickListener() {
                    @Override
                    public void onClick() {
                        if (activeShift) {
                            setLowerCaseMapping();
                        } else {
                            setUpperCaseMapping();
                        }
                    }
                };

        int selector = activeShift ? this.shiftKeyActiveSelector : this.shiftKeyInactiveSelector;

        getKey(row, index).setSpecial("^", listener, selector, false);
    }

    private void setMappingKey(int row, int index, final Mapping mapping) {
        OnClickListener listener =
                new OnClickListener() {
                    @Override
                    public void onClick() {
                        switch (mapping) {
                            case ABC:
                                setLowerCaseMapping();
                                break;
                            case NUMERIC:
                                setNumericMapping();
                                break;
                            case SYMBOL:
                                setSymbolMapping();
                                break;
                            default:
                                break;
                        }
                    }
                };

        getKey(row, index).setSpecial(mapping.getText(), listener, this.switchMappingKeySelector, false);
    }

    @Override
    public void computeContentOptimalSize(Size availableSize) {
        int widthHint = availableSize.getWidth();
        int heightHint = availableSize.getHeight();

        if (getChildrenCount() == 0) {
            return;
        }

        boolean widthConstraint = widthHint != Widget.NO_CONSTRAINT;

        boolean heightConstraint = heightHint != Widget.NO_CONSTRAINT;

        int maximumRowLength = 1;

        for (Row row : this.rows) {
            maximumRowLength = Math.max(maximumRowLength, row.length);
        }

        int cellWidth = widthConstraint ? widthHint / maximumRowLength : Widget.NO_CONSTRAINT;

        int cellHeight = heightConstraint ? heightHint / this.rows.length : Widget.NO_CONSTRAINT;

        int maximumCellWidth = 0;
        int maximumCellHeight = 0;

        for (Row row : this.rows) {
            for (Cell cell : row.cells) {
                computeChildOptimalSize(cell.key, cellWidth * cell.colspan, cellHeight);

                maximumCellWidth = Math.max(maximumCellWidth, cell.key.getWidth() / cell.colspan);
                maximumCellHeight = Math.max(maximumCellHeight, cell.key.getHeight());
            }
        }

        availableSize.setSize(maximumCellWidth * maximumRowLength, maximumCellHeight * this.rows.length);
    }

    @Override
    protected void layOutChildren(int contentWidth, int contentHeight) {
        if (getChildrenCount() == 0) {
            return;
        }

        int cellHeight = contentHeight / this.rows.length;
        int maximumRowLength = 1;

        for (Row row : this.rows) {
            maximumRowLength = Math.max(maximumRowLength, row.length);
        }

        int cellWidth = contentWidth / maximumRowLength;

        int rowY = 0;

        for (Row row : this.rows) {
            int rowWidth = row.length * cellWidth;

            int rowX = (contentWidth - rowWidth) / 2;

            for (Cell cell : row.cells) {
                layOutChild(cell.key, rowX + cell.startColumn * cellWidth, rowY, cell.colspan * cellWidth, cellHeight);
            }

            rowY += cellHeight;
        }
    }

    public KeyboardEventGenerator getEventGenerator() {
        return this.keyboardEvents;
    }
}