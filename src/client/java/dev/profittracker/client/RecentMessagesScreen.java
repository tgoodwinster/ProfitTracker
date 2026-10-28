package dev.profittracker.client;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class RecentMessagesScreen extends Screen {
    private final Screen parent;
    private final List<String> messages;
    private final Consumer<String> choose;
    private int page;

    RecentMessagesScreen(ProfitTrackerClient mod, Screen parent, Consumer<String> choose) {
        super(Component.literal("Choose a recent chat message"));
        this.parent = parent;
        this.choose = choose;
        messages = mod.recentMessages();
    }

    @Override
    protected void init() {
        int w = Math.min(420, width - 16);
        int x = (width - w) / 2;
        int y = Math.max(5, (height - 230) / 2);
        for (int i = 0; i < 5 && page * 5 + i < messages.size(); i++) {
            String message = messages.get(page * 5 + i);
            var choice = addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(message, w - 12)), button -> {
                choose.accept(message);
                minecraft.gui.setScreen(parent);
            }).bounds(x, y + 36 + i * 28, w, 20).build());
            choice.setTooltip(Tooltip.create(Component.literal(message)));
        }
        addRenderableWidget(Button.builder(Component.literal("Previous"), button -> { page--; rebuildWidgets(); })
                .bounds(x, y + 178, w / 2 - 4, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal("Next"), button -> { page++; rebuildWidgets(); })
                .bounds(x + w / 2 + 4, y + 178, w / 2 - 4, 20).build()).active = (page + 1) * 5 < messages.size();
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose()).bounds(x, y + 204, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, Math.max(5, (height - 230) / 2) + 8, 0xFFFFD166);
        if (messages.isEmpty()) graphics.centeredText(font, "No recent messages yet. You can paste text instead.", width / 2, height / 2, 0xFFB7C5D3);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
