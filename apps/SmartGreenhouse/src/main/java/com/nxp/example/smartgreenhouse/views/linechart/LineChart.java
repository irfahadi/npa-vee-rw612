package com.nxp.example.smartgreenhouse.views.linechart;

import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Fonts;
import com.nxp.example.smartgreenhouse.style.Images;

import ej.annotation.NonNullByDefault;
import ej.drawing.ShapePainter;
import ej.drawing.ShapePainter.Cap;
import ej.microui.display.Font;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.mwt.Widget;
import ej.mwt.util.Alignment;
import ej.mwt.util.Rectangle;
import ej.mwt.util.Size;
import ej.widget.render.StringPainter;

public class LineChart extends Widget {

    private static final int DEFAULT_SCALE_COUNT = 5;

    private static final String DECIMALS_SEPARATOR = ".";

    private static final int CONTENT_MARGIN = 10;

    private static final int PADDING_Y_BAR = 5;
    private static final int PADDING_X_BAR = 5;

    private static final int SCALE_LINE_DOT_LENGTH = 3;
    private static final int SCALE_LINE_DOT_GAP = 3;

    private static final int POINT_RADIUS = 4;
    private static final int LINE_THICKNESS = 1;
    private static final int LINE_FADE = 1;

    private static final int DECIMAL = 10;

    private static final int MAX_VISIBLE_X_LABELS = 8;

    private final Image grafikFrame;
    private final boolean drawCircle;

    private ChartPoint[] points;
    private String title;
    private String unit;

    private int scaleCount;
    private int selectedChartPointIndex;
    private int valueDecimalPlaces;

    public LineChart(String title, ChartPoint[] points) {
        this(title, points, true);
    }

    public LineChart(String title, ChartPoint[] points, boolean drawCircle) {
        super(true);

        this.grafikFrame = Image.getImage(Images.GRAFIK_FRAME);
        this.drawCircle = drawCircle;
        this.title = title == null ? "" : title;
        this.points = points == null ? new ChartPoint[0] : points;

        this.unit = "";
        this.scaleCount = DEFAULT_SCALE_COUNT;
        this.selectedChartPointIndex = -1;
        this.valueDecimalPlaces = 0;
    }

    public int getChartWidth() {
        return this.grafikFrame.getWidth();
    }

    public int getChartHeight() {
        return this.grafikFrame.getHeight();
    }

    public void setValueDecimalPlaces(int valueDecimalPlaces) {
        if (valueDecimalPlaces < 0) {
            valueDecimalPlaces = 0;
        }
        this.valueDecimalPlaces = valueDecimalPlaces;
        requestRender();
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
        requestRender();
    }

    public void setPoints(ChartPoint[] points) {
        clearPointSelection();

        if (points == null) {
            this.points = new ChartPoint[0];
        } else {
            this.points = points;
        }

        this.selectedChartPointIndex = -1;

        for (ChartPoint point : this.points) {
            if (point != null) {
                point.setSelected(false);
            }
        }

        requestRender();
    }

    public void setUnit(String unit) {
        this.unit = unit == null ? "" : unit;
        requestRender();
    }

    public String getUnit() {
        return this.unit;
    }

    public void setScaleCount(int scaleCount) {
        if (scaleCount <= 0) {
            return;
        }

        this.scaleCount = scaleCount;
        requestRender();
    }

    public int getScaleCount() {
        return this.scaleCount;
    }

    public void clearPoints() {
        clearPointSelection();

        this.points = new ChartPoint[0];
        this.selectedChartPointIndex = -1;

        requestRender();
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        size.setSize(getChartWidth(), getChartHeight());
    }

    public boolean selectPointAtLocalX(int localX) {
        if (this.points == null || this.points.length == 0) {
            return false;
        }

        Font font = Fonts.jetbrainsMonoRegular10px();

        int chartWidth = getChartWidth() - (CONTENT_MARGIN * 2);
        float topValue = getTopScaleValue();
        int yBarWidth = getYBarWidth(font, topValue);
        int xStart = CONTENT_MARGIN + yBarWidth;
        int xEnd = CONTENT_MARGIN + chartWidth;
        if (xEnd <= xStart) {
            return false;
        }

        if (localX >= xStart && localX < xEnd) {

            int selectedPoint = this.points.length * (localX - xStart) / (xEnd - xStart);

            selectPoint(selectedPoint);
            return true;
        }

        selectPoint(-1);
        return false;
    }

    public void selectPoint(int pointIndex) {
        if (this.points == null) {
            return;
        }

        if (pointIndex < -1 || pointIndex >= this.points.length) {
            return;
        }

        int lastIndex = this.selectedChartPointIndex;

        if (pointIndex == lastIndex) {
            return;
        }

        if (lastIndex >= 0 && lastIndex < this.points.length) {

            ChartPoint oldPoint = this.points[lastIndex];

            if (oldPoint != null) {
                oldPoint.setSelected(false);
            }
        }

        this.selectedChartPointIndex = pointIndex;

        if (pointIndex >= 0) {
            ChartPoint newPoint = this.points[pointIndex];

            if (newPoint == null || newPoint.getValue() < 0.0f) {
                this.selectedChartPointIndex = -1;
            } else {
                newPoint.setSelected(true);
            }
        }

        requestRender();
    }

