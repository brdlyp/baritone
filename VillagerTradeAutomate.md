# Librarian Enchanted Book Cycling

## Overview

This document describes the **villager trade cycling automation** feature in this Baritone fork.

**Primary Goal:** Automate the tedious process of cycling a librarian villager's trades to find specific enchanted books (like Mending, Sharpness V, Protection IV, etc.)

**Target Platform:** Minecraft 1.21.8 with Fabric Loader

---

## Quick Start

```
1. Look at an unemployed villager and run:  #trade setvil
2. Look at where to place the lectern and run:  #trade setpos
3. Have a lectern in your hotbar (will be auto-selected)
4. Run:  #trade cycle mending
```

---

## Commands

### Setup Commands

| Command | Description |
|---------|-------------|
| `#trade setup` | Show setup instructions and current status |
| `#trade setvil` | Select the villager you're currently looking at |
| `#trade setpos` | Select the block position you're looking at for workstation placement |

### Cycling Commands

| Command | Description |
|---------|-------------|
| `#trade cycle <enchantment>` | Start cycling until the specified enchantment is found |
| `#trade cycle <enchantment> autolock` | Cycle and auto-buy to lock the trade when found |
| `#trade cycle <enchantment> -human` | Enable human-like randomized behavior |
| `#trade stop` | Stop cycling |

### Info Commands

| Command | Description |
|---------|-------------|
| `#trade status` | Show cycle count, time elapsed, last trades seen |
| `#trade scan` | Show cached trades from the last opened trade GUI |
| `#trade presets` | List all available enchantment presets |

---

## Enchantment Syntax

### Single Enchantment

```
#trade cycle mending              - Any level (Mending only has 1)
#trade cycle efficiency           - Any level of Efficiency
#trade cycle "sharpness 5"        - Specifically Sharpness V
#trade cycle "protection 4"       - Specifically Protection IV
```

### Multiple Enchantments (OR logic)

```
#trade cycle [mending, silk_touch]
#trade cycle [mending, "sharpness 5", "protection 4"]
```

### Presets

```
#trade cycle sword_best           - Any top-tier sword enchant
#trade cycle helmet_best          - Any top-tier helmet enchant
#trade cycle all_best             - Any "god tier" enchantment
```

**Available Presets:**

| Preset | Enchantments |
|--------|--------------|
| `helmet_best` | protection 4, mending, unbreaking 3, aqua_affinity, respiration 3 |
| `chestplate_best` | protection 4, mending, unbreaking 3 |
| `leggings_best` | protection 4, mending, unbreaking 3, swift_sneak 3 |
| `boots_best` | protection 4, mending, unbreaking 3, feather_falling 4, depth_strider 3, soul_speed 3 |
| `sword_best` | sharpness 5, mending, unbreaking 3, looting 3, fire_aspect 2, sweeping_edge 3 |
| `pickaxe_best` | efficiency 5, mending, unbreaking 3, fortune 3, silk_touch |
| `axe_best` | efficiency 5, mending, unbreaking 3, sharpness 5 |
| `shovel_best` | efficiency 5, mending, unbreaking 3, silk_touch |
| `bow_best` | power 5, mending, unbreaking 3, infinity, flame |
| `crossbow_best` | quick_charge 3, mending, unbreaking 3, multishot, piercing 4 |
| `trident_best` | mending, unbreaking 3, riptide 3, loyalty 3, channeling, impaling 5 |
| `fishing_best` | luck_of_the_sea 3, lure 3, mending, unbreaking 3 |
| `elytra_best` | mending, unbreaking 3 |
| `all_best` | mending, silk_touch, fortune 3, sharpness 5, protection 4, efficiency 5, looting 3, unbreaking 3 |
| `utility` | mending, silk_touch, fortune 3, infinity, looting 3 |

---

## Command Flags

| Flag | Description |
|------|-------------|
| `autolock` | Automatically make one trade to lock the profession when found (not yet implemented) |
| `-human` or `human` | Enable human-like behavior with randomized delays and look movements |

### Human Mode

When `-human` flag is enabled:
- Random delays between actions (0.25-1 second before moving, 0.15-0.75 seconds before interactions)
- Slight random offsets when looking at entities (appears more natural)
- Occasional longer pauses (2-5 seconds) simulating player checking phone, etc.
- ~5% chance of taking a "human pause" between cycles

