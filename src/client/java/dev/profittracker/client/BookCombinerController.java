package dev.profittracker.client;

import dev.profittracker.BookCombiner;
import dev.profittracker.BookCombinerSettings;
import dev.profittracker.ChatTitleRule;
import dev.profittracker.client.mixin.ContainerScreenAccessor;
import java.util.ArrayList;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Only the open, explicitly learned chest-style menu can receive native shift-clicks. */
public final class BookCombinerController {
    private static BookCombinerController instance;
    private final ProfitTrackerClient mod;
    private final KeyMapping toggle;
    private ContainerScreen activeMenu;
    private BookCombiner combiner;
    private final ArrayList<BookCombiner.Cell> serverSlots = new ArrayList<>();
    private final ArrayList<Long> slotRevisions = new ArrayList<>();
    private long revision;
    private boolean serverCursorEmpty;
    private boolean refreshInventory;
    private boolean learning;
    private ContainerScreen learningMenu;
    private BookCombinerSettings draft;
    private int learnStep;
    private long learnDeadline;
    private String message = "";

    BookCombinerController(ProfitTrackerClient mod) {
        this.mod = mod;
        instance = this;
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("profittracker", "keys"));
        toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.profittracker.combine", GLFW.GLFW_KEY_K, category));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> attach(screen));
    }

    private static String server() {
        var current = Minecraft.getInstance().getCurrentServer();
        return current == null ? "singleplayer" : current.ip.toLowerCase(Locale.ROOT).trim();
    }

    private boolean matches(ContainerScreen screen) {
        var profile = mod.settings().books;
        return profile.configured() && profile.server.equals(server())
                && profile.rows == screen.getMenu().getRowCount()
                && profile.menuTitle.equals(ChatTitleRule.clean(screen.getTitle().getString()));
    }

    private void attach(Screen screen) {
        if (!(screen instanceof ContainerScreen menu)) return;
        boolean[] held = {false};
        ScreenKeyboardEvents.allowKeyPress(screen).register((current, key) -> {
            if (toggle.matches(key)) {
                if (held[0]) return false;
                held[0] = true;
                if (learning) cancelLearning("Slot selection cancelled");
                else if (running()) stop("Stopped");
                else start(menu);
                return false;
            }
            if (learning && key.key() != GLFW.GLFW_KEY_LEFT_SHIFT && key.key() != GLFW.GLFW_KEY_RIGHT_SHIFT) {
                cancelLearning("Slot selection cancelled");
            }
            if (running()) stop("Stopped by keyboard input");
            return true;
        });
        ScreenKeyboardEvents.afterKeyRelease(screen).register((current, key) -> {
            if (toggle.matches(key)) held[0] = false;
        });
        ScreenMouseEvents.allowMouseClick(screen).register((current, click) -> {
            if (learning) { select(menu, click); return false; }
            if (running()) { stop("Stopped by mouse input"); return false; }
            return true;
        });
        ScreenEvents.afterExtract(screen).register((current, graphics, x, y, delta) -> render(menu, graphics));
    }

    void learn() {
        stop("Stopped");
        draft = new BookCombinerSettings();
        draft.delayMs = mod.settings().books.delayMs;
        draft.maxLevel = mod.settings().books.maxLevel;
        learning = true;
        learningMenu = null;
        learnStep = 0;
        learnDeadline = ProfitTrackerClient.now() + 60_000;
        Minecraft.getInstance().gui.setScreen(null);
        ProfitTrackerClient.feedback("Open your empty Anvil menu, then select two inputs, the anvil button, the output, and a book. Setup clicks do not move items.");
    }

    private void select(ContainerScreen screen, MouseButtonEvent click) {
        if (screen != learningMenu || click.button() != 0) return;
        var access = (ContainerScreenAccessor) screen;
        var menu = screen.getMenu();
        for (var slot : menu.slots) {
            int x = access.tu$left() + slot.x, y = access.tu$top() + slot.y;
            if (click.x() < x || click.x() >= x + 16 || click.y() < y || click.y() >= y + 16) continue;
            var stack = slot.getItem();
            if (learnStep < 4) {
                if (slot.index >= draft.rows * 9) return;
                if ((learnStep > 0 && slot.index == draft.firstSlot)
                        || (learnStep > 1 && slot.index == draft.secondSlot)
                        || (learnStep > 2 && slot.index == draft.confirmSlot)) {
                    message = "Choose a different slot"; return;
                }
                if (BookItems.isBook(stack)) { message = "Remove books before learning empty slots"; return; }
                String idle = BookItems.cell(stack).idle();
                switch (learnStep) {
                    case 0 -> { draft.firstSlot = slot.index; draft.firstIdle = idle; }
                    case 1 -> { draft.secondSlot = slot.index; draft.secondIdle = idle; }
                    case 2 -> {
                        if (stack.isEmpty()) { message = "Select the visible anvil confirmation button"; return; }
                        draft.confirmSlot = slot.index;
                        draft.confirmItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    }
                    default -> { draft.outputSlot = slot.index; draft.outputIdle = idle; }
                }
                learnStep++;
                message = "";
                return;
            }
            if (slot.index < draft.rows * 9) return;
            var book = BookItems.book(stack);
            if (book == null || stack.getCount() != 1 || book.level() >= draft.maxLevel) {
                message = "Select one unstacked, single-enchantment book below tier " + draft.maxLevel; return;
            }
            draft.enchantment = book.enchantment();
            draft.inputLevel = book.level();
            mod.settings().books = draft;
            boolean saved = mod.saveSettings();
            learning = false;
            learningMenu = null;
            message = saved ? "Profile saved. Press " + keyName() + " to start." : "Profile active; could not save to disk.";
            ProfitTrackerClient.feedback(message);
            return;
        }
    }

    private void start(ContainerScreen screen) {
        if (!matches(screen)) {
            ProfitTrackerClient.feedback("Learn this server's Anvil slots and book first: /tu books"); return;
        }
        if (Minecraft.getInstance().player == null || !screen.getMenu().getCarried().isEmpty()) {
            ProfitTrackerClient.feedback("Clear the mouse cursor before starting"); return;
        }
        activeMenu = screen;
        serverSlots.clear();
        slotRevisions.clear();
        for (var slot : screen.getMenu().slots) { serverSlots.add(BookItems.cell(slot.getItem())); slotRevisions.add(0L); }
        revision = 0;
        serverCursorEmpty = true;
        refreshInventory = false;
        combiner = new BookCombiner(mod.settings().books, ProfitTrackerClient.now());
        message = "";
    }

    void tick() {
        var client = Minecraft.getInstance();
        var screen = client.gui.screen();
        if (learning) {
            if (ProfitTrackerClient.now() > learnDeadline) cancelLearning("Slot selection timed out");
            else if (learningMenu != null && screen != learningMenu) cancelLearning("Slot selection cancelled");
            else if (learningMenu == null && screen instanceof ContainerScreen menu) {
                learningMenu = menu;
                draft.server = server();
                draft.menuTitle = ChatTitleRule.clean(menu.getTitle().getString());
                draft.rows = menu.getMenu().getRowCount();
            }
        }
        if (!running()) return;
        if (client.player == null || client.gameMode == null || screen != activeMenu || !matches(activeMenu)
                || client.player.containerMenu != activeMenu.getMenu()) { stop("Menu closed or changed"); return; }
        if (refreshInventory) {
            refreshInventory = false;
            // Vanilla servers omit player-slot echoes when the native click prediction was correct.
            // Reconcile that view only after an authoritative input/output update, never on a timer alone.
            for (int i = activeMenu.getMenu().getRowCount() * 9; i < serverSlots.size(); i++) {
                serverSlots.set(i, BookItems.cell(activeMenu.getMenu().getSlot(i).getItem()));
                slotRevisions.set(i, revision);
            }
        }
        int previousCompleted = combiner.completed();
        int outputIndex = mod.settings().books.outputSlot;
        var localOutput = BookItems.cell(activeMenu.getMenu().getSlot(outputIndex).getItem());
        int click = combiner.tick(new BookCombiner.Snapshot(serverSlots, revision,
                serverCursorEmpty, slotRevisions, activeMenu.getMenu().getCarried().isEmpty(), localOutput), ProfitTrackerClient.now());
        // A completed cycle can acknowledge an omitted output echo through confirmed input clearing.
        if (combiner.completed() > previousCompleted) serverSlots.set(outputIndex, localOutput);
        if (click >= 0) client.gameMode.handleContainerInput(activeMenu.getMenu().containerId, click, 0,
                combiner.shiftClick() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, client.player);
        if (!combiner.running()) ProfitTrackerClient.feedback(combiner.status() + ". Combined: " + combiner.completed());
    }

    void stop(String reason) {
        if (combiner != null && combiner.running()) combiner.stop(reason);
    }
    void disconnect() {
        stop("Disconnected");
        learning = false;
        learningMenu = activeMenu = null;
        combiner = null;
        serverSlots.clear();
        slotRevisions.clear();
        message = "";
    }
    private void cancelLearning(String reason) {
        learning = false;
        learningMenu = null;
        message = reason;
    }
    boolean running() { return combiner != null && combiner.running(); }
    String keyName() { return toggle.getTranslatedKeyMessage().getString(); }

    private void render(ContainerScreen screen, GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        if (!learning && !matches(screen)) return;
        String label;
        if (learning) {
            String[] steps = {"Select FIRST input", "Select SECOND input", "Select ANVIL button", "Select OUTPUT", "Select a book in your inventory"};
            label = "Book setup: " + steps[learnStep] + " (Esc cancels)";
            var access = (ContainerScreenAccessor) screen;
            for (var slot : screen.getMenu().slots) {
                if (slot.index < screen.getMenu().getRowCount() * 9)
                    graphics.text(client.font, Integer.toString(slot.index), access.tu$left() + slot.x,
                            access.tu$top() + slot.y, 0xFFFFFF55);
            }
        } else if (running()) label = "Books: " + combiner.status() + " | " + combiner.completed() + " combined | " + keyName() + " to stop";
        else label = "Books: " + keyName() + " to start | max tier " + mod.settings().books.maxLevel;
        String line = client.font.plainSubstrByWidth(label, screen.width - 12);
        graphics.fill(3, 3, screen.width - 3, 28, 0xC0101720);
        graphics.text(client.font, line, 6, 6, 0xFF55FF55);
        String detail = !message.isEmpty() ? message : combiner != null && !running() ? combiner.status() : "";
        graphics.text(client.font, client.font.plainSubstrByWidth(detail, screen.width - 12), 6, 17, 0xFFFFD166);
    }

    /** Called only after vanilla has processed the packet on the Minecraft thread. */
    public static void serverSlot(ClientboundContainerSetSlotPacket packet) {
        var self = instance;
        if (self == null || !self.running() || self.activeMenu == null) return;
        int index = packet.getSlot();
        int menuId = self.activeMenu.getMenu().containerId;
        if (packet.getContainerId() == 0 && menuId != 0) {
            var player = Minecraft.getInstance().player;
            if (player == null || !player.inventoryMenu.isValidSlotIndex(index)) return;
            var inventorySlot = player.inventoryMenu.getSlot(index);
            var mapped = self.activeMenu.getMenu().findSlot(inventorySlot.container, inventorySlot.getContainerSlot());
            if (mapped.isEmpty()) return;
            index = mapped.getAsInt();
        } else if (packet.getContainerId() != menuId) return;
        self.update(index, packet.getItem());
    }

    private void update(int index, ItemStack stack) {
        if (index < 0 || index >= serverSlots.size()) return;
        serverSlots.set(index, BookItems.cell(stack));
        slotRevisions.set(index, ++revision);
        var profile = mod.settings().books;
        if (index == profile.firstSlot || index == profile.secondSlot || index == profile.outputSlot) refreshInventory = true;
    }

    public static void serverContents(ClientboundContainerSetContentPacket packet) {
        var self = instance;
        if (self == null || !self.running() || self.activeMenu == null) return;
        if (packet.containerId() == 0 && self.activeMenu.getMenu().containerId != 0) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            for (int i = 0; i < packet.items().size() && i < player.inventoryMenu.slots.size(); i++) {
                var inventorySlot = player.inventoryMenu.getSlot(i);
                var mapped = self.activeMenu.getMenu().findSlot(inventorySlot.container, inventorySlot.getContainerSlot());
                if (mapped.isPresent()) self.update(mapped.getAsInt(), packet.items().get(i));
            }
            return;
        }
        if (packet.containerId() != self.activeMenu.getMenu().containerId) return;
        self.serverCursorEmpty = packet.carriedItem().isEmpty();
        if (packet.items().size() != self.serverSlots.size()) { self.stop("Server changed the menu layout"); return; }
        for (int i = 0; i < packet.items().size(); i++) self.update(i, packet.items().get(i));
    }

    public static void serverInventory(int inventoryIndex, ItemStack item) {
        var self = instance;
        var player = Minecraft.getInstance().player;
        if (self == null || !self.running() || self.activeMenu == null || player == null) return;
        var mapped = self.activeMenu.getMenu().findSlot(player.getInventory(), inventoryIndex);
        if (mapped.isPresent()) self.update(mapped.getAsInt(), item);
    }

    public static void serverCursor(ItemStack item) {
        var self = instance;
        if (self != null && self.running()) self.serverCursorEmpty = item.isEmpty();
    }
}
