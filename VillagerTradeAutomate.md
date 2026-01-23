# Librarian Enchanted Book Cycling

## Overview

This document outlines the design and implementation plan for **automating the librarian villager enchanted book cycling process** in Baritone. 

**Primary Goal:** Automate the tedious process of cycling a librarian villager's trades to find specific enchanted books (like Mending, Sharpness V, Protection IV, etc.)

**Target Platform:** Fabric Loader (required dependency)

---

## Goals

1. **Cycle librarian trades** - Repeatedly place/break lectern to reset trades
2. **Read enchanted book trades** - Parse the book's enchantment data
3. **Match desired enchantments** - Check against user-specified criteria
4. **Stop when found** - Notify player when desired enchantment appears
5. **Optional auto-lock** - Make one trade to lock the profession

---

## Primary Use Case: Villager Trade Cycling

### The Manual Process (What Players Do Today)

Players use this technique to get specific trades (like Mending books) from villagers:

```
┌─────────────────────────────────────────────────────────────────┐
│                    VILLAGER TRADE CYCLING                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. SETUP                                                       │
│     └─► Trap unemployed villager in a cell                      │
│     └─► Have workstation block ready (e.g., Lectern)            │
│                                                                 │
│  2. PLACE WORKSTATION                                           │
│     └─► Villager claims job (becomes Librarian, etc.)           │
│     └─► Villager gets RANDOM trades                             │
│                                                                 │
│  3. CHECK TRADES                                                │
│     └─► Right-click villager                                    │
│     └─► Look at available trades                                │
│                                                                 │
│  4. DECISION                                                    │
│     ├─► WANT the trade? ──► DONE! Lock it in by trading once    │
│     │                                                           │
│     └─► DON'T want it? ──► Continue to step 5                   │
│                                                                 │
│  5. RESET                                                       │
│     └─► Break the workstation block                             │
│     └─► Villager becomes unemployed                             │
│     └─► Trades are cleared                                      │
│     └─► Go back to step 2                                       │
│                                                                 │
│  ∞  REPEAT until desired trade appears                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Why Automate This?

- **Tedious**: Getting a specific trade (like Mending) can take 50-200+ cycles
- **Time-consuming**: Each cycle takes ~5-10 seconds manually
- **Mindless**: No skill involved, just repetitive clicking
- **Perfect for automation**: Clear success/fail criteria, deterministic steps

### Example Commands (Proposed)

```
# Setup (one-time per villager)
#trade setup                                    - Enter setup mode
  → Right-click villager to select it
  → Right-click block position for lectern placement

# Basic - single enchantment
#trade cycle mending                            - Cycle until Mending book found
#trade cycle "sharpness 5"                      - Cycle until Sharpness V book found
#trade cycle silk_touch                         - Cycle until Silk Touch book found

# Multiple enchantments (OR logic - stops when ANY is found)
#trade cycle [mending, "sharpness 5", "protection 4"]
#trade cycle [mending, silk_touch, "fortune 3"]

# Presets - expand to common "best" enchantments for gear type
#trade cycle helmet_best                        - Protection 4, Mending, Unbreaking 3, Aqua Affinity, Respiration 3
#trade cycle sword_best                         - Sharpness 5, Mending, Looting 3, Fire Aspect 2, Sweeping Edge 3
#trade cycle pickaxe_best                       - Efficiency 5, Mending, Fortune 3, Silk Touch
#trade cycle all_best                           - Any "god tier" enchantment

# Auto-lock option (make one trade to lock profession permanently)
#trade cycle mending autolock                   - Find Mending, then buy it to lock
#trade cycle sword_best autolock

# Control & info commands
#trade stop                                     - Stop cycling
#trade status                                   - Show cycle count, time elapsed, last trades seen
#trade presets                                  - List all available presets
```

**Requirements before `#trade cycle`:**
1. Complete `#trade setup` (select villager + lectern position)
2. Hold **Lectern** in your main hand
3. Have an **unemployed villager** ready (not already traded with)

### Primary User Story

**As a player**, I want to automate the librarian enchanted book cycling process so that:
- I can specify which enchantment(s) I'm looking for (Mending, Sharpness V, etc.)
- The bot automatically places/breaks the lectern to reset trades
- The bot checks each set of trades for my desired enchanted book
- The bot stops and notifies me when it finds what I want
- I can AFK while this happens (cycling can take 50-200+ attempts)

### Example Scenarios

**Scenario 1: Looking for Mending**
```
Player: #trade setup
        [clicks villager]
        [clicks block position]
Player: [holds Lectern in hand]
Player: #trade cycle mending
Bot:    Cycling for: mending
        ... [cycles 47 times] ...
Bot:    ✓ FOUND after 47 cycles (2m 34s): Mending - 24 emeralds
```

**Scenario 2: Looking for any good sword enchant**
```
Player: #trade cycle sword_best
Bot:    Cycling for: sharpness 5, mending, looting 3, fire_aspect 2, sweeping_edge 3
        ... [cycles 12 times] ...
Bot:    ✓ FOUND after 12 cycles (38s): Looting III - 18 emeralds
```

**Scenario 3: Auto-lock when found**
```
Player: #trade cycle mending autolock
Bot:    Cycling for: mending (will auto-lock when found)
        ... [cycles 89 times] ...
Bot:    ✓ FOUND after 89 cycles: Mending - 20 emeralds
Bot:    Auto-locking trade... Done! Profession is now permanent.
```

### Secondary User Stories (Future)

1. **As a player**, I want to cycle multiple librarians in parallel (trading hall setup)
2. **As a player**, I want to find the cheapest price for a specific enchantment
3. **As a player**, I want to define my own custom presets in a config file

---

## Minecraft Mechanics Reference

### Villager Profession System