---

## How It Works

### The Automated Cycle

```
┌─────────────────────────────────────────────────────────────────┐
│                    AUTOMATED TRADE CYCLING                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. PLACE WORKSTATION                                            │
│     └─► Places lectern at the configured position                │
│     └─► Villager claims job (becomes Librarian)                  │
│                                                                  │
│  2. WAIT FOR PROFESSION                                          │
│     └─► Wait for villager to pathfind and claim workstation      │
│     └─► Timeout and retry if villager doesn't claim              │
│                                                                  │
│  3. MOVE TO VILLAGER                                             │
│     └─► Path to within interaction range                         │
│                                                                  │
│  4. OPEN TRADE GUI                                               │
│     └─► Look at villager and right-click to interact             │
│                                                                  │
│  5. READ TRADES                                                  │
│     └─► Check trades against desired enchantment predicate       │
│     ├─► MATCH FOUND? ──► Stop, notify player, optional auto-lock │
│     └─► NO MATCH? ──► Continue to step 6                         │
│                                                                  │
│  6. CLOSE GUI & BREAK WORKSTATION                                │
│     └─► Close trade interface                                    │
│     └─► Break the lectern block                                  │
│                                                                  │
│  7. WAIT FOR RESET                                               │
│     └─► Wait for villager to lose profession                     │
│     └─► Go back to step 1                                        │
│                                                                  │
│  8. COLLECT ITEM (if needed)                                     │
│     └─► If lectern dropped as item, walk to collect it           │
│     └─► Disables block breaking to avoid destroying builds       │
│                                                                  │
│  ∞  REPEAT until desired trade appears                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### State Machine

| State | Description |
|-------|-------------|
| `IDLE` | Not cycling, waiting for command |
| `PLACING_WORKSTATION` | Placing the lectern block |
| `WAITING_FOR_PROFESSION` | Waiting for villager to claim the workstation |
| `MOVING_TO_VILLAGER` | Pathing to get within interaction range |
| `OPENING_TRADE_GUI` | Looking at and interacting with villager |
| `READING_TRADES` | Checking trade offers against criteria |
| `CLOSING_TRADE_GUI` | Closing the merchant screen |
| `BREAKING_WORKSTATION` | Breaking the lectern to reset |
| `WAITING_FOR_RESET` | Waiting for villager to become unemployed |
| `COLLECTING_ITEM` | Picking up dropped workstation block |
| `FOUND` | Desired trade found! Process complete |
| `AUTO_LOCKING` | Making a trade to lock profession (not yet implemented) |
| `FAILED` | Error occurred |

---

## Requirements

Before running `#trade cycle`:

1. **Complete setup:**
   - Run `#trade setvil` while looking at an **unemployed** villager
   - Run `#trade setpos` while looking at the workstation placement position

2. **Have workstation in hotbar:**
   - Lectern (for Librarian) - prioritized if multiple workstations present
   - Or other workstation blocks for other professions
   - Will be auto-selected when cycling starts

3. **Villager must be:**
   - Unemployed (brown coat, no profession)
   - **Not yet traded with** (profession locks after first trade)
   - Within reasonable distance of the workstation position

---

## Settings

These settings can be modified in Baritone's settings system:

| Setting | Default | Description |
|---------|---------|-------------|
| `villagerWorkstationClaimTicks` | 40 | Ticks to wait for villager to claim workstation |
| `villagerProfessionWaitTicks` | 5 | Ticks to wait after profession gained |
| `villagerTradeReadDelayTicks` | 5 | Ticks to wait after opening GUI to read trades |
| `villagerTradeCloseDelayTicks` | 3 | Ticks to wait after closing GUI |
| `villagerWorkstationBreakDelayTicks` | 10 | Ticks to wait after breaking workstation |
| `villagerClaimTimeoutTicks` | 100 | Max ticks to wait for workstation claim before retry |
| `villagerMaxCycles` | -1 | Maximum cycles before stopping (-1 = unlimited) |
| `villagerTradeFoundSound` | true | Play bell sound when desired trade is found |

---

## Example Sessions

### Example 1: Finding Mending

