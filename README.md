# TgoodUtils

A small **client-only Fabric mod for Minecraft 26.2 and Java 25**, with coal profit
tracking in the Crystal Hollows, an Endermite counter in The End, custom chat alerts,
and a configurable book combiner for chest-style custom anvil menus on other servers too.
No API key or server installation.

The movable coal HUD shows estimated coins, coins per hour, coal mined (raw
equivalents), and active session duration. The Endermite HUD turns green at your
chosen threshold and can show a large bold title with a sound. Chat messages can
also trigger your own configurable titles. The optional book combiner shift-clicks
matching books through a learned menu, up to your chosen tier. Counter panels hide with the vanilla
HUD and in menus, and appear only in their relevant SkyBlock areas. Nothing
automates mining or combat.

## Download and install

1. Open [GitHub Actions → Build mod](https://github.com/tgoodwinster/ProfitTracker/actions/workflows/build.yml).
2. Select a successful run for the latest commit. Under **Artifacts**, download
   **TgoodUtils-Minecraft-26.2** (GitHub login may be required).
3. Extract the ZIP and put **tgoodutils-1.4.0.jar** into your Minecraft `mods` folder.
   Remove older ProfitTracker or TgoodUtils JARs first; install only one version.
4. Install **Fabric Loader 0.20.1 or newer for Minecraft 26.2** and
   **Fabric API 0.161.0+26.2** alongside it. Launch using **Java 25**.

Use the regular JAR, not the sources JAR. Every push runs the build and tests,
verifies the JAR's Fabric metadata and entrypoint, and uploads the installable JAR.
Artifacts are retained for 90 days; rerun the workflow if an old download expired.

## Use

- `/tu` — open **Coal**, **Endermites**, **Titles**, **HUD**, and **Books** settings.
- `/tu endermites` — open the Endermites settings tab directly.
- `/tu titles` — open Custom Titles directly.
- `/tu books` — configure and learn the book combiner.
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

In **The End**, the compact HUD shows just **Endermites: count / threshold**.
It refreshes four times per second. Kills, despawns, and mobs leaving range lower
the count. World changes and disconnects clear it.

The alert threshold defaults to **25**, as requested. To change it, open
`/tu endermites` and move the **Alert threshold** slider. **Range** is configurable from
**4–128 blocks**, default **48**. Changes save when you close the screen or open
another settings page. A larger range cannot load mobs the server hasn't sent.

At or above the threshold, the counter turns green and the default alert displays
a large, bold, bright-green **KILL** for **3 seconds**, accompanied by a ding.
It fires once per crossing and rearms after a second below the threshold. A title
stays up for its full duration even if you immediately start killing the mobs.

**Configure title and sound** opens the shared alert editor:

- **Text:** message, size slider (0.5–8×), bold, duration slider (0.5–15s).
- **Color:** red/green/blue sliders with a live swatch and hex readout.
- **Sound:** on/mute, ding/bell/level-up, volume slider (0–100%). Minecraft's Master volume applies.
- **Position:** horizontal/vertical percentage sliders or a draggable title preview.
- **Test alert:** immediately preview the title and selected sound, including in menus.

Save the alert settings to apply them; Cancel/Escape discards that editor's edits.
Long titles automatically shrink to fit your window. Titles respect F1. The
Endermites tab's **Title** switch controls visual alerts; sound can be muted
separately in the Sound page. Switching the tracker off disables both.

**The client does not receive Endermite ownership or an authoritative personal
spawn-cap counter.** This is a nearby live-mob count, not an exact count of mobs
you personally spawned. It includes other players' Endermites and omits unloaded
or out-of-range mobs. It never estimates spawns from the number of blocks mined.
The threshold is your chosen target, not a cap reported by the server. It is
configurable from 0–200; **0** disables threshold alerts and green highlighting.

### Custom chat titles

1. Open `/tu titles` → **Add Custom Title**.
2. Paste a **Target chat message** with Ctrl+V, or use **Pick recent message**.
3. Choose **Contains** or **Exact** matching and whether to ignore case.
4. Open **Configure title and sound** to choose the displayed text, color, size,
   duration, position, noise, and volume. Use **Test alert** to check it.
5. Save the alert, then **Save rule**. Each rule and the whole module have on/off controls.

For mining cooldowns, select the actual ability-ready message from your chat and
set its displayed title to something like **ABILITY READY**. The mod reacts to
the received message; it does not predict cooldowns or activate the ability.

Rules match literal chat text, with Minecraft formatting codes and repeated
whitespace removed. No regex is needed. Action bars are excluded. The recent-message
picker keeps up to 30 messages **in memory only**, clearing them on disconnect;
only messages you explicitly save as rule targets are written to configuration.

You can edit, toggle, or delete up to 50 rules. Each rule ignores duplicate triggers
for two seconds. If several rules match, the first enabled match wins. A new alert
replaces the current title, rather than stacking unreadable text. Alerts use local
UI sounds and never send chat or gameplay actions to the server.

### Book combiner

This supports **chest-style custom anvil menus**, with 1–6 rows, on the server you
configure. It does not operate in vanilla anvils or ordinary inventories. It is
initially unconfigured and stopped; it never starts automatically on opening a menu.

1. Have unstacked books ready in your inventory and an **empty** custom Anvil menu.
2. Open `/tu books`. **Maximum output tier** defaults to **5**. Set the
   **Minimum delay** and **Maximum delay** sliders; defaults are **750–1250 ms**.
   Each can be adjusted from **500–5000 ms** in **50 ms** steps.
3. Press **Learn menu slots and book**, then open your server's Anvil menu within
   60 seconds. Click the first input slot, second input slot, **anvil confirmation button**,
   output slot, and finally one single-enchantment book in **your inventory**.
   These setup clicks are intercepted and do **not** move items. Escape cancels setup.
4. The profile saves the exact server address, menu title, row count, selected
   slots, confirmation-button material, idle decorations, enchantment, and starting
   tier. There is one profile; learn again when changing server/menu or book type.
5. With that menu open and your mouse cursor empty, press **K** to start. Press
   **K** again, close the menu, or click to stop. The stopping mouse click is
   swallowed. Remap the key under **Options → Controls → Key Binds → TgoodUtils**.

The combiner picks two matching books of the same enchantment and tier, shift-clicks
them in separately, normal-clicks the learned anvil confirmation button, then
shift-clicks the expected next-tier book out. It waits for
**server-confirmed anvil-slot changes** and reconciles Minecraft's inventory
state before proceeding. Standard servers can omit player-slot echoes when
Minecraft already predicted them correctly.
It works upward from the selected sample's tier and leaves books at the configured
maximum untouched. For example, 16 tier-I books can become one tier-V book in 15
combinations (60 clicks). Existing eligible books of the selected enchantment can also be
combined; put books you want to keep in storage first.

Only single-enchantment books with actual **vanilla stored-enchantment metadata**
or **SkyBlock-style `ExtraAttributes.enchantments` metadata** are recognized.
Names/lore alone are not treated as proof of a tier. Multiple-enchantment books,
stacked books, and other enchantment types are ignored. Other custom metadata
formats require a server-specific change; the mod stops instead of guessing. The
sequence is **shift-click book → shift-click book → normal-click anvil →
shift-click output**. Additional confirmation dialogs are unsupported.

A new delay is sampled uniformly from the inclusive minimum/maximum range for
each click, including the first. It is scheduled without sleeping or blocking
Minecraft. Raising the minimum above the maximum also raises the maximum; lowering
the maximum below the minimum also lowers the minimum. Equal endpoints give a fixed
delay. Old fixed-delay settings migrate to that minimum plus a 500 ms range (capped
at 5000 ms), preserving the learned menu and book profile. Server latency or slow
frames can extend the actual wait beyond the selected range.

After each click the combiner waits up to **8 seconds** for confirmation. A missing
or unexpected result, full inventory, held cursor item, menu/world change, disconnect,
or manual input stops progress; it never retries a failed click. A timeout can leave
books in the inputs. Recover/check them manually before restarting. No catch-up
burst is sent after a freeze. The status strip appears only over the configured
menu; the rest of the mod's counters keep their existing area restrictions.

### Settings and HUD layout

The **Coal** tab controls HUD visibility and has start/pause/reset session buttons.
Hiding the coal HUD does not stop a running session. The **HUD** tab opens the drag
editor or restores default positions. The editor shows both previews even when a
tracker is disabled or you are outside its island. Positions stay on screen when
window size or GUI scale changes.

Numeric settings use sliders with visible values and keyboard increments. Title
messages and chat targets still accept text. Dragging sliders updates the preview
or in-memory setting without writing to disk on every movement.

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

## Performance and troubleshooting

Coal inventory polling runs only while that session is active in the Crystal
Hollows. Unrelated gear/bag metadata is never cloned by the coal scanner. HUD text
is cached rather than reformatted every frame. Endermite scans run four times per
second and inspect already-loaded entities only; they do not load chunks.

Bazaar connection setup, downloads, and parsing run on a separate worker, never
the Minecraft tick/render thread. Requests have a 15-second header/body deadline
and an 8 MB limit, including expanded gzip data. Gzip compression reduces the
download size when the API supports it. The parser streams past unrelated products instead of building
a JSON tree of the entire market. Unchanged settings are not repeatedly rewritten.
Settings are saved only through the mod settings screens, not when opening ordinary
inventories or polled from disk during gameplay.

Book input/render callbacks are installed only for the learned Anvil menu or
explicit slot setup, rather than every chest-style menu. Title rendering callbacks
are attached only when a title is active, including previews. Inactive trackers
do not attach callbacks to each new inventory/menu. A clean-client comparison of
80 inventory/menu openings in 1.3.0 and 1.4.0 did not reproduce a 500 ms pause;
initialization stayed below 4 ms in both. Those cloud measurements do not identify
the cause of a stall with another client setup.

These bounds reduce avoidable work; they cannot establish what caused a specific
freeze. If one recurs, record the approximate time, duration, mod version, allocated
RAM, and whether the camera/UI froze or only the world stopped updating. Check
`logs/latest.log`, the F3 memory graph, and a Java Flight Recorder profile if
available. For a recurring menu-opening pause, press **F3+L** (when supported),
open/close the affected menus during the recording, and keep the generated
profiling archive and `logs/latest.log` to inspect the actual stalled thread.
The mod does not change Minecraft's gameplay connection. When explicitly started,
the book combiner sends ordinary inventory shift-clicks through Minecraft's native
client interaction method. Public Bazaar requests use a separate HTTPS connection.

## Download verification and antivirus reports

Each Actions artifact includes **SHA256SUMS.txt** beside the installable JAR. In
PowerShell, run `Get-FileHash .\tgoodutils-1.4.0.jar -Algorithm SHA256` and compare
it with the file's line in the manifest. This checks download integrity; a matching
checksum is not an antivirus verdict. The archive is generated by GitHub Actions;
there is no Windows installer. The JAR has no bundled native programs, no code
obfuscation, no self-update/downloader, and no process-launching code.

The only external HTTP request in the mod is the public Hypixel Bazaar fetch.
Preferences are written to `config/profittracker.json`. Recent chat stays in memory
except for target strings you deliberately save. The book combiner uses the existing
game connection for ordinary inventory interactions, without another service.

If the ZIP or JAR is flagged, record the antivirus product, exact detection name,
file/version, and SHA-256. The scanner's protection/history details usually show
these. Definitions and reputation can change between downloads, but the warning
alone cannot establish the cause. Keep the detection for investigation or submit
it to the antivirus vendor as a suspected false positive; do not disable protection
or add a blanket mods-folder exclusion. Builds use pinned dependencies and verified
Gradle downloads, and archive ordering/timestamps are fixed for repeatable JARs.

## Build

Install a Java 25 JDK, then run:

```sh
./gradlew build
```

Windows: `gradlew.bat build`. Output: `build/libs/tgoodutils-1.4.0.jar`.
The checked-in Gradle 9.8.2 wrapper verifies its distribution SHA-256. Minecraft
26.2 is unobfuscated; the current `net.fabricmc.fabric-loom` plugin uses Mojang's
published class names directly, with no legacy Yarn/remapping configuration.

`./gradlew runClient` launches the development client. A display/OpenGL environment
and your own legitimate Minecraft login are needed to test on Hypixel. Unit tests
exercise compaction, initial stock, recipe-boundary attribution, delayed transfers,
sack parsing, session controls, price failures, Endermite threshold/rearm behavior,
config persistence, HUD/title position clamping, chat matching and debounce, alert
duration, randomized click scheduling, and old-settings migration. They do not substitute for a live-server test.

### In-game acceptance check

1. Begin with existing coal/enchanted coal. Start and wait without mining: zero gain.
2. Mine coal without sacks: confirm the count after six seconds.
3. Repeat with a Mining Sack and enabled notifications; check its item hover counts.
4. Mine across 160 coal with Personal Compactor, including starting with 159 coal:
   only newly mined coal should increase the raw-equivalent total.
5. Repeat with an Enchanted Mining Sack. Transfer inventory into sacks, withdraw
   coal, open menus, pause, and reset: no transfer should add mining profit.
6. Disconnect the network/API: counts continue; prices eventually become unavailable.
7. Open `/tu`, switch all five tabs, and change preferences. Reopen the screen and
   restart the client to check they persist.
8. Use `/tu hud` to drag both previews, save, and change GUI scale. Each panel should
   stay on screen. Reset positions from the HUD tab.
9. In The End, set a small threshold you can test. Observe Endermites, reach the
   threshold, and confirm green text and one title. Kill enough to stay below the
   threshold for a second, then reach it again: one new title. Move away and change
   islands: the nearby count should fall/clear rather than retaining old mobs.
10. Preview Endermite alerts: check large bold text, color, size, drag position,
    sound presets, and mute. The visual title should remain for its chosen duration.
11. Add a Custom Title from a real cooldown message, check exact/contains matching,
    trigger it again after two seconds, then verify disabled rules stay silent.

12. Set the book delay range with both sliders, check that crossing the bounds
    keeps them ordered, and verify the maximum tier, menu profile, and range persist.
13. Adjust title sliders, test the color/sound/position preview, save, and then
    verify that Cancel discards later slider edits. Reopen ordinary inventories
    with all trackers stopped and check for any repeated pause.

Live Hypixel acceptance requires an authenticated Minecraft session; the headless
build environment cannot verify actual server-side notification timing.
