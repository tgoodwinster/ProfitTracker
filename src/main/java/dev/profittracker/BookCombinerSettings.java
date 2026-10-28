package dev.profittracker;

/** One explicitly learned server/menu/book profile. Running state is never persisted. */
public final class BookCombinerSettings {
    public String server = "";
    public String menuTitle = "";
    public int rows;
    public int firstSlot = -1;
    public int secondSlot = -1;
    public int outputSlot = -1;
    public int confirmSlot = -1;
    public String confirmItem = "";
    public String firstIdle = "";
    public String secondIdle = "";
    public String outputIdle = "";
    public String enchantment = "";
    public int inputLevel = 1;
    public int maxLevel = 5;
    public int delayMs = 750;

    public void sanitize() {
        server = server == null ? "" : server;
        menuTitle = ChatTitleRule.clean(menuTitle);
        enchantment = enchantment == null ? "" : enchantment;
        firstIdle = firstIdle == null ? "" : firstIdle;
        secondIdle = secondIdle == null ? "" : secondIdle;
        outputIdle = outputIdle == null ? "" : outputIdle;
        confirmItem = confirmItem == null ? "" : confirmItem;
        delayMs = Math.clamp(delayMs, 500, 5_000);
        maxLevel = Math.clamp(maxLevel, 2, 10);
    }

    public boolean configured() {
        int size = rows * 9;
        return !server.isBlank() && !menuTitle.isBlank() && rows >= 1 && rows <= 6
                && firstSlot >= 0 && firstSlot < size && secondSlot >= 0 && secondSlot < size
                && outputSlot >= 0 && outputSlot < size
                && confirmSlot >= 0 && confirmSlot < size && !confirmItem.isBlank()
                && firstSlot != secondSlot && firstSlot != outputSlot && secondSlot != outputSlot
                && confirmSlot != firstSlot && confirmSlot != secondSlot && confirmSlot != outputSlot
                && !enchantment.isBlank() && inputLevel >= 1 && inputLevel < maxLevel;
    }
}
