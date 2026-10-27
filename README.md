# TgoodUtils

A small **client-only Fabric mod for Minecraft 26.2 and Java 25**, with coal profit
tracking in the Crystal Hollows and a nearby Endermite counter in The End.
No API key or server installation.

The movable coal HUD shows estimated coins, coins per hour, coal mined (raw
equivalents), and active session duration. The Endermite HUD turns green at your
chosen threshold and can show a brief title alert. Both hide with the vanilla HUD
and in menus, and appear only in their relevant SkyBlock areas. Nothing automates
mining or combat.

## Download and install

1. Open [GitHub Actions → Build mod](https://github.com/tgoodwinster/ProfitTracker/actions/workflows/build.yml).
2. Select a successful run for the latest commit. Under **Artifacts**, download
   **TgoodUtils-Minecraft-26.2** (GitHub login may be required).
3. Extract the ZIP and put **tgoodutils-1.1.1.jar** into your Minecraft `mods` folder.
   Remove older ProfitTracker or TgoodUtils JARs first; install only one version.
4. Install **Fabric Loader 0.20.1 or newer for Minecraft 26.2** and
   **Fabric API 0.161.0+26.2** alongside it. Launch using **Java 25**.

Use the regular JAR, not the sources JAR. Every push runs the build and tests,
verifies the JAR's Fabric metadata and entrypoint, and uploads the installable JAR.
Artifacts are retained for 90 days; rerun the workflow if an old download expired.

## Use

- `/tu` — open settings with **Coal**, **Endermites**, and **HUD** tabs.
- `/tu endermites` — open the Endermites settings tab directly.
- `/tu hud` — drag either HUD to reposition it; **Done** or **Escape** saves.
- `/profit start` — start/resume a session. Sessions initially start paused.
- `/profit pause` — stop tracking and freeze elapsed time, retaining confirmed gains.
- `/profit reset` — clear totals and elapsed time; preserve running/paused state.
- `/profit` — command help.

Enter the Crystal Hollows, enable **Sack Notifications** in SkyBlock settings,
run `/profit start`, wait for the 10-second settling period, then mine coal.
Gains appear after approximately **6 seconds**, allowing inventory and sack packets
to reconcile. Keep mining normally; you do not need to stop for updates to appear.
Confirmed gains survive pause/resume in the current game process. Disconnecting
pauses the session. Sessions are intentionally not saved to disk.

The timer counts running time in the Crystal Hollows with no menu open. It includes
idle time; it does not guess how much time you actually spent swinging a pickaxe.

### Endermite counter

In **The End**, the counter automatically shows **living Endermites within 48 blocks**,
refreshing four times per second. Kills, despawns, and mobs leaving range lower the
count. World changes and disconnects clear it.

The alert threshold defaults to **25**, as requested. To change it, open
`/tu endermites`, enter your spawn-cap number in **Alert threshold**, and click
**Apply threshold**. At or above that number, the counter turns green and the
optional **Endermite target reached!** title appears for 2.5 seconds. It fires once
per crossing, then rearms after the count stays below the target for one second.
You can disable the title or the whole Endermite tracker independently of coal.

**The client does not receive Endermite ownership or an authoritative personal
spawn-cap counter.** This is a nearby live-mob count, not an exact count of mobs
you personally spawned. It includes other players' Endermites and omits unloaded
or out-of-range mobs. It never estimates spawns from the number of blocks mined.
The threshold is your chosen target, not a cap reported by the server. It is
configurable from 0–200; **0** disables threshold alerts and green highlighting.

### Settings and HUD layout

The **Coal** tab controls HUD visibility and has start/pause/reset session buttons.
Hiding the coal HUD does not stop a running session. The **HUD** tab opens the drag
editor or restores default positions. The editor shows both previews even when a
tracker is disabled or you are outside its island. Positions stay on screen when
window size or GUI scale changes.

Preferences, threshold, and positions persist locally in `config/profittracker.json`.
They are separate from the in-memory coal session. Settings screens do not pause
multiplayer gameplay; you should be somewhere safe when adjusting the layout.

## Accounting and limits

This is an **observed estimate**, not an authoritative mining-history API. The
client cannot recover silent sack gains or prove the source of every server update.
It skips ambiguous intervals instead of estimating drops from Mining Fortune or XP.

- Existing inventory is a baseline, never profit. SkyBlock item IDs identify coal,
  coal blocks, enchanted coal, and enchanted coal blocks.
- Inventory and **signed item-level sack hover changes** form one combined ledger.
  Aggregate `[Sacks] +N items` totals are never used: they may include other materials.
- One enchanted coal is **160 coal**; one enchanted coal block is **25,600 coal**.
  Compaction changes the form, not the amount mined. Compaction across an existing
  baseline attributes only the newly mined fraction, never the whole enchanted item.
- Changes require recent evidence of targeted coal ore becoming air. Mere inventory
  growth, XP, an attack click, or a guessed fortune multiplier is insufficient.
- Menus, server commands, world changes, sack withdrawals, incomplete sack details,
  and nearby dropped coal invalidate the pending interval and rebaseline. A
  10-second quarantine discards old delayed updates. Already confirmed earnings remain.
- Normal inventory/sack transfers reconcile without adding profit. Do not move,
  buy, trade, drop, or withdraw coal during a mining run: **pause first**. Server
  delays beyond the reconciliation/quarantine windows and changes made by other
  mods cannot be perfectly attributed. This is not a promise of exact accounting
  under arbitrary packet delays or simultaneous transfers.
- Opening menus or pausing can discard the last unconfirmed gains. Hidden/missing
  sack hover details, truncated notifications, unloaded ore, ground pickups, and
  gains during quarantine are deliberately not reconstructed. Disable any server
  setting that suppresses sack notifications if you want sack gains tracked.
- Coal only is supported. Other ores, gemstones, XP, and treasure drops are ignored.

Prices come from the public `https://api.hypixel.net/v2/skyblock/bazaar` endpoint on
a background thread, at most once per minute. The estimate uses the best
**instant-sell** bid (`sell_summary`), before tax and market-depth slippage. Newly
observed compacted portions use their enchanted product's price; session totals
revalue when prices refresh. Failed refreshes retain clearly labelled cached prices
for at most five minutes, then show **unavailable**. Counts continue without prices.

## Build

Install a Java 25 JDK, then run:

```sh
./gradlew build
```

Windows: `gradlew.bat build`. Output: `build/libs/tgoodutils-1.1.1.jar`.
The checked-in Gradle 9.8.2 wrapper verifies its distribution SHA-256. Minecraft
26.2 is unobfuscated; the current `net.fabricmc.fabric-loom` plugin uses Mojang's
published class names directly, with no legacy Yarn/remapping configuration.

`./gradlew runClient` launches the development client. A display/OpenGL environment
and your own legitimate Minecraft login are needed to test on Hypixel. Unit tests
exercise compaction, initial stock, recipe-boundary attribution, delayed transfers,
sack parsing, session controls, price failures, Endermite threshold/rearm behavior,
config persistence, and HUD position clamping. They do not substitute for a live-server test.

### In-game acceptance check

1. Begin with existing coal/enchanted coal. Start and wait without mining: zero gain.
2. Mine coal without sacks: confirm the count after six seconds.
3. Repeat with a Mining Sack and enabled notifications; check its item hover counts.
4. Mine across 160 coal with Personal Compactor, including starting with 159 coal:
   only newly mined coal should increase the raw-equivalent total.
5. Repeat with an Enchanted Mining Sack. Transfer inventory into sacks, withdraw
   coal, open menus, pause, and reset: no transfer should add mining profit.
6. Disconnect the network/API: counts continue; prices eventually become unavailable.
7. Open `/tu`, switch all three tabs, and change preferences. Reopen the screen and
   restart the client to check they persist.
8. Use `/tu hud` to drag both previews, save, and change GUI scale. Each panel should
   stay on screen. Reset positions from the HUD tab.
9. In The End, set a small threshold you can test. Observe Endermites, reach the
   threshold, and confirm green text and one title. Kill enough to stay below the
   threshold for a second, then reach it again: one new title. Move away and change
   islands: the nearby count should fall/clear rather than retaining old mobs.

Live Hypixel acceptance requires an authenticated Minecraft session; the headless
build environment cannot verify actual server-side notification timing.
