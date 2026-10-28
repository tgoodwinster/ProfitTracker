package dev.profittracker;

/** Title dimensions after scaling. Long messages shrink to fit, without changing the saved size. */
public record AlertLayout(int x, int y, int width, int height, double scale) {
    public static AlertLayout calculate(AlertStyle style, int textWidth, int textHeight, int screenWidth, int screenHeight) {
        double scale = Math.min(style.scale, Math.min(Math.max(1, screenWidth - 12) / (double) Math.max(1, textWidth),
                Math.max(1, screenHeight - 12) / (double) Math.max(1, textHeight)));
        int w = (int) Math.ceil(textWidth * scale);
        int h = (int) Math.ceil(textHeight * scale);
        return new AlertLayout(style.position.left(screenWidth, w), style.position.top(screenHeight, h), w, h, scale);
    }
}
