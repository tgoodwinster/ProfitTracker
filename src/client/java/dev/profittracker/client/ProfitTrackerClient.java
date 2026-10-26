package dev.profittracker.client;

import dev.profittracker.CoalSession;
import dev.profittracker.CoalStock;
import dev.profittracker.SackParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class ProfitTrackerClient implements ClientModInitializer {
    private static final Set<String> HOLLOWS_REGIONS = Set.of("Jungle", "Mithril Deposits", "Goblin Holdout",
            "Precursor Remnants", "Magma Fields", "Crystal Nucleus", "Fairy Grotto", "Jungle Temple",
            "Lost Precursor City", "Goblin Queen's Den", "Khazad-dûm");
    private final CoalSession session = new CoalSession();
    private final BazaarClient bazaar = new BazaarClient();
    private final LinkedHashMap<BlockPos, Long> targets = new LinkedHashMap<>();
    private boolean inHollows;
    private int ticks;
    private Object lastLevel;

    @Override
    public void onInitializeClient() {
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
            lastLevel = null;
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("profittracker", "hud"), (graphics, delta) -> render(graphics));
    }

    private void tick(Minecraft client) {
        long time = now();
        if (client.level != lastLevel) {
            session.interrupt(time, inventory());
            targets.clear();
            inHollows = false;
            lastLevel = client.level;
        }
        if (++ticks % 20 == 0) inHollows = isCrystalHollows(client);
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
    }

    private static boolean isCoal(Minecraft client, BlockPos pos) {
        var state = client.level.getBlockState(pos);
        return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE);
    }

    private static boolean isCrystalHollows(Minecraft client) {
        if (client.level == null || client.player == null || client.getCurrentServer() == null) return false;
        String host = client.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":")[0];
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return false;
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
        if (!inHollows || client.player == null || client.gui.screen() != null) return;
        var prices = bazaar.usable();
        String state = !session.running() ? "Paused: /profit start"
                : session.settling(now()) ? "Settling transfers (10s)" : "Running | observed gains only";
        double coins = prices == null ? 0 : prices.value(session.shares());
        double hours = session.elapsedMillis() / 3_600_000.0;
        long seconds = session.elapsedMillis() / 1000;
        List<String> lines = List.of("ProfitTracker | Coal", state,
                "Est. coins: " + (prices == null ? "unavailable" : number(coins)),
                "Est. coins/h: " + (prices == null || hours == 0 ? "--" : number(coins / hours)),
                "Coal: " + String.format(Locale.US, "%,d", session.coal()) + " (raw equivalent)",
                String.format(Locale.ROOT, "Session: %02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60),
                bazaar.status());
        int width = lines.stream().mapToInt(client.font::width).max().orElse(160) + 12;
        int x = 6;
        int y = Math.max(6, Math.min(40, graphics.guiHeight() - lines.size() * 12 - 10));
        graphics.fill(x, y, x + width, y + lines.size() * 12 + 8, 0xA010151C);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(client.font, lines.get(i), x + 6, y + 5 + i * 12,
                    i == 0 ? 0xFFFFD166 : i == 6 ? 0xFFB7C5D3 : 0xFFF3F6FA);
        }
    }

    private static String number(double value) { return String.format(Locale.US, "%,.0f", value); }
    private static long now() { return System.nanoTime() / 1_000_000; }
    private static int feedback(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[ProfitTracker] " + message));
        return 1;
    }
}