    @Override
    @NonNullByDefault
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        Painter.drawImage(g, this.grafikFrame, 0, 0);

        if (this.points == null || this.points.length == 0) {
            return;
        }

        Font font = Fonts.jetbrainsMonoRegular10px();
        int chartWidth = contentWidth - (CONTENT_MARGIN * 2);
        int chartHeight = contentHeight - (CONTENT_MARGIN * 2);
        if (chartWidth <= 0 || chartHeight <= 0) {
            return;
        }
        renderChartContent(g, font, chartWidth, chartHeight);
    }

    private void renderChartContent(GraphicsContext g, Font font, int chartWidth, int chartHeight) {
        float topValue = getTopScaleValue();

        int fontHeight = font.getHeight();

        int yBarWidth = getYBarWidth(font, topValue);

        int xBarHeight = fontHeight + PADDING_X_BAR;

        int yBarTopHeight = getTopBarHeight(fontHeight);

        int yBarBottom = CONTENT_MARGIN + chartHeight - xBarHeight;

        int innerChartWidth = chartWidth - yBarWidth;

        if (innerChartWidth <= 0) {
            return;
        }

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        StringPainter.drawStringInArea(g, this.unit, font, CONTENT_MARGIN, CONTENT_MARGIN, yBarWidth - PADDING_Y_BAR, yBarTopHeight, Alignment.RIGHT, Alignment.TOP);

        drawSelectedPointInfo(g, font, chartWidth, yBarTopHeight);

        int numScaleValues = this.scaleCount;

        for (int i = 0; i < numScaleValues + 1; i++) {
            float scaleValue = topValue * i / numScaleValues;

            String scaleString = toStringFloat(scaleValue, 0);

            int yScale = yBarBottom + ((CONTENT_MARGIN + yBarTopHeight - yBarBottom) * i) / numScaleValues;

            g.setColor(ApplicationColors.SECONDARY_COLOR);

            StringPainter.drawStringAtPoint(g, scaleString, font, CONTENT_MARGIN + yBarWidth - PADDING_Y_BAR, yScale, Alignment.RIGHT, Alignment.VCENTER);

            drawHorizontalDottedLine(g, CONTENT_MARGIN + yBarWidth, yScale, innerChartWidth);
        }

        int chartDrawingHeight = chartHeight - yBarTopHeight - xBarHeight;

        if (chartDrawingHeight <= 0) {
            return;
        }

        Rectangle chartBounds = new Rectangle(CONTENT_MARGIN + yBarWidth, CONTENT_MARGIN + yBarTopHeight, innerChartWidth, chartDrawingHeight);

        renderPointsAndLabel(g, font, chartBounds, CONTENT_MARGIN + chartHeight, topValue);
    }

    private void drawSelectedPointInfo(GraphicsContext g, Font font, int chartWidth, int height) {
        String text = this.title;

        if (this.selectedChartPointIndex >= 0 && this.selectedChartPointIndex < this.points.length) {
            ChartPoint selectedPoint = this.points[this.selectedChartPointIndex];

            if (selectedPoint != null && selectedPoint.getValue() >= 0) {
                text = selectedPoint.getFullName() + ": " + toStringFloat(selectedPoint.getValue(), this.valueDecimalPlaces);

                if (this.unit != null && !this.unit.isEmpty()) {
                    text += this.unit;
                }
            }
        }

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        StringPainter.drawStringInArea(g, text, font, CONTENT_MARGIN, CONTENT_MARGIN, chartWidth, height, Alignment.HCENTER, Alignment.TOP);
    }

    private void renderPointsAndLabel(GraphicsContext g, Font font, Rectangle chartBounds, int contentBottom, float topValue) {
        ChartPoint[] chartPoints = this.points;

        if (chartPoints == null || chartPoints.length == 0) {
            return;
        }

        int yBottom = chartBounds.getY() + chartBounds.getHeight();

        float xStep = getStepSize(chartBounds.getWidth());

        float xPosStart = chartBounds.getX() + (xStep / 2);

        int previousX = -1;
        int previousY = -1;

        float xPos = xPosStart;

        for (int i = 0; i < chartPoints.length; i++) {
            ChartPoint chartPoint = chartPoints[i];

            int currentX = (int) xPos;

            xPos += xStep;

            if (chartPoint == null) {
                previousX = -1;
                previousY = -1;
                continue;
            }

            if (shouldDrawXLabel(i, chartPoints.length)) {
                g.setColor(ApplicationColors.SECONDARY_COLOR);
                StringPainter.drawStringAtPoint(g, chartPoint.getName(), font, currentX, contentBottom, Alignment.HCENTER, Alignment.BOTTOM);
            }

            float value = chartPoint.getValue();

            if (value < 0) {
                previousX = -1;
                previousY = -1;
                continue;
            }

            int finalLength = (int) (chartBounds.getHeight() * value / topValue);

            int currentY = yBottom - finalLength;

            if (currentY < chartBounds.getY()) {
                currentY = chartBounds.getY();
            }

            if (currentY > yBottom) {
                currentY = yBottom;
            }

            if (previousY != -1) {
                g.setColor(ApplicationColors.LINE_COLOR);
                ShapePainter.drawThickFadedLine(g, previousX, previousY, currentX, currentY, LINE_THICKNESS, LINE_FADE, Cap.NONE, Cap.NONE);
            }

            previousX = currentX;
            previousY = currentY;
        }

        if (!this.drawCircle) {
            return;
        }

        xPos = xPosStart;

        for (int i = 0; i < chartPoints.length; i++) {
            ChartPoint chartPoint = chartPoints[i];

            int currentX = (int) xPos;

            xPos += xStep;

            if (chartPoint == null) {
                continue;
            }

            float value = chartPoint.getValue();

            if (value < 0) {
                continue;
            }

            int finalLength = (int) (chartBounds.getHeight() * value / topValue);

            int currentY = yBottom - finalLength;

            if (currentY < chartBounds.getY()) {
                currentY = chartBounds.getY();
            }

            if (currentY > yBottom) {
                currentY = yBottom;
            }

            int color;

            if (chartPoint.isSelected()) {
                color = ApplicationColors.POINT_SELECTED_COLOR;
            } else {
                color = ApplicationColors.POINT_COLOR;
            }

            g.setColor(color);

            ShapePainter.drawThickFadedPoint(g, currentX, currentY, POINT_RADIUS * 2, LINE_FADE);
        }
    }

    private boolean shouldDrawXLabel(int pointIndex, int pointCount) {
        if (pointCount <= 0) {
            return false;
        }

        if (pointCount <= MAX_VISIBLE_X_LABELS) {
            return true;
        }

        int labelStep = (pointCount + MAX_VISIBLE_X_LABELS - 1) / MAX_VISIBLE_X_LABELS;

        return pointIndex == 0 || pointIndex == pointCount - 1 || pointIndex % labelStep == 0;
    }

    private void drawHorizontalDottedLine(GraphicsContext g, int x, int y, int width) {
        g.setColor(ApplicationColors.GRAPH_LINE_COLOR);

        int endX = x + width;

        for (int currentX = x; currentX < endX; currentX += SCALE_LINE_DOT_LENGTH + SCALE_LINE_DOT_GAP) {
            int lineEndX = currentX + SCALE_LINE_DOT_LENGTH;

            if (lineEndX > endX) {
                lineEndX = endX;
            }

            Painter.drawLine(g, currentX, y, lineEndX, y);
        }
    }

    private int getTopBarHeight(int fontHeight) {
        return fontHeight + (fontHeight / 2);
    }

    private int getYBarWidth(Font font, float topValue) {
        String topValueString = toStringFloat(topValue, 0);

        int widthUnit = font.stringWidth(this.unit);

        int widthValue = font.stringWidth(topValueString);

        return max(widthUnit, widthValue) + PADDING_Y_BAR;
    }

    protected float getStepSize(int width) {
        if (this.points == null || this.points.length == 0) {
            return 0;
        }

        return (float) width / this.points.length;
    }

    protected static String toStringFloat(float value, int decimals) {
        if (decimals < 0) {
            decimals = 0;
        }

        StringBuilder builder = new StringBuilder();

        boolean negative = value < 0;

        float absoluteValue = negative ? -value : value;

        int integerPart = (int) absoluteValue;

        if (negative) {
            builder.append("-");
        }

        builder.append(integerPart);

        if (decimals > 0) {
            builder.append(DECIMALS_SEPARATOR);
            float decimalPart = absoluteValue - integerPart;

            for (int i = 0; i < decimals; i++) {
                decimalPart *= DECIMAL;

                int digit = (int) decimalPart;

                builder.append(digit);

                decimalPart -= digit;
            }
        }

        return builder.toString();
    }

    private float getMaxPointValue() {
        float maxValue = 0.0f;

        if (this.points == null) {
            return maxValue;
        }

        for (ChartPoint point : this.points) {
            if (point == null) {
                continue;
            }

            float value = point.getValue();

            if (value >= 0 && value > maxValue) {
                maxValue = value;
            }
        }

        return maxValue;
    }

    private float getTopScaleValue() {
        int numValues = this.scaleCount;

        float value = getMaxPointValue();

        float multiplier = 1.0f;

        if (value <= 0) {
            return 100;
        }

        while (value < DECIMAL) {
            value *= DECIMAL;
            multiplier /= DECIMAL;
        }

        while (value > DECIMAL * (2 * numValues)) {
            value /= DECIMAL;
            multiplier *= DECIMAL;
        }

        int n = (int) Math.ceil(value);

        int extra = n % numValues;

        if (extra > 0) {
            n += numValues - extra;
        }

        return n * multiplier;
    }

    private void clearPointSelection() {
        if (this.points == null) {
            return;
        }

        for (ChartPoint point : this.points) {
            if (point != null) {
                point.setSelected(false);
            }
        }
    }

    private int max(int valueA, int valueB) {
        return Math.max(valueA, valueB);
    }
}