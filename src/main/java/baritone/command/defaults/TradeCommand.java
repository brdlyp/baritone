/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.process.IVillagerTradeProcess;
import baritone.api.process.IVillagerTradeProcess.CycleState;
import baritone.api.process.IVillagerTradeProcess.CycleStats;
import baritone.api.process.IVillagerTradeProcess.SetupStatus;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command for automating villager trade cycling.
 * <p>
 * Usage:
 * - #trade setup - Enter setup mode (click villager, then click position)
 * - #trade cycle <enchantment> [autolock] - Start cycling for specified enchantment
 * - #trade stop - Stop cycling
 * - #trade status - Show current status
 * - #trade scan - Show current trades in open GUI
 * - #trade presets - List available presets
 *
 * @author Brady
 * @since 1/23/2026
 */
public class TradeCommand extends Command {

    // Enchantment presets for common "best" enchantments
    private static final Map<String, List<EnchantmentCriteria>> PRESETS = new HashMap<>();

    static {
        PRESETS.put("helmet_best", List.of(
                new EnchantmentCriteria("protection", 4),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("aqua_affinity", null),
                new EnchantmentCriteria("respiration", 3)
        ));
        PRESETS.put("chestplate_best", List.of(
                new EnchantmentCriteria("protection", 4),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3)
        ));
        PRESETS.put("leggings_best", List.of(
                new EnchantmentCriteria("protection", 4),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("swift_sneak", 3)
        ));
        PRESETS.put("boots_best", List.of(
                new EnchantmentCriteria("protection", 4),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("feather_falling", 4),
                new EnchantmentCriteria("depth_strider", 3),
                new EnchantmentCriteria("soul_speed", 3)
        ));
        PRESETS.put("sword_best", List.of(
                new EnchantmentCriteria("sharpness", 5),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("looting", 3),
                new EnchantmentCriteria("fire_aspect", 2),
                new EnchantmentCriteria("sweeping_edge", 3)
        ));
        PRESETS.put("pickaxe_best", List.of(
                new EnchantmentCriteria("efficiency", 5),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("fortune", 3),
                new EnchantmentCriteria("silk_touch", null)
        ));
        PRESETS.put("axe_best", List.of(
                new EnchantmentCriteria("efficiency", 5),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("sharpness", 5)
        ));
        PRESETS.put("shovel_best", List.of(
                new EnchantmentCriteria("efficiency", 5),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("silk_touch", null)
        ));
        PRESETS.put("bow_best", List.of(
                new EnchantmentCriteria("power", 5),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("infinity", null),
                new EnchantmentCriteria("flame", null)
        ));
        PRESETS.put("crossbow_best", List.of(
                new EnchantmentCriteria("quick_charge", 3),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("multishot", null),
                new EnchantmentCriteria("piercing", 4)
        ));
        PRESETS.put("trident_best", List.of(
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3),
                new EnchantmentCriteria("riptide", 3),
                new EnchantmentCriteria("loyalty", 3),
                new EnchantmentCriteria("channeling", null),
                new EnchantmentCriteria("impaling", 5)
        ));
        PRESETS.put("fishing_best", List.of(
                new EnchantmentCriteria("luck_of_the_sea", 3),
                new EnchantmentCriteria("lure", 3),
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3)
        ));
        PRESETS.put("elytra_best", List.of(
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("unbreaking", 3)
        ));
        PRESETS.put("all_best", List.of(
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("silk_touch", null),
                new EnchantmentCriteria("fortune", 3),
                new EnchantmentCriteria("sharpness", 5),
                new EnchantmentCriteria("protection", 4),
                new EnchantmentCriteria("efficiency", 5),
                new EnchantmentCriteria("looting", 3),
                new EnchantmentCriteria("unbreaking", 3)
        ));
        PRESETS.put("utility", List.of(
                new EnchantmentCriteria("mending", null),
                new EnchantmentCriteria("silk_touch", null),
                new EnchantmentCriteria("fortune", 3),
                new EnchantmentCriteria("infinity", null),
                new EnchantmentCriteria("looting", 3)
        ));
    }

    public TradeCommand(IBaritone baritone) {
        super(baritone, "trade");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String action = args.getString().toLowerCase();
        IVillagerTradeProcess proc = baritone.getVillagerTradeProcess();

        switch (action) {
            case "setup" -> {
                // Show setup instructions
                logDirect("Trade Setup Instructions:");
                logDirect("  1. Look at a villager and run: #trade setvil");
                logDirect("  2. Look at where to place workstation and run: #trade setpos");
                logDirect("  3. Hold a lectern in your hand");
                logDirect("  4. Run: #trade cycle <enchantment>");
                
                // Show current setup status
                SetupStatus setup = proc.getSetupStatus();
                logDirect("");
                logDirect("Current setup:");
                logDirect("  Villager: " + (setup.hasVillager() ? "Selected at " + setup.villager().blockPosition() : "Not set"));
                logDirect("  Position: " + (setup.hasWorkstationPos() ? setup.workstationPos().toShortString() : "Not set"));
            }

            case "setvil", "setvillager" -> {
                // Select the villager the player is looking at
                if (proc.selectLookedAtVillager()) {
                    SetupStatus setup = proc.getSetupStatus();
                    logDirect("Villager selected at " + setup.villager().blockPosition());
                    if (!setup.hasWorkstationPos()) {
                        logDirect("Now look at where to place the workstation and run: #trade setpos");
                    } else {
                        logDirect("Setup complete! Hold a lectern and run: #trade cycle <enchantment>");
                    }
                } else {
                    logDirect("Error: Look at a villager first!");
                }
            }

            case "setpos", "setposition" -> {
                // Select the position the player is looking at
                if (proc.selectLookedAtPosition()) {
                    SetupStatus setup = proc.getSetupStatus();
                    logDirect("Workstation position set to " + setup.workstationPos().toShortString());
                    if (!setup.hasVillager()) {
                        logDirect("Now look at a villager and run: #trade setvil");
                    } else {
                        logDirect("Setup complete! Hold a lectern and run: #trade cycle <enchantment>");
                    }
                } else {
                    logDirect("Error: Look at a block first!");
                }
            }

            case "cycle" -> {
                // #trade cycle <enchantment> [autolock]
                args.requireMin(1);

                // Validate setup is complete
                SetupStatus setup = proc.getSetupStatus();
                if (!setup.hasVillager()) {
                    throw new CommandInvalidStateException("No villager selected. Run #trade setup first.");
                }
                if (!setup.hasWorkstationPos()) {
                    throw new CommandInvalidStateException("No workstation position set. Run #trade setup first.");
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

                // Start cycling
                proc.startCycling(predicate, autolock);

                logDirect("Cycling for: " + formatCriteria(criteria));
                if (autolock) {
                    logDirect("Will auto-lock when found (if you have emeralds + book)");
                }
            }

            case "stop" -> {
                proc.stopCycling();
            }

            case "status" -> {
                CycleStats stats = proc.getStats();
                CycleState state = proc.getState();

                logDirect("State: " + state);
                logDirect("Cycles: " + stats.cycleCount());
                logDirect("Trades checked: " + stats.tradesChecked());
                logDirect("Elapsed: " + stats.getElapsedFormatted());

                if (proc.getFoundTrade() != null) {
                    logDirect("§aFOUND: " + formatTrade(proc.getFoundTrade()));
                }

                // Show setup status
                SetupStatus setup = proc.getSetupStatus();
                logDirect("Setup - Villager: " + (setup.hasVillager() ? "Yes" : "No") +
                        ", Position: " + (setup.hasWorkstationPos() ? setup.workstationPos() : "Not set"));

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
                    logDirect("No trades cached. Open a villager trade window first.");
                } else {
                    logDirect("Cached trades:");
                    for (int i = 0; i < offers.size(); i++) {
                        logDirect("[" + i + "] " + formatTrade(offers.get(i)));
                    }
                }
            }

            case "presets" -> {
                // List available presets
                logDirect("Available presets:");
                for (String preset : PRESETS.keySet()) {
                    String enchants = PRESETS.get(preset).stream()
                            .map(EnchantmentCriteria::toString)
                            .collect(Collectors.joining(", "));
                    logDirect("  " + preset + " -> " + enchants);
                }
            }

            default -> throw new CommandInvalidTypeException(args.consumed(),
                    "Expected: setup, cycle, stop, status, scan, presets");
        }
    }

    private List<EnchantmentCriteria> parseEnchantments(String arg) throws CommandException {
        // Check if it's a preset
        String lower = arg.toLowerCase();
        if (PRESETS.containsKey(lower)) {
            return PRESETS.get(lower);
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
        List<EnchantmentCriteria> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : listStr.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                String part = current.toString().trim();
                if (!part.isEmpty()) {
                    result.add(parseOneEnchantment(part));
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        // Don't forget the last element
        String part = current.toString().trim();
        if (!part.isEmpty()) {
            result.add(parseOneEnchantment(part));
        }

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

    private String formatCriteria(List<EnchantmentCriteria> criteria) {
        return criteria.stream()
                .map(EnchantmentCriteria::toString)
                .collect(Collectors.joining(", "));
    }

    private String formatTrade(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        ItemStack cost = offer.getCostA();
        String resultName = result.getHoverName().getString();
        int emeraldCost = cost.getCount();

        // If it's an enchanted book, show the enchantment
        if (result.is(Items.ENCHANTED_BOOK)) {
            // Use Data Components API (1.21+)
            ItemEnchantments storedEnchants = result.get(DataComponents.STORED_ENCHANTMENTS);
            if (storedEnchants != null && !storedEnchants.isEmpty()) {
                // Get the first enchantment
                for (Holder<Enchantment> enchant : storedEnchants.keySet()) {
                    int lvl = storedEnchants.getLevel(enchant);
                    String enchantName = enchant.getRegisteredName();
                    if (enchantName != null) {
                        enchantName = enchantName.replace("minecraft:", "");
                    } else {
                        enchantName = enchant.value().description().getString();
                    }
                    return enchantName + " " + lvl + " - " + emeraldCost + " emeralds";
                }
            }
        }

        return resultName + " - " + emeraldCost + " emeralds";
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String prefix = args.peekString().toLowerCase();
            return Stream.of("setup", "setvil", "setpos", "cycle", "stop", "status", "scan", "presets")
                    .filter(s -> s.startsWith(prefix));
        }

        if (args.has(2)) {
            String action = args.getString().toLowerCase();
            if (action.equals("cycle") && args.hasExactlyOne()) {
                // Tab complete enchantment names and presets
                String prefix = args.peekString().toLowerCase();
                Stream<String> presetStream = PRESETS.keySet().stream();
                Stream<String> commonEnchants = Stream.of(
                        "mending", "unbreaking", "silk_touch", "fortune",
                        "sharpness", "protection", "efficiency", "looting",
                        "feather_falling", "fire_aspect", "power", "infinity"
                );
                return Stream.concat(presetStream, commonEnchants)
                        .filter(s -> s.startsWith(prefix));
            }
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Automate villager trade cycling";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The trade command automates villager trade cycling to find specific enchanted books.",
                "",
                "Setup:",
                "  #trade setup - Show setup instructions and current status",
                "  #trade setvil - Select the villager you're looking at",
                "  #trade setpos - Select the block position you're looking at",
                "",
                "Usage:",
                "  #trade cycle mending - Cycle until Mending book found",
                "  #trade cycle \"sharpness 5\" - Cycle until Sharpness V",
                "  #trade cycle [mending, silk_touch] - Cycle for multiple enchants",
                "  #trade cycle sword_best - Use a preset",
                "  #trade cycle mending autolock - Auto-buy to lock profession",
                "",
                "Control:",
                "  #trade stop - Stop cycling",
                "  #trade status - Show cycle count, time, last trades",
                "  #trade presets - List available presets",
                "",
                "Requirements:",
                "  - Run #trade setvil while looking at a villager",
                "  - Run #trade setpos while looking at workstation placement spot",
                "  - Hold workstation block (lectern) in main hand"
        );
    }

    /**
     * Criteria for matching an enchantment on a book.
     */
    private record EnchantmentCriteria(String enchantmentName, @Nullable Integer level) {

        boolean matches(MerchantOffer offer) {
            ItemStack result = offer.getResult();
            if (!result.is(Items.ENCHANTED_BOOK)) {
                return false;
            }

            // Use Data Components API (1.21+) to get stored enchantments
            ItemEnchantments storedEnchants = result.get(DataComponents.STORED_ENCHANTMENTS);
            if (storedEnchants == null || storedEnchants.isEmpty()) {
                return false;
            }

            for (Holder<Enchantment> enchant : storedEnchants.keySet()) {
                int lvl = storedEnchants.getLevel(enchant);

                // Get enchantment name from the registry
                String registeredName = enchant.getRegisteredName();
                String normalized;
                if (registeredName != null) {
                    normalized = registeredName.replace("minecraft:", "").toLowerCase();
                } else {
                    // Fallback to description
                    normalized = enchant.value().description().getString().toLowerCase().replace(" ", "_");
                }

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
}
