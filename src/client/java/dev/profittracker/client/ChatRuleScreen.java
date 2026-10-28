package dev.profittracker.client;

import dev.profittracker.ChatTitleRule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ChatRuleScreen extends Screen {
    private final ProfitTrackerClient mod;
    private final Screen parent;
    private final ChatTitleRule draft;
    private final boolean adding;
    private EditBox target;
    private int left;
    private int top;
    private int panel;
    private String error = "";

    ChatRuleScreen(ProfitTrackerClient mod, Screen parent, ChatTitleRule rule, boolean adding) {
        super(Component.literal(adding ? "Add Custom Title" : "Edit Custom Title"));
        this.mod = mod;
        this.parent = parent;
        draft = rule.copy();
        this.adding = adding;
    }

    @Override
    protected void init() {
        panel = Math.min(420, width - 16);
        left = (width - panel) / 2;
        top = Math.max(5, (height - 230) / 2);
        target = addRenderableWidget(new EditBox(font, left, top + 43, panel, 20, Component.literal("Target chat message")));
        target.setMaxLength(512);
        target.setValue(draft.target);
        button("Pick recent message", left, top + 70, panel, () -> {
            capture();
            minecraft.gui.setScreen(new RecentMessagesScreen(mod, this, value -> draft.target = value));
        });
        button("Match: " + (draft.exact ? "Exact" : "Contains"), left, top + 98, panel / 2 - 4, () -> {
            capture(); draft.exact = !draft.exact; rebuildWidgets();
        });
        button("Ignore case: " + (draft.ignoreCase ? "ON" : "OFF"), left + panel / 2 + 4, top + 98, panel / 2 - 4, () -> {
            capture(); draft.ignoreCase = !draft.ignoreCase; rebuildWidgets();
        });
        button("Configure title and sound", left, top + 126, panel, () -> {
            capture();
            minecraft.gui.setScreen(new AlertEditorScreen(mod, this, draft.alert, style -> draft.alert = style));
        });
        button("Test alert", left, top + 154, panel / 2 - 4, () -> mod.previewAlert(draft.alert));
        button("Enabled: " + (draft.enabled ? "ON" : "OFF"), left + panel / 2 + 4, top + 154, panel / 2 - 4, () -> {
            capture(); draft.enabled = !draft.enabled; rebuildWidgets();
        });
        button("Save rule", left, top + 202, panel / 2 - 4, this::saveRule);
        button("Cancel", left + panel / 2 + 4, top + 202, panel / 2 - 4, this::onClose);
    }

    private void capture() { draft.target = target.getValue(); }

    private void saveRule() {
        capture();
        draft.sanitize();
        if (draft.target.isBlank()) { error = "Paste a chat message or choose a recent one."; return; }
        if (adding) {
            if (mod.settings().customTitles.size() >= 50) { error = "You can save up to 50 custom titles."; return; }
            mod.settings().customTitles.add(draft.copy());
        } else {
            var list = mod.settings().customTitles;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(draft.id)) { list.set(i, draft.copy()); break; }
            }
        }
        if (!mod.saveSettings()) ProfitTrackerClient.feedback("Custom title could not be saved. See the game log.");
        minecraft.gui.setScreen(parent);
    }

    private void button(String text, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), ignored -> action.run()).bounds(x, y, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top + 6, 0xFFFFD166);
        graphics.text(font, "Target chat message (Ctrl+V to paste)", left, top + 29, 0xFFB7C5D3);
        graphics.text(font, "Literal text; formatting is ignored. No action bars.", left, top + 181, 0xFFB7C5D3);
        if (!error.isEmpty()) graphics.centeredText(font, error, width / 2, top + 192, 0xFFFF7777);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
