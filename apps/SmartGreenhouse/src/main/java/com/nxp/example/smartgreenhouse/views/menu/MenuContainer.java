package com.nxp.example.smartgreenhouse.views.menu;

import com.nxp.example.smartgreenhouse.models.menu.MenuItemData;
import com.nxp.example.smartgreenhouse.style.Images;
import com.nxp.example.smartgreenhouse.views.HorizontalSwipeListener;
import ej.annotation.NonNullByDefault;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.microui.event.Event;
import ej.microui.event.generator.Buttons;
import ej.microui.event.generator.Pointer;
import ej.mwt.Container;
import ej.mwt.util.Size;

public class MenuContainer extends Container {

    public interface onSwipeDownListener {
        void onSwipeDown();
    }

    public interface OnMenuItemClickListener {
        void onMenuItemClicked(MenuItemData item);
    }

    private static final int SWIPE_THRESHOLD = 35;
    private int touchStartX;
    private int touchStartY;

    private static final int CARD_COLUMNS = 3;
    private static final int CARD_ROWS = 2;
    private static final int CARD_GAP_X = 8;
    private static final int CARD_GAP_Y = 8;

    private final Image menuFrame;
    private final MenuHeader menuHeader;
    private MenuCard[] cards;
    private final MenuFooter menuFooter;

    private onSwipeDownListener onSwipeDownListener;
    private HorizontalSwipeListener horizontalSwipeListener;
    private OnMenuItemClickListener menuItemClickListener;

    public MenuContainer() {
        setEnabled(true);
        this.menuFrame = Image.getImage(Images.MENU_FRAME);
        this.menuHeader = new MenuHeader();
        this.menuFooter = new MenuFooter();

        int maxVisible = CARD_COLUMNS * CARD_ROWS;
        this.cards = new MenuCard[maxVisible];;

        for (int i = 0; i < maxVisible; i++) {
            this.cards[i] = new MenuCard();

            this.cards[i].setOnCardClickListener(
                    new MenuCard.OnCardClickListener() {
                        @Override
                        public void onCardClicked(MenuItemData item) {
                            if (menuItemClickListener != null) {
                                menuItemClickListener.onMenuItemClicked(item);
                            }
                        }
                    }
            );

            addChild(this.cards[i]);
        }
    }

    public int getMenuWidth() {
        return menuFrame.getWidth();
    }
    public int getMenuHeight() {
        return menuFrame.getHeight();
    }

    public void setOnSwipeListener(HorizontalSwipeListener listener) {
        this.horizontalSwipeListener = listener;
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        this.menuItemClickListener = listener;
    }

    public void setItems(MenuItemData[] items) {
        int itemCount = items == null ? 0 : items.length;

        for (int i = 0; i < this.cards.length; i++) {
            MenuItemData item = i < itemCount ? items[i] : null;
            this.cards[i].setDisplayItem(item);
        }

        requestRender();
    }

    public void setOnSwipeDownListener(onSwipeDownListener listener) {
        this.onSwipeDownListener = listener;
    }

    public void setIndicator(int total, int selected) {
        this.menuFooter.setPageCount(total);
        this.menuFooter.setCurrentPage(selected);
        requestRender();
    }

    @Override
    protected void layOutChildren(int contentWidth, int contentHeight) {
        if (this.cards == null || this.cards.length == 0) {
            return;
        }

        int cardWidth = this.cards[0].getCardWidth();
        int cardHeight = this.cards[0].getCardHeight();

        int gridWidth = (CARD_COLUMNS * cardWidth) + ((CARD_COLUMNS - 1) * CARD_GAP_X);

        int contentAreaY = menuHeader.getMenuHeaderHeight();

        int startX = (contentWidth - gridWidth) / 2;
        int startY = contentAreaY + 2;

        for (int i = 0; i < this.cards.length; i++) {
            int column = i % CARD_COLUMNS;
            int row = i / CARD_COLUMNS;

            int cardX = startX + (column * (cardWidth + CARD_GAP_X));
            int cardY = startY + (row * (cardHeight + CARD_GAP_Y));

            layOutChild(this.cards[i], cardX, cardY, cardWidth, cardHeight);
        }
    }

    @Override
    @NonNullByDefault
    protected void computeContentOptimalSize(Size size) {
        int menuWidth = getMenuWidth();
        int menuHeight = getMenuHeight();

        if (this.cards != null) {
            for (MenuCard card : this.cards) {
                computeChildOptimalSize(card, card.getCardWidth(), card.getCardHeight());
            }
        }

        size.setSize(menuWidth, menuHeight);
    }

    @Override
    @NonNullByDefault
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        Painter.drawImage(g, this.menuFrame, 0, 0);

        this.menuHeader.render(g, contentWidth);

        this.menuFooter.render(g, contentWidth, contentHeight);
        
        super.renderContent(g, contentWidth, contentHeight);
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
            int deltaY = pointer.getY() - this.touchStartY;
            int deltaX = pointer.getX() - this.touchStartX;

            if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > SWIPE_THRESHOLD) {
                if (deltaX < 0 && this.horizontalSwipeListener != null) {
                    this.horizontalSwipeListener.onSwipeLeft();
                } else if (deltaX > 0 && this.horizontalSwipeListener != null) {
                    this.horizontalSwipeListener.onSwipeRight();
                }
                return true;
            }

            if (deltaY > 0 && Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > SWIPE_THRESHOLD) {
                if (this.onSwipeDownListener != null) {
                    this.onSwipeDownListener.onSwipeDown();
                }
                return true;
            }

            return false;
        }

        return false;
    };
}