```
Player: #trade setvil                   (while looking at villager)
Bot:    Villager selected at [100, 64, 200]
        Now look at where to place the workstation and run: #trade setpos

Player: #trade setpos                   (while looking at ground)
Bot:    Workstation position set to [101, 65, 200]
        Setup complete! Have a lectern in your hotbar and run: #trade cycle <enchantment>

Player: [has Lectern in hotbar]
Player: #trade cycle mending
Bot:    Auto-selected lectern from hotbar slot 3
        Starting trade cycling...
        Looking for matching trade. Will cycle until found.
        Cycle 1: efficiency 3 (14 emeralds)
        Cycle 2: No book trade
        Cycle 3: unbreaking 2 (12 emeralds)
        ...
        Cycle 47: mending 1 (24 emeralds)
        ✓ FOUND after 47 cycles (2m 34s):
          Enchanted Book - 24 emeralds
```

### Example 2: Human Mode

```
Player: #trade cycle mending -human
Bot:    Starting trade cycling...
        Looking for matching trade. Will cycle until found.
        Human mode enabled: randomized delays and movements
        Cycle 1: fire_aspect 2 (20 emeralds)
        Cycle 2: silk_touch 1 (19 emeralds)
        (Taking a brief pause...)
        Cycle 3: fortune 2 (16 emeralds)
        ...
```

### Example 3: Multiple Enchantments

```
Player: #trade cycle [mending, silk_touch, "fortune 3"]
Bot:    Cycling for: mending, silk_touch, fortune 3
        Cycle 1: protection 3 (8 emeralds)
        Cycle 2: efficiency 4 (24 emeralds)
        Cycle 3: silk_touch 1 (18 emeralds)
        ✓ FOUND after 3 cycles (15s):
          Enchanted Book - 18 emeralds
```

---

## Technical Details

### Files Modified/Added

| File | Type | Purpose |
|------|------|---------|
| `IVillagerTradeProcess.java` | API | Public interface for trade automation |
| `VillagerTradeProcess.java` | Process | Main state machine implementation |
| `TradeCommand.java` | Command | Chat command handler |
| `MerchantOffersEvent.java` | Event | Event for trade offer packets |
| `MixinClientPlayNetHandler.java` | Mixin | Packet hook for merchant offers |
| `IGameEventListener.java` | API | Added `onMerchantOffersReceived` method |
| `GameEventHandler.java` | Handler | Event dispatching |
| `Settings.java` | Config | Added villager trade settings |
| `Baritone.java` | Core | Added VillagerTradeProcess to processes |

### Key Implementation Details

1. **Villager Tracking:** Uses UUID comparison instead of entity reference (more reliable across chunk loading)

2. **Entity Interaction:** Uses `gameMode.interact()` directly instead of input override for more reliable villager interaction

3. **Trade Detection:** Hooks `ClientboundMerchantOffersPacket` via mixin to capture trade data when GUI opens

4. **Item Collection:** Automatically collects dropped workstation items; temporarily disables `allowBreak` to prevent destroying player builds while pathing to items

---

## Known Limitations

1. **Auto-lock not implemented:** The `autolock` flag is parsed but the actual trading logic is not yet implemented

2. **Single villager only:** Cannot cycle multiple villagers simultaneously

3. **Requires direct line of sight:** Villager must be able to pathfind to workstation

4. **No custom presets:** Cannot define custom presets in config (presets are hardcoded)

---

## Troubleshooting

### "Villager's profession appears to be locked"
The villager has already been traded with. You need an **unemployed villager that has never been traded with**.

### "Timeout waiting for villager to claim workstation"
- Make sure the workstation position is within 48 blocks of the villager
- Ensure there's no block between the villager and workstation

### "No workstation block found in hotbar"
- Make sure you have a workstation (lectern) in your hotbar (slots 1-9)
- If it dropped as an item, the bot will try to collect it automatically

### Villager keeps going to a different workstation
Another workstation of the same type might be nearby. Break other workstations or move further away.

---

## Future Enhancements

- [ ] Implement auto-lock trading
- [ ] Support cycling multiple villagers in parallel
- [ ] Custom preset configuration via file
- [ ] Best price search across multiple villagers
- [ ] Auto-setup (find unemployed villager, create trading cell)
