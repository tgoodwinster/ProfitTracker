package dev.profittracker.client;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Vanilla slider with explicit steps, visible values, and matching keyboard increments. */
final class SettingSlider extends AbstractSliderButton {
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> label;
    private final DoubleConsumer changed;
    private double number;

    SettingSlider(int x, int y, int width, double min, double max, double step, double initial,
                  DoubleFunction<String> label, DoubleConsumer changed) {
        super(x, y, width, 20, Component.empty(), 0);
        this.min = min;
        this.max = max;
        this.step = step;
        this.label = label;
        this.changed = changed;
        active = min < max;
        setNumber(initial);
    }

    /** Update a linked slider without firing its callback or rounding an existing saved value. */
    void setNumber(double number) {
        this.number = Double.isFinite(number) ? Math.clamp(number, min, max) : min;
        value = min == max ? 0 : (this.number - min) / (max - min);
        updateMessage();
    }

    @Override protected void updateMessage() { setMessage(Component.literal(label.apply(number))); }

    @Override protected void applyValue() {
        setNumber(min + Math.round(value * (max - min) / step) * step);
        changed.accept(number);
    }

    @Override public boolean keyPressed(KeyEvent key) {
        if (canChangeValue && (key.key() == GLFW.GLFW_KEY_LEFT || key.key() == GLFW.GLFW_KEY_RIGHT)) {
            setNumber(number + (key.key() == GLFW.GLFW_KEY_RIGHT ? step : -step));
            changed.accept(number);
            return true;
        }
        return super.keyPressed(key);
    }
}