Understanding how villager professions work is critical for this feature:

```
UNEMPLOYED VILLAGER                    EMPLOYED VILLAGER
       │                                      │
       │ Claims workstation                   │ Workstation broken
       │ (within 48 blocks)                   │ (OR villager hasn't traded yet)
       ▼                                      ▼
┌─────────────────┐                  ┌─────────────────┐
│ - No profession │   ◄──────────►   │ - Has profession│
│ - No trades     │                  │ - Has trades    │
│ - Brown coat    │                  │ - Colored coat  │
└─────────────────┘                  └─────────────────┘
```

**Key Rules:**
1. Villager must be **within 48 blocks** of workstation to claim it
2. Villager must have **line of sight** to workstation (pathfinding)
3. Only **one villager** can claim each workstation
4. Trades are **randomized** when profession is gained
5. **Once traded with**, profession is LOCKED (can't be reset!)
6. Villagers only change profession during **work hours** (2000-9000 ticks)

### Workstation → Profession Mapping

**Primary Focus: Librarian**

| Workstation Block | Profession | Notable Trades |
|-------------------|------------|----------------|
| **`lectern`** | **Librarian** | **Enchanted books**, name tags, lanterns |

The librarian is the most commonly cycled villager because:
- Only source of specific enchanted books
- Enchanted books can have any enchantment at any level
- Crucial for getting "god" gear (Mending, Sharpness V, etc.)

**Other Workstations (for future expansion):**

| Workstation Block | Profession | Notable Trades |
|-------------------|------------|----------------|
| `cartography_table` | Cartographer | Maps, banners |
| `brewing_stand` | Cleric | Ender pearls, glowstone |
| `smithing_table` | Toolsmith | Diamond tools |
| `blast_furnace` | Armorer | Diamond armor |
| `grindstone` | Weaponsmith | Diamond weapons |
| `composter` | Farmer | Golden carrots |
| `fletching_table` | Fletcher | Arrows, bows |

### Enchantment Presets

To make it easy to search for commonly desired enchantments, we define presets:

| Preset | Enchantments Included |
|--------|----------------------|
| `helmet_best` | Protection 4, Mending, Unbreaking 3, Aqua Affinity, Respiration 3 |
| `chestplate_best` | Protection 4, Mending, Unbreaking 3 |
| `leggings_best` | Protection 4, Mending, Unbreaking 3, Swift Sneak 3 |
| `boots_best` | Protection 4, Mending, Unbreaking 3, Feather Falling 4, Depth Strider 3, Soul Speed 3 |
| `sword_best` | Sharpness 5, Mending, Unbreaking 3, Looting 3, Fire Aspect 2, Sweeping Edge 3 |
| `pickaxe_best` | Efficiency 5, Mending, Unbreaking 3, Fortune 3, Silk Touch |
| `axe_best` | Efficiency 5, Mending, Unbreaking 3, Sharpness 5 |
| `shovel_best` | Efficiency 5, Mending, Unbreaking 3, Silk Touch |
| `bow_best` | Power 5, Mending, Unbreaking 3, Infinity, Flame |
| `crossbow_best` | Quick Charge 3, Mending, Unbreaking 3, Multishot, Piercing 4 |
| `trident_best` | Mending, Unbreaking 3, Riptide 3, Loyalty 3, Channeling, Impaling 5 |
| `fishing_best` | Luck of the Sea 3, Lure 3, Mending, Unbreaking 3 |
| `elytra_best` | Mending, Unbreaking 3 |
| `all_best` | All "god tier" enchants: Mending, Silk Touch, Fortune 3, Sharpness 5, Protection 4, Efficiency 5 |
| `utility` | Mending, Silk Touch, Fortune 3, Infinity, Looting 3 |

**Note:** Some enchantments are mutually exclusive (e.g., Fortune/Silk Touch, Infinity/Mending). The preset includes all desirable options - player gets whichever appears first.

**Customization (Future):** Allow players to define custom presets in a config file.

### Trade Offer Structure

Each `MerchantOffer` contains:
- `baseCostA` - First input item (e.g., emeralds)
- `costB` - Second input item (optional)
- `result` - Output item
- `uses` - Times this trade has been used
- `maxUses` - Max uses before restock needed
- `xp` - XP given to villager
- `priceMultiplier` - For demand-based pricing

### Enchanted Book Trade Format

Librarian's enchanted book trades:
- **Input**: Emeralds (varies by enchant level) + Book
- **Output**: Enchanted Book with single enchantment

The enchantment is stored in the book's NBT data:
```
StoredEnchantments: [{id: "minecraft:mending", lvl: 1}]
```

---

## Technical Analysis

### Relevant Minecraft Classes

| Class | Purpose |
|-------|---------|
| `Villager` | The villager entity |
| `VillagerProfession` | Enum of professions (librarian, farmer, etc.) |
| `VillagerData` | Contains profession, level, type |
| `MerchantScreen` | The GUI shown when trading |
| `MerchantMenu` | Server-side container for trades |
| `MerchantOffers` | List of available trades |
| `MerchantOffer` | Single trade offer (input items → output item) |
| `ServerboundInteractPacket` | Packet to interact with entity |
| `ClientboundMerchantOffersPacket` | Server sends trade list to client |
| `ServerboundSelectTradePacket` | Client selects a trade |

### Fabric Loader & API Integration

This mod requires **Fabric Loader** (already configured in `fabric.mod.json`). We can optionally depend on **Fabric API** modules for cleaner hooks.

#### Option A: Pure Mixin Approach (No Fabric API dependency)

Continue using Baritone's existing Mixin pattern. This is consistent with the current codebase.

```java
// New mixin: MixinClientPacketListener (add to existing)
@Inject(method = "handleMerchantOffers", at = @At("RETURN"))
private void onMerchantOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
    // Fire event with trade data
    baritone.getGameEventHandler().onMerchantOffers(new MerchantOffersEvent(packet));
}
```

#### Option B: Fabric API Events (Additional dependency)

Add Fabric API as a dependency for cleaner screen/network hooks:

```gradle
// In fabric/build.gradle
dependencies {
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_api_version}"
}
```

**Useful Fabric API modules:**

| Module | Use Case |
|--------|----------|
| `fabric-screen-api-v1` | `ScreenEvents.AFTER_INIT` - detect when `MerchantScreen` opens |
| `fabric-networking-api-v1` | `ClientPlayNetworking.registerGlobalReceiver` - intercept packets |
| `fabric-lifecycle-events-v1` | `ClientTickEvents.END_CLIENT_TICK` - tick hooks |

**Example with Fabric Screen API:**
```java
ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
    if (screen instanceof MerchantScreen merchantScreen) {
        // Trade window opened - can now read trades
        MerchantMenu menu = merchantScreen.getMenu();
        MerchantOffers offers = menu.getOffers();
        // Fire event...
    }
});
```

#### Recommendation: **Option A (Pure Mixin)**

Rationale:
- Consistent with existing Baritone architecture
- No additional dependencies
- Baritone already has `onReceivePacket` event infrastructure
- Mixins give us precise control over timing

### Existing Baritone Event System

Baritone already has packet event infrastructure we can use:

```java
// IGameEventListener.java - ALREADY EXISTS
void onReceivePacket(PacketEvent event);  // Fires before inbound packet processed
void onSendPacket(PacketEvent event);      // Fires before outbound packet sent
```

We need to add:
```java
// New events for villager trading
void onMerchantOffersReceived(MerchantOffersEvent event);  // When trade list received
void onScreenChanged(ScreenEvent event);                    // When GUI changes
```

### Existing Baritone Components to Leverage

| Component | How to Use |
|-----------|------------|
| `FollowProcess` | Reference for entity tracking patterns |
| `GetToBlockProcess` | Reference for right-click-on-arrival pattern |
| `InventoryBehavior` | Inventory management, slot clicking |
| `IPlayerController` | Has `windowClick()` for GUI interaction |
| `InputOverrideHandler` | For simulating right-click input |
| `GoalNear` | Navigate to within range of villager |
| `MixinClientPlayNetHandler` | **Existing packet hook mixin - extend this** |
| `IGameEventListener` | **Existing event system - add new events** |

### New Components Needed

1. **`IVillagerTradeProcess`** (API interface in `src/api/`)
   - Define public methods for trade automation

2. **`VillagerTradeProcess`** (Implementation in `src/main/`)
   - Main process controlling the trade workflow

3. **`TradeCommand`** (Chat command in `src/main/java/baritone/command/defaults/`)
   - Parse user commands like `#trade buy mending`

4. **`MerchantOffersEvent`** (Event in `src/api/java/baritone/api/event/events/`)
   - Event fired when trade offers are received

5. **`MixinMerchantScreen`** (Mixin in `src/launch/`) 
   - Hook into merchant screen for trade execution

6. **Extension to `MixinClientPlayNetHandler`**
   - Add handler for `ClientboundMerchantOffersPacket`

---

## Architecture Design

### State Machine (Trade Cycling)

```
                         ┌──────────────────────────────────────────────────────┐
                         │                   CYCLE LOOP                         │
                         │                                                      │
┌─────────────┐          │  ┌─────────────────┐    ┌──────────────────────┐    │
│    IDLE     │          │  │ PLACE_WORKSTATION│───►│ WAIT_FOR_PROFESSION  │    │
└──────┬──────┘          │  └─────────────────┘    └──────────┬───────────┘    │
       │                 │           ▲                        │                 │
       │ User: #trade    │           │                        ▼                 │
       │ cycle lectern   │           │              ┌──────────────────────┐    │
       │ mending         │           │              │ INTERACT_VILLAGER    │    │
       ▼                 │           │              └──────────┬───────────┘    │
┌─────────────────┐      │           │                        │                 │
│ VALIDATE_SETUP  │──────┼───────────┼────────────────────────┘                 │
└─────────────────┘      │           │                        │                 │
  - Villager nearby?     │           │                        ▼                 │
  - Workstation pos?     │           │              ┌──────────────────────┐    │
  - Villager unemployed? │           │              │ READ_TRADES          │    │
                         │           │              └──────────┬───────────┘    │
                         │           │                        │                 │
                         │           │         ┌──────────────┴──────────────┐  │
                         │           │         │                             │  │
                         │           │         ▼                             ▼  │
                         │  ┌─────────────────────┐              ┌───────────┐  │
                         │  │ BREAK_WORKSTATION   │◄─────────────│  NO MATCH │  │
                         │  └─────────────────────┘   Not found  └───────────┘  │
                         │                                                      │
                         └──────────────────────────────────────────────────────┘
                                                                 │
                                                          Found match!
                                                                 │
                                                                 ▼
                                                      ┌────────────────────┐
                                                      │ FOUND - SUCCESS!   │
                                                      │ - Log the trade    │
                                                      │ - Notify player    │
                                                      │ - Optional: lock   │
                                                      └────────────────────┘
```

### Key States Explained

| State | Description | Exit Condition |
|-------|-------------|----------------|
| `IDLE` | Waiting for command | User issues `#trade cycle` |
| `VALIDATE_SETUP` | Check villager & workstation positions | Setup valid |
| `PLACE_WORKSTATION` | Place the workstation block | Block placed |
| `WAIT_FOR_PROFESSION` | Wait for villager to claim job | Villager has profession |
| `INTERACT_VILLAGER` | Right-click to open trade GUI | Trade GUI opens |
| `READ_TRADES` | Parse and check trades against criteria | Trades read |
| `BREAK_WORKSTATION` | Destroy workstation to reset | Block broken |
| `FOUND` | Desired trade found! | Process complete |

### Required Inputs from User

For the cycle to work, we need:

1. **Target Trade(s)** - What are we looking for? (e.g., "mending", "protection 4")
2. **Workstation Type** - What block to place? (e.g., lectern, smithing_table)
3. **Workstation Position** - Where to place/break the block?
4. **Villager Reference** - Which villager to cycle? (nearest? specific?)

### Process Interface (Draft)

```java
public interface IVillagerTradeProcess extends IBaritoneProcess {
    
    /**
     * Start cycling a villager's trades until desired trade is found.
     * 
     * @param workstationBlock The workstation to place (e.g., Blocks.LECTERN)
     * @param workstationPos Where to place/break the workstation
     * @param desiredTrades Predicate that returns true when desired trade is found
     */
    void startCycling(Block workstationBlock, BlockPos workstationPos, 
                      Predicate<MerchantOffer> desiredTrades);
    
    /**
     * Stop the cycling process.
     */
    void stopCycling();
    
    /**
     * Get cycle statistics.
     */
    CycleStats getStats();
    
    /**
     * Get current state.
     */
    CycleState getState();
    
    /**
     * Get the last set of trades seen (for debugging/display).
     */
    List<MerchantOffer> getLastSeenTrades();
    
    record CycleStats(
        int cycleCount,
        long startTime,
        long elapsedMs,
        @Nullable MerchantOffer foundTrade
    ) {}
    
    enum CycleState {
        IDLE,
        VALIDATING,
        PLACING_WORKSTATION,
        WAITING_FOR_PROFESSION,
        INTERACTING,
        READING_TRADES,
        BREAKING_WORKSTATION,
        FOUND,
        FAILED
    }
}

---

## Implementation Phases

### Phase 1: Core Infrastructure
- [ ] Add `MerchantOffersEvent` to event system
- [ ] Add mixin to `ClientPacketListener.handleMerchantOffers()`
- [ ] Implement villager entity interaction (right-click)
- [ ] Detect when trade GUI opens/closes

### Phase 2: Trade Reading & Matching
- [ ] Parse `MerchantOffer` structure (cost items, result item)
- [ ] Create trade matching predicates (by item, by enchantment)
- [ ] Enchanted book parsing (extract enchantment + level)
- [ ] Display current trades to user via chat (`#trade scan`)

### Phase 3: Block Interaction
- [ ] Place workstation block at position
- [ ] Break workstation block
- [ ] Detect when villager gains/loses profession
- [ ] Handle block placement failures (blocked, out of range)

### Phase 4: Cycle Loop
- [ ] Implement `VillagerTradeProcess` state machine
- [ ] Wire up the full cycle: place → wait → interact → read → break → repeat
- [ ] Add cycle statistics (count, time)
- [ ] Add `#trade cycle` command

### Phase 5: Polish & Edge Cases
- [ ] Handle villager moving away
- [ ] Handle villager taking damage (becomes scared)
- [ ] Handle inventory full (for locking trades)
- [ ] Handle workstation being claimed by different villager
- [ ] Configurable delays between actions
- [ ] Sound/notification when trade found

### Future Enhancements
- [ ] Multiple simultaneous villagers
- [ ] Auto-setup (find unemployed villager, create cell)
- [ ] Trade locking (auto-buy once to lock profession)
- [ ] Best price search across multiple villagers

---

## Open Questions

### 1. Setup Mode: Manual vs Automatic

**Question:** How much setup should the user do vs the bot?

| Approach | User Does | Bot Does |
|----------|-----------|----------|
| **Fully Manual** | Position near villager, tell bot workstation pos | Everything else |
| **Semi-Auto** | Start command with block in hand | Bot figures out where to place |
| **Click to Set** | Click on villager, click on workstation pos | Bot remembers positions |

**Recommendation:** Start with **Click to Set** approach:
```
#trade setup                    - Enter setup mode
[Player clicks villager]        - Bot: "Villager selected: Unemployed villager at x,y,z"
[Player clicks block position]  - Bot: "Workstation position set: x,y,z"
#trade cycle mending            - Start cycling with lectern (inferred from "mending")
```

This gives the player control while minimizing typing.

### 2. Workstation Handling (DECIDED)

**Decision:** Keep it simple - player provides everything.

**Requirements before `#trade cycle`:**
1. Player must have **workstation block selected in hotbar** (not just in inventory)
2. Player must have completed `#trade setup` (clicked villager + clicked position)

**Bot will:**
- Use whatever block is in player's hand
- Place at the clicked position
- Break the block when resetting
- Repeat

**Bot will NOT:**
- Search inventory for workstation
- Get blocks from chests
- Figure out what block to use

**Validation on `#trade cycle`:**
```java
if (player.getMainHandItem().isEmpty()) {
    error("Hold the workstation block in your hand");
    return;
}
if (!isWorkstationBlock(player.getMainHandItem())) {
    error("That's not a workstation block. Need lectern, smithing_table, etc.");
    return;
}
```

### 3. Enchantment Syntax (DECIDED)

**Syntax options:**

```
# Single enchantment (no level = any level)
#trade cycle mending
#trade cycle efficiency

# Single enchantment with level
#trade cycle "sharpness 5"
#trade cycle "protection 4"

# Multiple enchantments (array syntax, OR logic)
#trade cycle [mending, "sharpness 5", silk_touch]

# Presets (expand to predefined list)
#trade cycle sword_best
#trade cycle all_best

# With autolock flag
#trade cycle mending autolock
#trade cycle [mending, silk_touch] autolock
```

**Parsing rules:**
- Case insensitive: `Mending` = `mending` = `MENDING`
- Underscores and spaces interchangeable: `silk_touch` = `silk touch`
- Level is optional: `sharpness` matches any level, `sharpness 5` matches only V
- Quotes required for multi-word with level: `"sharpness 5"`
- Brackets for multiple: `[a, b, c]`
- Presets are single words ending in `_best` or specific names

### 4. Profession Detection

**Question:** How do we detect when villager has gained/lost profession?

**Options:**
| Method | How | Reliability |
|--------|-----|-------------|
| Poll entity data | Check `Villager.getVillagerData().getProfession()` each tick | High |
| Watch for particle | Villager emits particles when gaining job | Medium |
| Time-based assume | Wait N ticks after placing workstation | Low |

**Recommendation:** **Poll entity data** - Most reliable, Baritone already ticks each frame.

### 5. Timing & Delays

**Question:** How long to wait between actions?

| Action | Minimum Wait | Why |
|--------|--------------|-----|
| After placing workstation | ~20-40 ticks | Villager needs time to pathfind & claim |
| After villager gets job | ~5 ticks | Let trades initialize |
| After opening trade GUI | ~3 ticks | Let packet arrive |
| After closing trade GUI | ~5 ticks | Let GUI close |
| After breaking workstation | ~10 ticks | Let villager lose profession |

**Recommendation:** Make these configurable settings with sensible defaults.

```java
public final Setting<Integer> workstationClaimWaitTicks = new Setting<>(40);
public final Setting<Integer> tradeGuiReadDelayTicks = new Setting<>(5);
public final Setting<Integer> workstationBreakDelayTicks = new Setting<>(10);
```

### 6. What If Villager Doesn't Claim Workstation?

**Question:** How to handle edge cases?

**Scenarios:**
- Villager can't pathfind to workstation (blocked)
- Another villager claims it first
- Villager is scared (recently hurt)
- Wrong time of day (villagers sleep at night)

**Recommendation:** 
- Timeout after N ticks, log warning, retry
- Add setting for max retries before giving up
- Log clear error messages so player can fix setup

### 7. Trade Locking (DECIDED)

**Decision:** Make autolock an **optional command argument**, not a setting.

```
#trade cycle mending              - Find mending, notify player, do NOT lock
#trade cycle mending autolock     - Find mending, then make one trade to lock profession
```

**Why command argument instead of setting?**
- Player might want to autolock sometimes but not others
- More explicit - player knows what will happen
- No need to remember to change a setting

**Autolock behavior:**
1. Find matching trade
2. Check if player has required items (emeralds + book)
3. If yes: execute one trade to lock profession
4. If no: notify player "Found trade but can't autolock - need X emeralds and 1 book"

### 8. GUI Closing (DECIDED)

**Question:** How do we close the trade GUI?

**Research:** Baritone currently has **no code for closing GUIs**. The `Input` enum only covers movement and clicks (no ESCAPE key). Baritone's approach is to detect when a GUI is open and skip/pause operations.

**Options:**

| Method | Code | Pros | Cons |
|--------|------|------|------|
| `mc.setScreen(null)` | `ctx.minecraft().setScreen(null)` | Simple, direct | Might not send close packet to server |
| `player.closeContainer()` | `ctx.player().closeContainer()` | Proper method, sends packet | Need to verify it exists |
| Send packet directly | `new ServerboundContainerClosePacket(containerId)` | Full control | More complex |
| Leave open | Don't close | Player sees the trade | Bot can't continue until closed |

**Decision:** Use **`player.closeContainer()`** if available, fallback to **`mc.setScreen(null)`**

```java
// Close the trade GUI properly
if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
    ctx.player().closeContainer();  // Sends packet + closes screen
}
// Or fallback:
// ctx.minecraft().setScreen(null);
```

This is consistent with how the game handles it when player presses ESC.

---

## Settings (Proposed)

```java
// In Settings.java - Villager Trade Cycling Settings

/**
 * Ticks to wait after placing workstation for villager to claim it.
 * Villager needs time to pathfind and claim the job.
 */
public final Setting<Integer> villagerWorkstationClaimTicks = new Setting<>(40);

/**
 * Ticks to wait after villager gets profession before interacting.
 */
public final Setting<Integer> villagerProfessionWaitTicks = new Setting<>(5);

/**
 * Ticks to wait after opening trade GUI before reading trades.
 */
public final Setting<Integer> villagerTradeReadDelayTicks = new Setting<>(5);

/**
 * Ticks to wait after closing trade GUI.
 */
public final Setting<Integer> villagerTradeCloseDelayTicks = new Setting<>(3);

/**
 * Ticks to wait after breaking workstation before placing again.
 */
public final Setting<Integer> villagerWorkstationBreakDelayTicks = new Setting<>(10);

/**
 * Maximum ticks to wait for villager to claim workstation before retrying.
 * If exceeded, will break and re-place workstation.
 */
public final Setting<Integer> villagerClaimTimeoutTicks = new Setting<>(100);

/**
 * Maximum cycles before giving up. -1 for unlimited.
 */
public final Setting<Integer> villagerMaxCycles = new Setting<>(-1);

/**
 * Play sound when desired trade is found.
 */
public final Setting<Boolean> villagerTradeFoundSound = new Setting<>(true);
```

---

---

## Implementation Details

### File Structure

```
src/
├── api/java/baritone/api/
│   ├── event/events/
│   │   ├── MerchantOffersEvent.java          # NEW - trade offers received
│   │   └── ScreenChangedEvent.java           # NEW - GUI screen changed
│   ├── event/listener/
│   │   └── IGameEventListener.java           # MODIFY - add new event methods
│   └── process/
│       └── IVillagerTradeProcess.java        # NEW - public API interface
│
├── main/java/baritone/
│   ├── process/
│   │   └── VillagerTradeProcess.java         # NEW - main implementation
│   └── command/defaults/
│       └── TradeCommand.java                 # NEW - chat command
│
└── launch/java/baritone/launch/mixins/
    ├── MixinClientPlayNetHandler.java        # MODIFY - add merchant packet hook
    └── MixinMinecraft.java                   # MODIFY - add screen change hook
```

### Key Code Sketches

#### MerchantOffersEvent.java
```java
package baritone.api.event.events;

import net.minecraft.world.item.trading.MerchantOffers;

public class MerchantOffersEvent {
    private final int containerId;
    private final MerchantOffers offers;
    private final int villagerLevel;
    private final int villagerXp;
    private final boolean showProgress;
    private final boolean canRestock;
    
    // Constructor, getters...
}
```

#### IVillagerTradeProcess.java
```java
package baritone.api.process;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

public interface IVillagerTradeProcess extends IBaritoneProcess {
    
    // ==================== SETUP ====================
    
    /**
     * Enter setup mode - next villager click sets target villager.
     */
    void beginSetup();
    
    /**
     * Set the target villager to cycle.
     */
    void setTargetVillager(Villager villager);
    
    /**
     * Set where to place/break the workstation.
     */
    void setWorkstationPosition(BlockPos pos);
    
    /**
     * Get current setup status.
     */
    SetupStatus getSetupStatus();
    
    record SetupStatus(
        @Nullable Villager villager,
        @Nullable BlockPos workstationPos,
        boolean isReady
    ) {}
    
    // ==================== CYCLING ====================
    
    /**
     * Start cycling trades until predicate matches.
     * 
     * @param workstationBlock Block to place (e.g., Blocks.LECTERN)
     * @param desiredTrade Predicate that returns true for desired trades
     * @param autoLock If true, make one trade to lock profession when found
     */
    void startCycling(Block workstationBlock, Predicate<MerchantOffer> desiredTrade, boolean autoLock);
    
    /**
     * Stop cycling.
     */
    void stopCycling();
    
    /**
     * Check if currently cycling.
     */
    boolean isCycling();
    
    // ==================== STATUS ====================
    
    /**
     * Get current state.
     */
    CycleState getState();
    
    /**
     * Get cycle statistics.
     */
    CycleStats getStats();
    
    /**
     * Get the last trades seen (for display).
     */
    List<MerchantOffer> getLastSeenOffers();
    
    /**
     * Get the matching trade if found.
     */
    @Nullable MerchantOffer getFoundTrade();
    
    // ==================== TYPES ====================
    
    record CycleStats(
        int cycleCount,
        long startTimeMs,
        long elapsedMs,
        int tradesChecked
    ) {}
    
    enum CycleState {
        IDLE,
        SETUP_PENDING,          // Waiting for user to click villager/position
        PLACING_WORKSTATION,    // Placing the workstation block
        WAITING_FOR_PROFESSION, // Waiting for villager to claim job
        OPENING_TRADE_GUI,      // Right-clicking villager
        READING_TRADES,         // Parsing trade offers
        CLOSING_TRADE_GUI,      // Closing the GUI
        BREAKING_WORKSTATION,   // Breaking workstation to reset
        FOUND,                  // Desired trade found!
        FAILED                  // Something went wrong
    }
}
```

#### Mixin Addition to MixinClientPlayNetHandler.java
```java
@Inject(
    method = "handleMerchantOffers",
    at = @At("RETURN")
)
private void onMerchantOffersReceived(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
    for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
        LocalPlayer player = ibaritone.getPlayerContext().player();
        if (player != null && player.connection == (ClientPacketListener) (Object) this) {
            ibaritone.getGameEventHandler().onMerchantOffersReceived(
                new MerchantOffersEvent(
                    packet.getContainerId(),
                    packet.getOffers(),
                    packet.getVillagerLevel(),
                    packet.getVillagerXp(),
                    packet.showProgress(),
                    packet.canRestock()
                )
            );
        }
    }
}
```

#### TradeCommand.java (Skeleton)
```java
package baritone.command.defaults;

public class TradeCommand extends Command {
    
    // Enchantment presets - common "best" enchantments for each gear type
    private static final Map<String, List<EnchantmentCriteria>> PRESETS = Map.of(
        "helmet_best", List.of(
            new EnchantmentCriteria("protection", 4),
            new EnchantmentCriteria("mending", null),
            new EnchantmentCriteria("unbreaking", 3),
            new EnchantmentCriteria("aqua_affinity", null),
            new EnchantmentCriteria("respiration", 3)
        ),
        "sword_best", List.of(
            new EnchantmentCriteria("sharpness", 5),
            new EnchantmentCriteria("mending", null),
            new EnchantmentCriteria("unbreaking", 3),
            new EnchantmentCriteria("looting", 3),
            new EnchantmentCriteria("fire_aspect", 2),
            new EnchantmentCriteria("sweeping_edge", 3)  // Java only
        ),
        "pickaxe_best", List.of(
            new EnchantmentCriteria("efficiency", 5),
            new EnchantmentCriteria("mending", null),
            new EnchantmentCriteria("unbreaking", 3),
            new EnchantmentCriteria("fortune", 3),
            new EnchantmentCriteria("silk_touch", null)
        ),
        "all_best", List.of(
            new EnchantmentCriteria("mending", null),
            new EnchantmentCriteria("silk_touch", null),
            new EnchantmentCriteria("fortune", 3),
            new EnchantmentCriteria("sharpness", 5),
            new EnchantmentCriteria("protection", 4),
            new EnchantmentCriteria("efficiency", 5),
            new EnchantmentCriteria("looting", 3),
            new EnchantmentCriteria("unbreaking", 3)
        )
        // ... more presets
    );
    
    public TradeCommand(IBaritone baritone) {
        super(baritone, "trade");
    }
    
    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.getString();
        IVillagerTradeProcess proc = baritone.getVillagerTradeProcess();
        
        switch (action.toLowerCase()) {
            case "setup" -> {
                // #trade setup - enter setup mode
                proc.beginSetup();
                logDirect("Setup mode enabled.");
                logDirect("1. Right-click a villager to select it");
                logDirect("2. Right-click where to place workstation");
            }
            
            case "cycle" -> {
                // #trade cycle <enchantments> [autolock]
                // Examples:
                //   #trade cycle mending
                //   #trade cycle "sharpness 5"
                //   #trade cycle [mending, silk_touch, "protection 4"]
                //   #trade cycle sword_best
                //   #trade cycle mending autolock
                
                // Validate setup is complete
                SetupStatus setup = proc.getSetupStatus();
                if (!setup.isReady()) {
                    throw new CommandInvalidStateException(
                        "Setup not complete. Run #trade setup first.");
                }
                
                // Validate player is holding workstation
                ItemStack heldItem = ctx.player().getMainHandItem();
                if (heldItem.isEmpty() || !isWorkstationBlock(heldItem)) {
                    throw new CommandInvalidStateException(
                        "Hold a workstation block (lectern, smithing_table, etc.) in your main hand.");
                }
                
                // Parse enchantment argument
                String enchantArg = args.getString();
                boolean autolock = false;
                
                // Check for autolock flag at the end
                if (args.hasAny()) {
                    String next = args.peekString();
                    if (next.equalsIgnoreCase("autolock")) {
                        autolock = true;
                        args.getString(); // consume it
                    }
                }
                
                // Parse enchantments (single, array, or preset)
                List<EnchantmentCriteria> criteria = parseEnchantments(enchantArg);
                Predicate<MerchantOffer> predicate = offer -> 
                    criteria.stream().anyMatch(c -> c.matches(offer));
                
                // Get block from player's hand
                Block workstation = ((BlockItem) heldItem.getItem()).getBlock();
                
                // Start cycling
                proc.startCycling(workstation, predicate, autolock);
                
                logDirect("Cycling for: " + formatCriteria(criteria));
                if (autolock) {
                    logDirect("Will auto-lock when found (if you have emeralds + book)");
                }
            }
            
            case "stop" -> {
                proc.stopCycling();
                CycleStats stats = proc.getStats();
                logDirect("Stopped after " + stats.cycleCount() + " cycles (" + 
                         (stats.elapsedMs() / 1000) + "s)");
            }
            
            case "status" -> {
                CycleStats stats = proc.getStats();
                CycleState state = proc.getState();
                
                logDirect("State: " + state);
                logDirect("Cycles: " + stats.cycleCount());
                logDirect("Elapsed: " + formatTime(stats.elapsedMs()));
                
                if (proc.getFoundTrade() != null) {
                    logDirect("§aFOUND: " + formatTrade(proc.getFoundTrade()));
                }
                
                // Show last seen trades
                List<MerchantOffer> lastOffers = proc.getLastSeenOffers();
                if (!lastOffers.isEmpty()) {
                    logDirect("Last seen trades:");
                    for (MerchantOffer offer : lastOffers) {
                        logDirect("  - " + formatTrade(offer));
                    }
                }
            }
            
            case "scan" -> {
                // Manual scan of currently open trade GUI
                List<MerchantOffer> offers = proc.getLastSeenOffers();
                if (offers.isEmpty()) {
                    logDirect("No trades. Open a villager trade window first.");
                } else {
                    logDirect("Current trades:");
                    for (int i = 0; i < offers.size(); i++) {
                        logDirect("[" + i + "] " + formatTrade(offers.get(i)));
                    }
                }
            }
            
            case "presets" -> {
                // List available presets
                logDirect("Available presets:");
                for (String preset : PRESETS.keySet()) {
                    logDirect("  " + preset + " -> " + 
                             PRESETS.get(preset).stream()
                                 .map(EnchantmentCriteria::toString)
                                 .collect(Collectors.joining(", ")));
                }
            }
            
            default -> throw new CommandInvalidTypeException(args.consumed(), 
                "Expected: setup, cycle, stop, status, scan, presets");
        }
    }
    
    private List<EnchantmentCriteria> parseEnchantments(String arg) {
        // Check if it's a preset
        if (PRESETS.containsKey(arg.toLowerCase())) {
            return PRESETS.get(arg.toLowerCase());
        }
        
        // Check if it's an array: [mending, "sharpness 5", silk_touch]
        if (arg.startsWith("[") && arg.endsWith("]")) {
            String inner = arg.substring(1, arg.length() - 1);
            return parseEnchantmentList(inner);
        }
        
        // Single enchantment
        return List.of(parseOneEnchantment(arg));
    }
    
    private List<EnchantmentCriteria> parseEnchantmentList(String listStr) {
        // Parse comma-separated, respecting quotes
        // "mending, \"sharpness 5\", silk_touch"
        List<EnchantmentCriteria> result = new ArrayList<>();
        // ... parsing logic
        return result;
    }
    
    private EnchantmentCriteria parseOneEnchantment(String str) {
        // "mending" -> EnchantmentCriteria("mending", null)
        // "sharpness 5" -> EnchantmentCriteria("sharpness", 5)
        // "protection 4" -> EnchantmentCriteria("protection", 4)
        str = str.trim().replace("\"", "");
        String[] parts = str.split("\\s+");
        String name = parts[0].toLowerCase().replace(" ", "_");
        Integer level = parts.length > 1 ? Integer.parseInt(parts[1]) : null;
        return new EnchantmentCriteria(name, level);
    }
    
    record EnchantmentCriteria(String enchantmentName, @Nullable Integer level) {
        boolean matches(MerchantOffer offer) {
            ItemStack result = offer.getResult();
            if (!result.is(Items.ENCHANTED_BOOK)) return false;
            
            // Get stored enchantments from book
            ListTag enchants = EnchantedBookItem.getEnchantments(result);
            for (int i = 0; i < enchants.size(); i++) {
                CompoundTag tag = enchants.getCompound(i);
                String id = tag.getString("id"); // "minecraft:mending"
                int lvl = tag.getInt("lvl");
                
                // Normalize ID
                String normalized = id.replace("minecraft:", "").toLowerCase();
                
                if (normalized.equals(enchantmentName) || 
                    normalized.equals(enchantmentName.replace("_", ""))) {
                    // Name matches - check level if specified
                    if (level == null || level == lvl) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        @Override
        public String toString() {
            return level != null ? enchantmentName + " " + level : enchantmentName;
        }
    }
    
    private boolean isWorkstationBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        return block == Blocks.LECTERN ||
               block == Blocks.CARTOGRAPHY_TABLE ||
               block == Blocks.BREWING_STAND ||
               block == Blocks.SMITHING_TABLE ||
               block == Blocks.BLAST_FURNACE ||
               block == Blocks.GRINDSTONE ||
               block == Blocks.LOOM ||
               block == Blocks.BARREL ||
               block == Blocks.SMOKER ||
               block == Blocks.COMPOSTER ||
               block == Blocks.FLETCHING_TABLE ||
               block == Blocks.CAULDRON ||
               block == Blocks.STONECUTTER;
    }
}
```

---

## Discussion Notes

*Add notes from design discussions here*

### Session 1 - Initial Planning (Jan 23, 2026)
- Confirmed Baritone has no existing villager/entity interaction
- Identified key Minecraft classes for trading
- Outlined basic state machine and phases
- Decision: Use pure Mixin approach (consistent with codebase, no Fabric API dep)
- Decision: Use input simulation for entity interaction (consistent with GetToBlockProcess)
- Decision: Extend existing `MixinClientPlayNetHandler` for packet hooks

### Session 2 - Use Case Refinement (Jan 23, 2026)
- **Focused on specific use case**: Villager trade cycling (not general trading)
- The workflow: place workstation → check trades → break if bad → repeat
- This is the "rolling for Mending" technique players use manually
- Updated state machine to reflect cycle loop
- Added Minecraft mechanics reference (profession system, workstations)
- Key insight: Villagers must be unemployed AND not yet traded with to reset

### Session 3 - Command Syntax & Presets (Jan 23, 2026)
- **Multiple enchantments**: Support array syntax `[mending, "sharpness 5", silk_touch]`
- **Presets**: Added `helmet_best`, `sword_best`, `pickaxe_best`, etc. for quick setup
- **Workstation handling**: Player MUST have lectern selected in hotbar (keep it simple)
- **Auto-lock**: Optional argument `autolock` instead of a setting
- Decided syntax rules (case insensitive, underscores = spaces, quotes for levels)
- **Refocused on Librarians**: This feature is specifically for cycling librarian enchanted books
- **GUI Closing**: Use `player.closeContainer()` - proper method that sends packet
- **Timeout handling**: If villager doesn't claim workstation, timeout and break/replace
- Added `#trade presets` command to list available presets

### Key Decisions Made
1. **Platform**: Fabric Loader required (already the case)
2. **Fabric API**: Not required initially (pure Mixin approach)
3. **Primary Use Case**: **Librarian enchanted book cycling** (not general trading)
4. **Setup Method**: Click-to-set (player clicks villager, clicks lectern position)
5. **Trade Detection**: Packet listener (`handleMerchantOffers`)
6. **Enchant Syntax**: 
   - Single: `mending`, `"sharpness 5"`
   - Multiple: `[mending, silk_touch, "protection 4"]`
   - Presets: `sword_best`, `helmet_best`, `all_best`
7. **Workstation**: Must be **Lectern** in player's hand
8. **Auto-lock**: Command argument `autolock`, not a setting
9. **GUI Closing**: `player.closeContainer()` method
10. **Claim Timeout**: Timeout + break/replace if villager doesn't claim

### Open Design Decisions
- [x] ~~Decide: Auto-lock trades when found, or just notify?~~ → Command arg `autolock`
- [x] ~~Decide: How to handle villager not claiming workstation?~~ → Timeout + break/replace
- [x] ~~Decide: How to close trade GUI~~ → `player.closeContainer()`
- [ ] Decide: Default timing values for each action (testing needed)

---

## Next Steps

### Design Phase (Current)
1. [ ] Finalize the click-to-set setup flow
2. [ ] Decide on all open questions above
3. [ ] Validate timing assumptions (may need testing)
4. [ ] Review enchantment parsing approach

### Implementation Phase (After Design)
1. [ ] Implement `MerchantOffersEvent` and wire up mixin
2. [ ] Implement villager interaction (look at + right-click)
3. [ ] Implement workstation place/break
4. [ ] Implement profession detection (poll villager data)
5. [ ] Create `VillagerTradeProcess` state machine
6. [ ] Add enchantment matching logic
7. [ ] Create `#trade` command with subcommands
8. [ ] Add timing settings to `Settings.java`
9. [ ] Testing & edge case handling

---

## References

- [Minecraft Protocol - Merchant Packets](https://wiki.vg/Protocol#Set_Trade_Offers)
- [Baritone API Javadocs](https://baritone.leijurv.com/)
- [Fabric Loader Docs](https://fabricmc.net/wiki/documentation)
- Existing processes: `FollowProcess`, `GetToBlockProcess`, `FarmProcess`
- Existing mixin patterns: `MixinClientPlayNetHandler`, `MixinMinecraft`
