# ProfitTracker

A small **client-only Fabric mod for Minecraft 26.2 and Java 25**, focused on coal
mining in Hypixel SkyBlock's Crystal Hollows. No API key or server installation.

The top-left HUD shows estimated coins, coins per hour, coal mined (raw equivalents),
and active session duration. It hides with the vanilla HUD, in menus, and outside
the Crystal Hollows. Nothing changes your controls or automates mining.

## Download and install

1. Open [GitHub Actions → Build mod](https://github.com/tgoodwinster/ProfitTracker/actions/workflows/build.yml).
2. Select a successful run for the latest commit. Under **Artifacts**, download
   **ProfitTracker-Minecraft-26.2** (GitHub login may be required).
3. Extract the ZIP and put **profittracker-1.0.0.jar** into your Minecraft `mods` folder.
4. Install **Fabric Loader 0.20.1 or newer for Minecraft 26.2** and
   **Fabric API 0.161.0+26.2** alongside it. Launch using **Java 25**.

Use the regular JAR, not the sources JAR. Every push runs the build and tests,
verifies the JAR's Fabric metadata and entrypoint, and uploads the installable JAR.
Artifacts are retained for 90 days; rerun the workflow if an old download expired.

## Use

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

Windows: `gradlew.bat build`. Output: `build/libs/profittracker-1.0.0.jar`.
The checked-in Gradle 9.8.2 wrapper verifies its distribution SHA-256. Minecraft
26.2 is unobfuscated; the current `net.fabricmc.fabric-loom` plugin uses Mojang's
published class names directly, with no legacy Yarn/remapping configuration.

`./gradlew runClient` launches the development client. A display/OpenGL environment
and your own legitimate Minecraft login are needed to test on Hypixel. Unit tests
exercise compaction, initial stock, recipe-boundary attribution, delayed transfers,
sack parsing, session controls, and price failures. They do not substitute for a
live-server test.

### In-game acceptance check

1. Begin with existing coal/enchanted coal. Start and wait without mining: zero gain.
2. Mine coal without sacks: confirm the count after six seconds.
3. Repeat with a Mining Sack and enabled notifications; check its item hover counts.
4. Mine across 160 coal with Personal Compactor, including starting with 159 coal:
   only newly mined coal should increase the raw-equivalent total.
5. Repeat with an Enchanted Mining Sack. Transfer inventory into sacks, withdraw
   coal, open menus, pause, and reset: no transfer should add mining profit.
6. Disconnect the network/API: counts continue; prices eventually become unavailable.

Live Hypixel acceptance requires an authenticated Minecraft session; the headless
build environment cannot verify actual server-side notification timing.
