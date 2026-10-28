package dev.profittracker.client;

import dev.profittracker.CoalSession;
import dev.profittracker.CoalStock;
import dev.profittracker.SackParser;
import dev.profittracker.EndermiteCounter;
import dev.profittracker.SettingsStore;
import dev.profittracker.TrackerSettings;
import dev.profittracker.TrackerSettings.HudPosition;
import dev.profittracker.AlertStyle;
import dev.profittracker.ChatTitleRule;
import dev.profittracker.ChatTitleMatcher;
import dev.profittracker.TitleTimeline;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class ProfitTrackerClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("tgoodutils");
    private static final long CLOCK_ORIGIN = System.nanoTime();
    private static final Set<String> HOLLOWS_REGIONS = Set.of("Jungle", "Mithril Deposits", "Goblin Holdout",
            "Precursor Remnants", "Magma Fields", "Crystal Nucleus", "Fairy Grotto", "Jungle Temple",
            "Lost Precursor City", "Goblin Queen's Den", "Khazad-dûm");
    private static final Set<String> END_REGIONS = Set.of("The End", "Dragon's Nest", "Void Sepulture",
            "Zealot Bruiser Hideout");
    private final CoalSession session = new CoalSession();
    private final EndermiteCounter endermites = new EndermiteCounter();
    private final ChatTitleMatcher chatTitles = new ChatTitleMatcher();
    private final TitleTimeline titles = new TitleTimeline();
    private final ArrayDeque<String> recentChat = new ArrayDeque<>();
    private final BazaarClient bazaar = new BazaarClient();
    private final SettingsStore settingsStore = new SettingsStore(FabricLoader.getInstance().getConfigDir().resolve("profittracker.json"));
    private TrackerSettings settings = new TrackerSettings();
    private final LinkedHashMap<BlockPos, Long> targets = new LinkedHashMap<>();
    private boolean inHollows;
    private boolean inEnd;
    private int ticks;
    private Object lastLevel;
    private Screen pendingScreen;

    @Override
    public void onInitializeClient() {
        try { settings = settingsStore.load(); }
        catch (IOException ex) { LOGGER.warn("Could not load tracker settings; using defaults", ex); }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal("profit")
                        .executes(context -> feedback("/profit start | pause | reset"))
                        .then(literal("start").executes(context -> {
                            session.start(now(), inventory());
                            return feedback("Started. Allow 10s for old sack updates to drain; gains settle for 6s.");
                        }))
                        .then(literal("pause").executes(context -> {
                            session.pause(now(), inventory());
                            return feedback("Paused. Confirmed earnings retained.");
                        }))
                        .then(literal("reset").executes(context -> {
                            session.reset(now(), inventory());
                            return feedback("Session reset; " + (session.running() ? "running" : "paused") + ".");
                        }))));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal("tu")
                        .executes(context -> openSettings(0))
                        .then(literal("endermites").executes(context -> openSettings(1)))
                        .then(literal("titles").executes(context -> openSettings(2)))
                        .then(literal("hud").executes(context -> {
                            pendingScreen = new HudEditorScreen(this, null);
                            return 1;
                        }))));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // ALLOW_GAME observes the original server component even if another mod hides sack spam.
        // Do not listen to signed player chat: players must not be able to spoof sack gains.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && inHollows && Minecraft.getInstance().gui.screen() == null
                    && SackParser.clean(message.getString()).startsWith("[Sacks]")) {
                List<String> hovers = new ArrayList<>();
                collectHovers(message, hovers, new HashSet<>());
                SackParser.Result result = SackParser.parse(message.getString(), hovers);
                session.sackChange(now(), inventory(), result.delta(), result.complete());
            }
            if (!overlay) receiveChat(message.getString());
            return true;
        });
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, type, timestamp) -> {
            receiveChat(message.getString());
            return true;
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (!command.equals("profit") && !command.startsWith("profit ")) {
                session.interrupt(now(), inventory());
                targets.clear();
            }
            return true;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            session.pause(now(), inventory());
            targets.clear();
            inHollows = false;
            inEnd = false;
            endermites.clear();
            titles.clear();
            chatTitles.clear();
            recentChat.clear();
            pendingScreen = null;
            lastLevel = null;
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("profittracker", "hud"), (graphics, delta) -> render(graphics));
        // Draw after other HUD layers. Screen previews use the matching foreground hook.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("profittracker", "alerts"), (graphics, delta) -> {
            if (Minecraft.getInstance().gui.screen() == null) renderAlert(graphics);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterExtract(screen).register((current, graphics, x, y, delta) -> {
                    if (!(current instanceof TitlePositionScreen)) renderAlert(graphics);
                }));
    }

    private void tick(Minecraft client) {
        long time = now();
        if (client.level != lastLevel) {
            session.interrupt(time, inventory());
            targets.clear();
            inHollows = false;
            inEnd = false;
            endermites.clear();
            titles.clear();
            chatTitles.clear();
            lastLevel = client.level;
        }
        if (++ticks % 20 == 0) {
            inHollows = isCrystalHollows(client);
            inEnd = isTheEnd(client);
        }
        boolean allowed = inHollows && client.player != null && client.gui.screen() == null && !nearbyCoalDrops(client);
        boolean brokeCoal = false;
        if (allowed) {
            if (client.options.keyAttack.isDown() && client.hitResult instanceof BlockHitResult hit
                    && isCoal(client, hit.getBlockPos())) {
                targets.put(hit.getBlockPos().immutable(), time);
            }
            var iterator = targets.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (time - entry.getValue() > 2_000) iterator.remove();
                else if (client.level.getBlockState(entry.getKey()).isAir()) {
                    brokeCoal = true;
                    iterator.remove();
                }
            }
        } else targets.clear();
        session.tick(time, inventory(), allowed, brokeCoal);
        if (inHollows) bazaar.refresh(time);
        if (!settings.endermitesEnabled || !inEnd || client.player == null) {
            endermites.clear();
            titles.clearSource("endermites");
        } else if (ticks % 5 == 0) {
            var nearby = client.level.getEntitiesOfClass(Endermite.class,
                    client.player.getBoundingBox().inflate(settings.endermiteRange),
                    mite -> mite.isAlive() && !mite.isRemoved()
                            && mite.distanceToSqr(client.player) <= settings.endermiteRange * settings.endermiteRange);
            boolean crossed = endermites.update(nearby.stream().map(Endermite::getUUID).toList(), settings.endermiteTarget, time);
            if (crossed) triggerAlert(settings.endermiteAlert, settings.endermiteTitle, "endermites");
        }
        if (!settings.endermiteTitle) titles.clearSource("endermites");
        if (!settings.customTitlesEnabled) titles.clearSource("chat");
        // Chat closes its screen after running a command. Open ours on the following tick.
        if (pendingScreen != null) {
            client.gui.setScreen(pendingScreen);
            pendingScreen = null;
        }
    }

    private static boolean isCoal(Minecraft client, BlockPos pos) {
        var state = client.level.getBlockState(pos);
        return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE);
    }

    private static boolean isSkyBlock(Minecraft client) {
        if (client.level == null || client.player == null || client.getCurrentServer() == null) return false;
        String host = client.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":")[0];
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return false;
        var objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return objective != null && SackParser.clean(objective.getDisplayName().getString()).contains("SKYBLOCK");
    }

    private static boolean isCrystalHollows(Minecraft client) {
        if (!isSkyBlock(client)) return false;
        var scoreboard = client.level.getScoreboard();
        var objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null || !SackParser.clean(objective.getDisplayName().getString()).contains("SKYBLOCK")) return false;
        if (client.getConnection() != null) {
            for (var info : client.getConnection().getOnlinePlayers()) {
                var name = info.getTabListDisplayName();
                if (name != null && SackParser.clean(name.getString()).trim().equals("Area: Crystal Hollows")) return true;
            }
        }
        for (var score : scoreboard.listPlayerScores(objective)) {
            var team = scoreboard.getPlayersTeam(score.owner());
            String line = SackParser.clean(PlayerTeam.formatNameForTeam(team, Component.literal(score.owner())).getString());
            if (line.contains("Crystal Hollows")) return true;
            // The sidebar normally names a subregion rather than the whole island.
            int marker = line.indexOf('⏣');
            if (marker >= 0 && HOLLOWS_REGIONS.contains(line.substring(marker + 1).trim())) return true;
        }
        return false;
    }

    private static boolean isTheEnd(Minecraft client) {
        if (!isSkyBlock(client)) return false;
        if (client.getConnection() != null) {
            for (var info : client.getConnection().getOnlinePlayers()) {
                var name = info.getTabListDisplayName();
                if (name != null && SackParser.clean(name.getString()).trim().equals("Area: The End")) return true;
            }
        }
        var scoreboard = client.level.getScoreboard();
        var objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        for (var score : scoreboard.listPlayerScores(objective)) {
            var team = scoreboard.getPlayersTeam(score.owner());
            String line = SackParser.clean(PlayerTeam.formatNameForTeam(team, Component.literal(score.owner())).getString());
            int marker = line.indexOf('⏣');
            if (marker >= 0 && END_REGIONS.contains(line.substring(marker + 1).trim())) return true;
        }
        return false;
    }

    private static CoalStock inventory() {
        var player = Minecraft.getInstance().player;
        if (player == null) return CoalStock.ZERO;
        CoalStock stock = CoalStock.ZERO;
        // Player inventory only, never open chest/sack slots or a menu's carried cursor stack.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            stock = stock.plus(stockOf(stack));
        }
        return stock;
    }

    // Ground items have no trustworthy mining provenance. Skip them rather than count old drops.
    private static boolean nearbyCoalDrops(Minecraft client) {
        for (var item : client.level.getEntitiesOfClass(ItemEntity.class, client.player.getBoundingBox().inflate(8))) {
            if (stockOf(item.getItem()).units() > 0) return true;
        }
        return false;
    }

    private static CoalStock stockOf(ItemStack stack) {
        if (stack.isEmpty()) return CoalStock.ZERO;
        String id = "";
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var tag = data.copyTag();
            id = tag.getString("id").orElse("");
            if (id.isEmpty()) id = tag.getCompound("ExtraAttributes")
                    .flatMap(extra -> extra.getString("id")).orElse("");
        }
        int amount = stack.getCount();
        return switch (id) {
            case "COAL" -> new CoalStock(amount, 0, 0);
            case "COAL_BLOCK" -> new CoalStock(amount * 9L, 0, 0);
            case "ENCHANTED_COAL" -> new CoalStock(0, amount, 0);
            case "ENCHANTED_COAL_BLOCK" -> new CoalStock(0, 0, amount);
            default -> id.isEmpty() && stack.is(Items.COAL) ? new CoalStock(amount, 0, 0)
                    : id.isEmpty() && stack.is(Items.COAL_BLOCK) ? new CoalStock(amount * 9L, 0, 0) : CoalStock.ZERO;
        };
    }

    private static void collectHovers(Component component, List<String> output, Set<String> seen) {
        if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover) {
            String text = hover.value().getString();
            if (seen.add(text)) output.add(text);
        }
        for (Component sibling : component.getSiblings()) collectHovers(sibling, output, seen);
    }

    private void render(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null) return;
        if (inHollows && settings.coalHudEnabled) drawPanel(graphics, coalLines(), settings.coalPosition, false, false);
        if (inEnd && settings.endermitesEnabled) {
            drawPanel(graphics, endermiteLines(), settings.endermitePosition, endermites.atTarget(), false);
        }
    }

    List<String> coalLines() {
        var prices = bazaar.usable();
        String state = !session.running() ? "Paused: /profit start"
                : session.settling(now()) ? "Settling transfers (10s)" : "Running | observed gains only";
        double coins = prices == null ? 0 : prices.value(session.shares());
        double hours = session.elapsedMillis() / 3_600_000.0;
        long seconds = session.elapsedMillis() / 1000;
        return List.of("TgoodUtils | Coal", state,
                "Est. coins: " + (prices == null ? "unavailable" : number(coins)),
                "Est. coins/h: " + (prices == null || hours == 0 ? "--" : number(coins / hours)),
                "Coal: " + String.format(Locale.US, "%,d", session.coal()) + " (raw equivalent)",
                String.format(Locale.ROOT, "Session: %02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60),
                bazaar.status());
    }

    List<String> endermiteLines() {
        String total = settings.endermiteTarget == 0 ? Integer.toString(endermites.count())
                : endermites.count() + " / " + settings.endermiteTarget;
        return List.of("Endermites: " + total);
    }

    int panelWidth(List<String> lines, int screenWidth) {
        return Math.min(screenWidth, lines.stream().mapToInt(Minecraft.getInstance().font::width).max().orElse(160) + 12);
    }

    int panelHeight(List<String> lines) { return lines.size() * 12 + 8; }

    void drawPanel(GuiGraphicsExtractor graphics, List<String> lines, HudPosition position, boolean green, boolean editing) {
        var client = Minecraft.getInstance();
        int width = panelWidth(lines, graphics.guiWidth());
        int height = panelHeight(lines);
        int x = position.left(graphics.guiWidth(), width);
        int y = position.top(graphics.guiHeight(), height);
        graphics.fill(x, y, x + width, y + height, editing ? 0xD010151C : 0xA010151C);
        if (editing) {
            graphics.fill(x, y, x + width, y + 1, 0xFF69B8F0);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xFF69B8F0);
            graphics.fill(x, y, x + 1, y + height, 0xFF69B8F0);
            graphics.fill(x + width - 1, y, x + width, y + height, 0xFF69B8F0);
        }
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(client.font, client.font.plainSubstrByWidth(lines.get(i), Math.max(0, width - 12)), x + 6, y + 5 + i * 12,
                    green ? 0xFF69F09C : i == 0 ? 0xFFFFD166 : i == lines.size() - 1 ? 0xFFB7C5D3 : 0xFFF3F6FA);
        }
    }

    TrackerSettings settings() { return settings; }
    boolean coalRunning() { return session.running(); }
    boolean endermitesAtTarget() { return endermites.atTarget(); }

    private void receiveChat(String raw) {
        if (Minecraft.getInstance().level == null) return;
        String message = ChatTitleRule.clean(raw);
        if (message.isBlank() || message.startsWith("[TgoodUtils]")) return;
        String recent = message.length() > 512 ? message.substring(0, 512) : message;
        if (!recent.equals(recentChat.peekFirst())) recentChat.addFirst(recent);
        while (recentChat.size() > 30) recentChat.removeLast();
        if (settings.customTitlesEnabled) chatTitles.match(settings.customTitles, message, now())
                .ifPresent(rule -> triggerAlert(rule.alert, true, "chat"));
    }

    List<String> recentMessages() { return List.copyOf(recentChat); }

    private void triggerAlert(AlertStyle style, boolean showTitle, String source) {
        if (showTitle) titles.show(style, source, now());
        if (style.soundEnabled && style.volume > 0) {
            var sound = switch (style.sound) {
                case DING -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                case BELL -> SoundEvents.BELL_BLOCK;
                case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            };
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, (float) style.volume));
        }
    }

    void previewAlert(AlertStyle style) { triggerAlert(style, true, "preview"); }
    void clearAlert() { titles.clear(); }

    private void renderAlert(GuiGraphicsExtractor graphics) {
        if (Minecraft.getInstance().gui.hud.isHidden()) return;
        var active = titles.current(now());
        if (active != null) AlertRenderer.draw(graphics, active);
    }

    void toggleCoal() {
        if (session.running()) session.pause(now(), inventory());
        else session.start(now(), inventory());
    }

    void resetCoal() { session.reset(now(), inventory()); }

    boolean saveSettings() {
        try { settingsStore.save(settings); return true; }
        catch (IOException ex) { LOGGER.error("Could not save tracker settings", ex); return false; }
    }

    private int openSettings(int tab) {
        pendingScreen = new TrackerScreen(this, tab);
        return 1;
    }

    private static String number(double value) { return String.format(Locale.US, "%,.0f", value); }
    private static long now() { return (System.nanoTime() - CLOCK_ORIGIN) / 1_000_000; }
    static int feedback(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[TgoodUtils] " + message));
        return 1;
    }
}
