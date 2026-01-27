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

    // Valid enchantment identifiers (resource location style) with their max levels
    private static final Map<String, Integer> ENCHANTMENT_MAX_LEVELS = Map.ofEntries(
            // Max level 1
            Map.entry("aqua_affinity", 1),
            Map.entry("binding_curse", 1),
            Map.entry("channeling", 1),
            Map.entry("flame", 1),
            Map.entry("infinity", 1),
            Map.entry("mending", 1),
            Map.entry("multishot", 1),
            Map.entry("silk_touch", 1),
            Map.entry("vanishing_curse", 1),
            // Max level 2
            Map.entry("fire_aspect", 2),
            Map.entry("frost_walker", 2),
            Map.entry("knockback", 2),
            Map.entry("punch", 2),
            // Max level 3
            Map.entry("depth_strider", 3),
            Map.entry("fortune", 3),
            Map.entry("looting", 3),
            Map.entry("loyalty", 3),
            Map.entry("luck_of_the_sea", 3),
            Map.entry("lunge", 3),
            Map.entry("lure", 3),
            Map.entry("quick_charge", 3),
            Map.entry("respiration", 3),
            Map.entry("riptide", 3),
            Map.entry("soul_speed", 3),
            Map.entry("sweeping_edge", 3),
            Map.entry("swift_sneak", 3),
            Map.entry("thorns", 3),
            Map.entry("unbreaking", 3),
            Map.entry("wind_burst", 3),
            // Max level 4
            Map.entry("blast_protection", 4),
            Map.entry("breach", 4),
            Map.entry("feather_falling", 4),
            Map.entry("fire_protection", 4),
            Map.entry("piercing", 4),
            Map.entry("projectile_protection", 4),
            Map.entry("protection", 4),
            // Max level 5
            Map.entry("bane_of_arthropods", 5),
            Map.entry("density", 5),
            Map.entry("efficiency", 5),
            Map.entry("impaling", 5),
            Map.entry("power", 5),
            Map.entry("sharpness", 5),
            Map.entry("smite", 5)
    );

    // Valid enchantment identifiers (derived from the max levels map)
    private static final Set<String> VALID_ENCHANTMENTS = ENCHANTMENT_MAX_LEVELS.keySet();

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
                logDirect("  3. Have a lectern in your hotbar (auto-selected)");
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
                        logDirect("Setup complete! Have a lectern in your hotbar and run: #trade cycle <enchantment>");
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
                        logDirect("Setup complete! Have a lectern in your hotbar and run: #trade cycle <enchantment>");
                    }
                } else {
                    logDirect("Error: Look at a block first!");
                }
            }

            case "cycle" -> {
                // #trade cycle <enchantment> [autolock] [-human]
                args.requireMin(1);

                // Validate setup is complete
                SetupStatus setup = proc.getSetupStatus();
                if (!setup.hasVillager()) {
                    throw new CommandInvalidStateException("No villager selected. Run #trade setup first.");
                }
                if (!setup.hasWorkstationPos()) {
                    throw new CommandInvalidStateException("No workstation position set. Run #trade setup first.");
                }

                // Parse enchantment argument - need to handle arrays with spaces
                // e.g., [protection:4, mending, thorns:3] gets split by the arg parser
                String enchantArg = args.getString();
                
                // If it starts with [ but doesn't end with ], keep consuming until we find ]
                if (enchantArg.startsWith("[") && !enchantArg.endsWith("]")) {
                    StringBuilder sb = new StringBuilder(enchantArg);
                    while (args.hasAny() && !sb.toString().endsWith("]")) {
                        String next = args.peekString();
                        // Stop if we hit a flag
                        if (next.equalsIgnoreCase("autolock") || next.startsWith("-")) {
                            break;
                        }
                        sb.append(args.getString());
                    }
                    enchantArg = sb.toString();
                }
                
                // Same for enchantments={...} syntax
                if (enchantArg.startsWith("enchantments={") && !enchantArg.endsWith("}")) {
                    StringBuilder sb = new StringBuilder(enchantArg);
                    while (args.hasAny() && !sb.toString().endsWith("}")) {
                        String next = args.peekString();
                        // Stop if we hit a flag
                        if (next.equalsIgnoreCase("autolock") || next.startsWith("-")) {
                            break;
                        }
                        sb.append(args.getString());
                    }
                    enchantArg = sb.toString();
                }
                boolean autolock = false;
                boolean humanMode = false;

                // Check for optional flags at the end
                while (args.hasAny()) {
                    String next = args.peekString().toLowerCase();
                    if (next.equals("autolock")) {
                        autolock = true;
                        args.getString(); // consume it
                    } else if (next.equals("-human") || next.equals("human")) {
                        humanMode = true;
                        args.getString(); // consume it
                    } else {
                        break; // Unknown flag, stop parsing
                    }
                }

                // Parse enchantments (single, array, or preset)
                List<EnchantmentCriteria> criteria = parseEnchantments(enchantArg);
                Predicate<MerchantOffer> predicate = offer ->
                        criteria.stream().anyMatch(c -> c.matches(offer));

                // Start cycling
                proc.startCycling(predicate, autolock, humanMode);

                logDirect("Cycling for: " + formatCriteria(criteria));
                if (autolock) {
                    logDirect("Will auto-lock when found (if you have emeralds + book)");
                }
                if (humanMode) {
                    logDirect("Human mode enabled: randomized delays and movements");
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

        // Check if it's the new format: enchantments={mending:1, protection:4}
        if (arg.startsWith("enchantments={") && arg.endsWith("}")) {
            String inner = arg.substring("enchantments={".length(), arg.length() - 1);
            return parseEnchantmentList(inner);
        }

        // Check if it's an array: [mending, silk_touch, protection:4]
        if (arg.startsWith("[") && arg.endsWith("]")) {
            String inner = arg.substring(1, arg.length() - 1);
            return parseEnchantmentList(inner);
        }

        // Single enchantment: mending or protection:4
        return List.of(parseOneEnchantment(arg));
    }

    private List<EnchantmentCriteria> parseEnchantmentList(String listStr) throws CommandException {
        // Parse comma-separated enchantments: mending:1, protection:4, silk_touch
        List<EnchantmentCriteria> result = new ArrayList<>();

        for (String part : listStr.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(parseOneEnchantment(trimmed));
            }
        }

        return result;
    }

    private EnchantmentCriteria parseOneEnchantment(String str) throws CommandException {
        // Parse resource location style: "mending" or "mending:1" or "protection:4"
        str = str.trim().toLowerCase();

        String name;
        Integer level = null;

        if (str.contains(":")) {
            // Format: enchantment:level (e.g., "protection:4")
            String[] parts = str.split(":");
            name = parts[0];
            if (parts.length > 1 && !parts[1].isEmpty()) {
                try {
                    level = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    // Invalid level, treat as enchantment name only
                    name = str;
                }
            }
        } else {
            // Format: just enchantment name (e.g., "mending")
            name = str;
        }

        // Validate enchantment name
        if (!VALID_ENCHANTMENTS.contains(name)) {
            throw new CommandInvalidStateException(
                    "Unknown enchantment '" + name + "'. Use #trade presets to see valid enchantment names."
            );
        }

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
                String current = args.peekString();
                return getEnchantmentCompletions(current);
            }
        }

        return Stream.empty();
    }

    /**
     * Get tab completions for enchantment input, supporting:
     * - Simple enchantment names: "mend" -> "mending"
     * - Array format: "[mending," -> "[mending,protection", "[mending,prot" -> "[mending,protection"
     * - Enchantments with levels: "protection:" -> "protection:1", "protection:2", etc.
     */
    private Stream<String> getEnchantmentCompletions(String current) {
        String lower = current.toLowerCase();

        // Check if we're inside bracket notation [...]
        if (lower.startsWith("[")) {
            return getArrayCompletions(current, "[", "]");
        }

        // Check if we're inside enchantments={...} notation
        if (lower.startsWith("enchantments={")) {
            return getArrayCompletions(current, "enchantments={", "}");
        }

        // Simple single enchantment or preset completion
        return getSingleEnchantmentCompletions(lower, "");
    }

    /**
     * Handle completions inside array notation like [...] or enchantments={...}
     */
    private Stream<String> getArrayCompletions(String current, String openBracket, String closeBracket) {
        String inner;
        boolean isClosed = current.endsWith(closeBracket);

        if (isClosed) {
            inner = current.substring(openBracket.length(), current.length() - closeBracket.length());
        } else {
            inner = current.substring(openBracket.length());
        }

        // Find the last comma to get the current enchantment being typed
        int lastComma = inner.lastIndexOf(',');
        String prefix;
        String beforeCurrent;

        if (lastComma >= 0) {
            beforeCurrent = inner.substring(0, lastComma + 1);
            prefix = inner.substring(lastComma + 1).trim().toLowerCase();
        } else {
            beforeCurrent = "";
            prefix = inner.trim().toLowerCase();
        }

        // Get completions for the current enchantment
        return getSingleEnchantmentCompletions(prefix, "")
                .map(completion -> openBracket + beforeCurrent + (lastComma >= 0 ? " " : "") + completion);
    }

    /**
     * Get completions for a single enchantment name, with optional level suggestions.
     */
    private Stream<String> getSingleEnchantmentCompletions(String prefix, String wrapperPrefix) {
        // Check if we're completing a level (e.g., "protection:" or "protection:3")
        if (prefix.contains(":")) {
            String[] parts = prefix.split(":", 2);
            String enchantName = parts[0];

            // Verify the enchantment name is valid and get its max level
            Integer maxLevel = ENCHANTMENT_MAX_LEVELS.get(enchantName);
            if (maxLevel != null) {
                String levelPrefix = parts.length > 1 ? parts[1] : "";
                // Suggest levels 1 to maxLevel for this enchantment
                return java.util.stream.IntStream.rangeClosed(1, maxLevel)
                        .mapToObj(String::valueOf)
                        .filter(lvl -> lvl.startsWith(levelPrefix))
                        .map(lvl -> wrapperPrefix + enchantName + ":" + lvl);
            }
        }

        // Complete enchantment names and presets
        Stream<String> presetStream = PRESETS.keySet().stream()
                .filter(s -> s.startsWith(prefix))
                .map(s -> wrapperPrefix + s);
        Stream<String> enchantStream = VALID_ENCHANTMENTS.stream()
                .filter(s -> s.startsWith(prefix))
                .map(s -> wrapperPrefix + s);

        return Stream.concat(presetStream, enchantStream);
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
                "Enchantment Format (resource location style):",
                "  name - Any level (e.g., mending, silk_touch)",
                "  name:level - Specific level (e.g., protection:4, sharpness:5)",
                "",
                "Usage:",
                "  #trade cycle mending - Cycle until Mending book found",
                "  #trade cycle sharpness:5 - Cycle until Sharpness V",
                "  #trade cycle [mending, silk_touch] - Cycle for multiple enchants",
                "  #trade cycle enchantments={mending:1, protection:4} - Alternative array syntax",
                "  #trade cycle sword_best - Use a preset",
                "  #trade cycle mending autolock - Auto-buy to lock profession",
                "  #trade cycle mending -human - Enable human-like randomized behavior",
                "",
                "Control:",
                "  #trade stop - Stop cycling",
                "  #trade status - Show cycle count, time, last trades",
                "  #trade presets - List available presets",
                "",
                "Flags:",
                "  autolock - Auto-buy the book when found to lock the trade",
                "  -human - Add randomized delays/movements to appear more human-like",
                "",
                "Requirements:",
                "  - Run #trade setvil while looking at a villager",
                "  - Run #trade setpos while looking at workstation placement spot",
                "  - Have workstation block (lectern) in hotbar (auto-selected)"
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
            return level != null ? enchantmentName + ":" + level : enchantmentName;
        }
    }
}
