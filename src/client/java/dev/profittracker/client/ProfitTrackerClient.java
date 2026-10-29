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
import dev.profittracker.GemMessages;
import dev.profittracker.GemSession;
import dev.profittracker.GemSackParser;
import dev.profittracker.GemHud;
import dev.profittracker.CompactNumbers;
import dev.profittracker.EndermiteSession;
import dev.profittracker.BestiaryTotal;
import dev.profittracker.PrivateApiKeyStore;
import dev.profittracker.VisitorTimer;
import dev.profittracker.AlertBurst;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class ProfitTrackerClient implements ClientModInitializer {
    private static ProfitTrackerClient instance;
    private static final Logger LOGGER = LoggerFactory.getLogger("tgoodutils");
    private static final long CLOCK_ORIGIN = System.nanoTime();
    private static final Set<String> HOLLOWS_REGIONS = Set.of("Jungle", "Mithril Deposits", "Goblin Holdout",
            "Precursor Remnants", "Magma Fields", "Crystal Nucleus", "Fairy Grotto", "Jungle Temple",
            "Lost Precursor City", "Goblin Queen's Den", "Khazad-dûm");
    private static final Set<String> END_REGIONS = Set.of("The End", "Dragon's Nest", "Void Sepulture",
            "Zealot Bruiser Hideout");
    private final CoalSession session = new CoalSession();
    private final GemSession gemstones = new GemSession();
    private final EndermiteCounter endermites = new EndermiteCounter();
    private final EndermiteSession endermiteSession = new EndermiteSession();
    private BestiaryClient bestiary;
    private VisitorTimer visitors;
    private final AlertBurst visitorBurst = new AlertBurst();
    private final TitleTimeline visitorTitles = new TitleTimeline();
    private boolean visitorPreview, forceBestiary;
    private AlertStyle visitorPreviewStyle;
    private List<String> visitorHudText;
    private long nextVisitorText;
    private boolean inGarden, wasGarden;
    private String profileName = "", bestiaryKey = "";
    private long appliedBestiary, nextEndermiteText;
    private int lastSettingsTab;
    private final java.util.concurrent.ExecutorService settingsWorker = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "TgoodUtils-Settings"); thread.setDaemon(true); return thread;
    });
    private java.util.concurrent.CompletableFuture<Void> latestSave;
    private final ChatTitleMatcher chatTitles = new ChatTitleMatcher();
    private final TitleTimeline titles = new TitleTimeline();
    private final ArrayDeque<String> recentChat = new ArrayDeque<>();
    private final BazaarClient bazaar = new BazaarClient();
    private final SettingsStore settingsStore = new SettingsStore(FabricLoader.getInstance().getConfigDir().resolve("profittracker.json"));
    private TrackerSettings settings = new TrackerSettings();
    private final LinkedHashMap<BlockPos, Long> targets = new LinkedHashMap<>();
    private boolean inHollows;
    private boolean inEnd;
    private boolean inSkyBlock;
    private int ticks;
    private Object lastLevel;
    private Screen pendingScreen;
    private Screen lastGemScreen;
    private List<String> coalHudText;
    private long nextCoalHudText;
    private List<String> gemHudText;
    private long nextGemHudText;
    private List<String> endermiteHudText;
    private int displayedEndermites = -1;
    private int displayedTarget = -1;
    private BookCombinerController books;
    private final Set<Screen> alertScreens = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onInitializeClient() {
        instance = this;
        try { settings = settingsStore.load(); }
        catch (IOException ex) { LOGGER.warn("Could not load tracker settings; using defaults", ex); }
        bestiary = new BestiaryClient(new PrivateApiKeyStore(FabricLoader.getInstance().getConfigDir().resolve("tgoodutils-private.json")));
        visitors = new VisitorTimer(settings.visitors);
        books = new BookCombinerController(this);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            var client = Minecraft.getInstance();
            if (player == client.player && entity instanceof Endermite mite && trackMite(mite)) endermiteSession.hit(mite.getUUID(), now());
            return InteractionResult.PASS;
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (settings.visitors.enabled && settings.visitors.autoGarden && settings.visitors.armed) visitors.resume(System.currentTimeMillis());
            saveLater(); bestiary.close(); settingsWorker.shutdown();
            try { if (latestSave != null) latestSave.get(2, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception ignored) { LOGGER.warn("Settings save did not finish before shutdown"); }
        });
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
                        .executes(context -> openSettings(lastSettingsTab))
                        .then(literal("endermites").executes(context -> openSettings(1)))
                        .then(literal("titles").executes(context -> openSettings(2)))
                        .then(literal("books").executes(context -> openSettings(4)))
                        .then(literal("gems").executes(context -> openSettings(5)))
                        .then(literal("gemstones").executes(context -> openSettings(5)))
                        .then(literal("visitors").executes(context -> openSettings(6)))
                        .then(literal("api").executes(context -> { pendingScreen = new ApiKeyScreen(this, null); return 1; }))
                        .then(literal("hud").executes(context -> {
                            pendingScreen = new HudEditorScreen(this, null);
                            return 1;
                        }))));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // ALLOW_GAME observes the original server component even if another mod hides sack spam.
        // Do not listen to signed player chat: players must not be able to spoof sack gains.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            observeGemMenu(now());
            if (!overlay && inSkyBlock && gemstones.running()) {
                GemMessages.pristine(message.getString()).ifPresent(gain -> {
                    if (gemstones.pristine(gain, now())) nextGemHudText = 0;
                });
            }
            if (!overlay && inSkyBlock && (inHollows || gemstones.running()) && Minecraft.getInstance().gui.screen() == null
                    && SackParser.clean(message.getString()).startsWith("[Sacks]")) {
                List<String> hovers = new ArrayList<>();
                collectHovers(message, hovers, new HashSet<>());
                if (inHollows) {
                    SackParser.Result result = SackParser.parse(message.getString(), hovers);
                    session.sackChange(now(), inventory(), result.delta(), result.complete());
                }
                if (gemstones.rough(GemSackParser.rough(message.getString(), hovers), now())) nextGemHudText = 0;
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
                gemstones.interrupt(now());
            }
            return true;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (settings.visitors.enabled && settings.visitors.autoGarden && settings.visitors.armed) visitors.resume(System.currentTimeMillis());
            saveLater(); bestiary.disconnect(); bestiaryKey = ""; profileName = ""; inGarden = wasGarden = false;
            endermiteSession.clearEvidence(); visitorBurst.clear(); visitorTitles.clear();
            session.pause(now(), inventory());
            targets.clear();
            inHollows = false;
            inEnd = false;
            inSkyBlock = false;
            gemstones.pause(now());
            endermites.clear();
            titles.clear();
            chatTitles.clear();
            recentChat.clear();
            pendingScreen = null;
            lastLevel = null;
            lastGemScreen = null;
            books.disconnect();
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("profittracker", "hud"), (graphics, delta) -> render(graphics));
        // Draw after other HUD layers. Screen previews use the matching foreground hook.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("profittracker", "alerts"), (graphics, delta) -> {
            if (Minecraft.getInstance().gui.screen() == null) renderAlert(graphics);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            // Fabric resets per-screen events on init/resize. Inactive titles need no callbacks.
            alertScreens.remove(screen);
            if (titles.current(now()) != null || visitorBurst.active()) attachAlert(screen);
            if (screen instanceof InventoryScreen && inSkyBlock
                    && (settings.gems.hudEnabled || gemstones.running() || gemstones.procs() > 0 || gemstones.glossy() > 0)) {
                var reset = Button.builder(Component.literal("Reset gem timer"), ignored -> {
                    gemstones.resetRate(now());
                    nextGemHudText = 0;
                    feedback("Gemstone coins/h window reset. Session totals kept.");
                }).bounds(6, Math.max(4, height - 26), Math.min(140, width - 12), 20).build();
                reset.setTooltip(Tooltip.create(Component.literal("Reset coins/h earnings and time; keep total gemstones and coins.")));
                Screens.getWidgets(screen).add(reset);
            }
        });
    }

    private void tick(Minecraft client) {
        long time = now();
        observeGemMenu(time);
        if (client.level != lastLevel) {
            session.interrupt(time, inventory());
            targets.clear();
            inHollows = false;
            inEnd = false;
            inSkyBlock = false;
            gemstones.interrupt(time);
            endermites.clear();
            endermiteSession.clearEvidence();
            titles.clear();
            chatTitles.clear();
            books.disconnect();
            lastLevel = client.level;
        }
        if (++ticks % 20 == 0) {
            inSkyBlock = isSkyBlock(client);
            inHollows = isCrystalHollows(client);
            inEnd = isTheEnd(client);
            inGarden = isGarden(client);
            String nextProfile = readProfile(client);
            if (!nextProfile.isEmpty() && !nextProfile.equals(profileName)) {
                if (!profileName.isEmpty()) endermiteSession.reset(time);
                profileName = nextProfile; bestiaryKey = "";
            }
            if (inSkyBlock && client.player != null && (inEnd && settings.endermitesEnabled || forceBestiary)) {
                boolean started = bestiary.refresh(client.player.getUUID().toString(), profileName, time);
                if (started || !bestiary.hasKey()) forceBestiary = false;
            }
            applyBestiary(client);
            tickVisitors(client, time);
        }
        endermiteSession.tick(time, settings.endermitesEnabled && inEnd && client.player != null);
        if (visitorBurst.tick(time)) {
            var pulse = (visitorPreview && visitorPreviewStyle != null ? visitorPreviewStyle : settings.visitors.alert).copy(); pulse.seconds = 0.65;
            visitorTitles.show(pulse, "visitors", time); attachAlert(client.gui.screen());
            playAlertSound(pulse);
        }
        if (visitorBurst.finished()) {
            if (!visitorPreview) { settings.visitors.notified = true; saveLater(); }
            visitorBurst.clear(); visitorPreview = false; visitorPreviewStyle = null;
        }
        boolean allowed = session.running() && inHollows && client.player != null
                && client.gui.screen() == null && !nearbyCoalDrops(client);
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
        // Inactive sessions do not need inventory baselines; the quarantine rebaselines on resume.
        session.tick(time, allowed ? inventory() : CoalStock.ZERO, allowed, brokeCoal);
        boolean gemAllowed = inSkyBlock && client.player != null
                && (!settings.gems.miningTimeOnly || client.gui.screen() == null);
        boolean miningGem = gemstones.running() && inSkyBlock && client.player != null && client.gui.screen() == null && client.options.keyAttack.isDown()
                && client.hitResult instanceof BlockHitResult hit && isGemstone(client, hit.getBlockPos());
        gemstones.tick(time, gemAllowed, miningGem, settings.gems.miningTimeOnly);
        if (inHollows || (inSkyBlock && gemstones.running())) bazaar.refresh(time, gemstones.running());
        if (!settings.endermitesEnabled || !inEnd || client.player == null) {
            endermites.clear();
            titles.clearSource("endermites");
        } else if (ticks % 5 == 0) {
            var nearby = client.level.getEntitiesOfClass(Endermite.class,
                    client.player.getBoundingBox().inflate(settings.endermiteRange),
                    mite -> !mite.isRemoved()
                            && mite.distanceToSqr(client.player) <= settings.endermiteRange * settings.endermiteRange);
            for (var mite : nearby) if (mite.deathTime > 0 || mite.getHealth() <= 0) recordMiteDeath(mite);
            boolean crossed = endermites.update(nearby.stream().filter(Endermite::isAlive).map(Endermite::getUUID).toList(), settings.endermiteTarget, time);
            if (crossed) triggerAlert(settings.endermiteAlert, settings.endermiteTitle, "endermites");
        }
        if (!settings.endermiteTitle) titles.clearSource("endermites");
        if (!settings.customTitlesEnabled) titles.clearSource("chat");
        // Chat closes its screen after running a command. Open ours on the following tick.
        if (pendingScreen != null) {
            client.gui.setScreen(pendingScreen);
            pendingScreen = null;
        }
        books.tick();
    }

    BookCombinerController books() { return books; }
    GemSession gemstones() { return gemstones; }
    VisitorTimer visitors() { return visitors; }
    BestiaryClient bestiary() { return bestiary; }
    void rememberTab(int tab) { lastSettingsTab = tab; }
    void resetEndermiteSession() { endermiteSession.reset(now()); nextEndermiteText = 0; }
    void syncBestiaryNow() { bestiary.requestNow(); forceBestiary = true; }

    private boolean trackMite(Endermite mite) {
        var client = Minecraft.getInstance();
        return settings.endermitesEnabled && inEnd && client.player != null && mite.distanceToSqr(client.player) <= settings.endermiteRange * settings.endermiteRange;
    }
    public static void endermiteDamage(ClientboundDamageEventPacket packet) {
        var client = Minecraft.getInstance(); var mod = instance;
        if (mod == null || client.level == null || !(client.level.getEntity(packet.entityId()) instanceof Endermite mite) || !mod.trackMite(mite)) return;
        var cause = packet.getSource(client.level).getEntity();
        if (cause != null) mod.endermiteSession.damage(mite.getUUID(), cause == client.player, now());
    }
    public static void endermiteEvent(ClientboundEntityEventPacket packet) {
        var client = Minecraft.getInstance(); var mod = instance;
        // Vanilla's death status, never the generic hurt status or entity removal packet.
        if (packet.getEventId() == 3 && mod != null && client.level != null && packet.getEntity(client.level) instanceof Endermite mite) mod.recordMiteDeath(mite);
    }
    private void recordMiteDeath(Endermite mite) {
        if (!trackMite(mite) || !endermiteSession.death(mite.getUUID(), now(), settings.endermiteNearbyKills)) return;
        var total = currentBestiary();
        if (total != null && endermiteSession.personalDeath()) total.kill();
        nextEndermiteText = 0;
    }

    private void applyBestiary(Minecraft client) {
        var result = bestiary.snapshot();
        if (result == null || result.sequence() == appliedBestiary || client.player == null || !client.player.getUUID().toString().equals(result.player())) return;
        if (!profileName.isEmpty() && !profileName.equalsIgnoreCase(result.data().profileName())) return;
        appliedBestiary = result.sequence();
        bestiaryKey = result.player() + "|" + result.data().profileId();
        var total = settings.bestiary.computeIfAbsent(bestiaryKey, ignored -> new BestiaryTotal());
        if (total.update(result.data(), System.currentTimeMillis())) saveLater();
        nextEndermiteText = 0;
    }
    private BestiaryTotal currentBestiary() {
        if (bestiaryKey.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null && !profileName.isEmpty()) for (var entry : settings.bestiary.entrySet()) {
                if (entry.getKey().startsWith(player.getUUID() + "|") && entry.getValue().profileName.equalsIgnoreCase(profileName)) { bestiaryKey = entry.getKey(); break; }
            }
        }
        return settings.bestiary.get(bestiaryKey);
    }
    private static String readProfile(Minecraft client) {
        if (client.getConnection() != null) for (var entry : client.getConnection().getOnlinePlayers()) {
            var text = entry.getTabListDisplayName();
            if (text == null) continue;
            String line = ChatTitleRule.clean(text.getString());
            if (line.startsWith("Profile: ")) return line.substring(9).trim();
        }
        return "";
    }
    private static boolean isGarden(Minecraft client) {
        if (!isSkyBlock(client)) return false;
        if (client.getConnection() != null) for (var entry : client.getConnection().getOnlinePlayers()) {
            var text = entry.getTabListDisplayName();
            if (text != null && java.util.Set.of("Area: Garden", "Area: The Garden").contains(ChatTitleRule.clean(text.getString()))) return true;
        }
        var board = client.level.getScoreboard(); var objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective != null) for (var score : board.listPlayerScores(objective)) {
            String line = ChatTitleRule.clean(PlayerTeam.formatNameForTeam(board.getPlayersTeam(score.owner()), Component.literal(score.owner())).getString());
            if (line.contains("⏣ Garden") || line.contains("⏣ The Garden")) return true;
        }
        return false;
    }

    private String visitorProfile() { var player = Minecraft.getInstance().player; return player == null ? "" : player.getUUID() + "|" + profileName; }
    private boolean visitorProfileMatches() {
        String current = visitorProfile(); var v = settings.visitors;
        if (v.profile.isEmpty()) return true;
        if (v.profile.endsWith("|") && current.startsWith(v.profile)) { v.profile = current; saveLater(); }
        return v.profile.equals(current);
    }
    private void tickVisitors(Minecraft client, long time) {
        var v = settings.visitors; long epoch = System.currentTimeMillis();
        if (!v.enabled || !inSkyBlock || !visitorProfileMatches()) return;
        boolean changed = false;
        if (v.autoGarden) {
            if (inGarden) changed = visitors.pause(epoch);
            else if (wasGarden || v.armed) {
                if (v.profile.isEmpty()) v.profile = visitorProfile();
                v.armed = true; changed = visitors.resume(epoch);
            }
        }
        wasGarden = inGarden;
        if (visitors.ready(epoch) && !visitorBurst.active()) {
            if (v.alertEnabled) { visitorPreview = false; visitorBurst.start(time); }
            else { v.notified = true; changed = true; }
        }
        if (changed) saveLater();
    }
    void toggleVisitors() {
        var v = settings.visitors; v.enabled = !v.enabled;
        if (!v.enabled) { visitors.pause(System.currentTimeMillis()); visitorBurst.clear(); visitorTitles.clear(); }
    }
    void reconfigureVisitors(int capacity, int seconds) { visitors.reconfigure(capacity, seconds, System.currentTimeMillis()); nextVisitorText = 0; }
    void correctVisitors(int count) {
        settings.visitors.profile = visitorProfile(); visitors.correct(count, System.currentTimeMillis());
        if (settings.visitors.autoGarden && inGarden) visitors.pause(System.currentTimeMillis());
        visitorBurst.clear(); visitorTitles.clear();
        nextVisitorText = 0;
    }
    void toggleVisitorClock() {
        settings.visitors.profile = visitorProfile();
        if (settings.visitors.running) { visitors.pause(System.currentTimeMillis()); settings.visitors.armed = false; }
        else visitors.resume(System.currentTimeMillis());
        nextVisitorText = 0;
    }
    void previewVisitors() { previewVisitors(settings.visitors.alert); }
    void previewVisitors(AlertStyle style) { visitorPreview = true; visitorPreviewStyle = style.copy(); visitorBurst.start(now()); }

    private void observeGemMenu(long time) {
        Screen current = Minecraft.getInstance().gui.screen();
        if (current != lastGemScreen) {
            gemstones.quarantineSacks(time);
            lastGemScreen = current;
        }
    }

    private static boolean isGemstone(Minecraft client, BlockPos position) {
        if (client.level == null) return false;
        var block = client.level.getBlockState(position).getBlock();
        return block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock;
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
        // Hypixel's item metadata confirms all four supported coal forms use these two materials.
        // Never deep-copy unrelated gear/bag NBT just to discover it is not coal.
        if (stack.isEmpty() || (!stack.is(Items.COAL) && !stack.is(Items.COAL_BLOCK))) return CoalStock.ZERO;
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
        if (inSkyBlock && settings.visitors.enabled && settings.visitors.hudEnabled && visitorProfileMatches()) {
            drawPanel(graphics, visitorLines(), settings.visitorPosition, visitors.count(System.currentTimeMillis()) >= settings.visitors.capacity, false);
        }
        if (inSkyBlock && settings.gems.hudEnabled && (gemstones.running() || gemstones.procs() > 0 || gemstones.glossy() > 0)) {
            drawGemPanel(graphics, false);
        }
    }

    List<String> gemLines() {
        long time = now();
        if (gemHudText != null && time < nextGemHudText) return gemHudText;
        nextGemHudText = time + 250;
        gemHudText = GemHud.lines(gemstones, bazaar.gems(), settings.gems);
        return gemHudText;
    }

    void drawGemPanel(GuiGraphicsExtractor graphics, boolean editing) {
        drawPanel(graphics, gemLines(), settings.gemPosition, false, editing, true);
    }

    List<String> coalLines() {
        long time = now();
        if (coalHudText != null && time < nextCoalHudText) return coalHudText;
        nextCoalHudText = time + 250;
        var prices = bazaar.usable();
        String state = !session.running() ? "Paused: /profit start"
                : session.settling(now()) ? "Settling transfers (10s)" : "Running | observed gains only";
        double coins = prices == null ? 0 : prices.value(session.shares());
        double hours = session.elapsedMillis() / 3_600_000.0;
        long seconds = session.elapsedMillis() / 1000;
        coalHudText = List.of("TgoodUtils | Coal", state,
                "Est. coins: " + (prices == null ? "unavailable" : number(coins)),
                "Est. coins/h: " + (prices == null || hours == 0 ? "--" : number(coins / hours)),
                "Coal: " + CompactNumbers.format(session.coal()) + " (raw equivalent)",
                String.format(Locale.ROOT, "Session: %02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60),
                bazaar.status());
        return coalHudText;
    }

    List<String> endermiteLines() {
        if (endermiteHudText != null && now() < nextEndermiteText && displayedEndermites == endermites.count() && displayedTarget == settings.endermiteTarget) return endermiteHudText;
        nextEndermiteText = now() + 1_000;
        displayedEndermites = endermites.count();
        displayedTarget = settings.endermiteTarget;
        String total = settings.endermiteTarget == 0 ? Integer.toString(endermites.count())
                : endermites.count() + " / " + settings.endermiteTarget;
        var bestiary = currentBestiary();
        String lifetime = bestiary == null || bestiary.total() < 0 ? "--" : "~" + CompactNumbers.format(bestiary.total());
        endermiteHudText = List.of("Endermites: " + total, "Kills/h: " + endermiteSession.rate() + " | Total: " + lifetime,
                "Time: " + CompactNumbers.shortTime(endermiteSession.elapsedMillis()));
        return endermiteHudText;
    }
    List<String> visitorLines() {
        if (visitorHudText == null || now() >= nextVisitorText) {
            nextVisitorText = now() + 1_000; visitorHudText = List.of(visitors.line(System.currentTimeMillis()));
        }
        return visitorHudText;
    }

    int panelWidth(List<String> lines, int screenWidth) {
        return Math.min(screenWidth, lines.stream().mapToInt(Minecraft.getInstance().font::width).max().orElse(160) + 12);
    }

    int panelHeight(List<String> lines) { return lines.size() * 12 + 8; }

    void drawPanel(GuiGraphicsExtractor graphics, List<String> lines, HudPosition position, boolean green, boolean editing) {
        drawPanel(graphics, lines, position, green, editing, false);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, List<String> lines, HudPosition position, boolean green, boolean editing, boolean gem) {
        var client = Minecraft.getInstance();
        int width = panelWidth(lines, graphics.guiWidth());
        int height = panelHeight(lines);
        int x = position.left(graphics.guiWidth(), width);
        int y = position.top(graphics.guiHeight(), height);
        graphics.fill(x, y, x + width, y + height, editing ? 0xD010151C : 0xA010151C);
        if (gem) graphics.fill(x, y, x + width, y + 1, 0xFF69B8F0);
        if (editing) {
            graphics.fill(x, y, x + width, y + 1, 0xFF69B8F0);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xFF69B8F0);
            graphics.fill(x, y, x + 1, y + height, 0xFF69B8F0);
            graphics.fill(x + width - 1, y, x + width, y + height, 0xFF69B8F0);
        }
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(client.font, client.font.plainSubstrByWidth(lines.get(i), Math.max(0, width - 12)), x + 6, y + 5 + i * 12,
                    green || gem && i == 1 ? 0xFF69F09C : i == 0 ? (gem ? 0xFF69B8F0 : 0xFFFFD166)
                            : i == lines.size() - 1 || gem ? 0xFFB7C5D3 : 0xFFF3F6FA);
        }
    }

    TrackerSettings settings() { return settings; }
    boolean coalRunning() { return session.running(); }
    boolean endermitesAtTarget() { return endermites.atTarget(); }

    private void receiveChat(String raw) {
        if (Minecraft.getInstance().level == null) return;
        String message = ChatTitleRule.clean(raw);
        if (message.isBlank() || message.startsWith("[TgoodUtils]")) return;
        rememberChat(message);
        if (settings.customTitlesEnabled) chatTitles.match(settings.customTitles, message, now())
                .ifPresent(rule -> triggerAlert(rule.alert, true, "chat"));
    }

    private void rememberChat(String message) {
        String recent = message.length() > 512 ? message.substring(0, 512) : message;
        if (!recent.equals(recentChat.peekFirst())) recentChat.addFirst(recent);
        while (recentChat.size() > 30) recentChat.removeLast();
    }

    /** Other mods' local notifications are not delivered through the server-message event. */
    public static void systemGemNotification(Component component) {
        var client = Minecraft.getInstance();
        if (!client.isSameThread()) { client.execute(() -> systemGemNotification(component)); return; }
        var mod = instance;
        if (mod == null || !mod.inSkyBlock || !mod.settings.gems.includeGlossy) return;
        mod.observeGemMenu(now());
        String text = ChatTitleRule.clean(component.getString());
        if (!text.toLowerCase(Locale.ROOT).contains("glossy gemstone")) return;
        mod.rememberChat(text);
        if (Minecraft.getInstance().gui.screen() != null || !mod.gemstones.running()) return;
        var amount = GemMessages.glossy(text, mod.settings.gems.glossyFormat);
        if (amount.isPresent() && mod.gemstones.glossy(amount.getAsLong(), now())) mod.nextGemHudText = 0;
    }

    List<String> recentMessages() { return List.copyOf(recentChat); }

    private void triggerAlert(AlertStyle style, boolean showTitle, String source) {
        if (showTitle) {
            titles.show(style, source, now());
            attachAlert(Minecraft.getInstance().gui.screen());
        }
        playAlertSound(style);
    }

    private static void playAlertSound(AlertStyle style) {
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

    private void attachAlert(Screen screen) {
        if (screen == null || screen instanceof TitlePositionScreen || !alertScreens.add(screen)) return;
        ScreenEvents.afterExtract(screen).register((current, graphics, x, y, delta) -> renderAlert(graphics));
    }

    private void renderAlert(GuiGraphicsExtractor graphics) {
        if (Minecraft.getInstance().gui.hud.isHidden()) return;
        var active = visitorBurst.active() ? visitorTitles.current(now()) : titles.current(now());
        if (active != null) AlertRenderer.draw(graphics, active);
    }

    void toggleCoal() {
        if (session.running()) session.pause(now(), inventory());
        else session.start(now(), inventory());
    }

    void resetCoal() { session.reset(now(), inventory()); }
    void toggleGems() {
        if (gemstones.running()) gemstones.pause(now());
        else gemstones.start(now());
        nextGemHudText = 0;
    }

    boolean saveSettings() {
        try { settingsStore.save(settings); return true; }
        catch (IOException ex) { LOGGER.error("Could not save tracker settings", ex); return false; }
    }
    private void saveLater() {
        if (settingsWorker.isShutdown()) return;
        latestSave = settingsStore.saveAsync(settings, settingsWorker);
        latestSave.exceptionally(error -> { LOGGER.warn("Could not save tracker state"); return null; });
    }

    private int openSettings(int tab) {
        pendingScreen = new TrackerScreen(this, tab);
        return 1;
    }

    private static String number(double value) { return CompactNumbers.format(value); }
    static long now() { return (System.nanoTime() - CLOCK_ORIGIN) / 1_000_000; }
    static int feedback(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[TgoodUtils] " + message));
        return 1;
    }
}
