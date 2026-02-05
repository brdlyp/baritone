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

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalStrictDirection;
import baritone.api.utils.SettingsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class TunnelCommand extends Command {

    public TunnelCommand(IBaritone baritone) {
        super(baritone, "tunnel");
    }

    /**
     * Check if a position is valid (not the "not set" sentinel value).
     */
    private static boolean isPositionSet(Vec3i pos) {
        return pos != null && pos.getY() != Integer.MIN_VALUE;
    }

    /**
     * Get the block position the player is currently looking at.
     * @return The BlockPos, or null if not looking at a block
     */
    private BlockPos getLookedAtBlock() {
        HitResult hitResult = ctx.objectMouseOver();
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            return blockHit.getBlockPos();
        }
        return null;
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Settings settings = Baritone.settings();

        // Check for subcommands
        if (args.hasAny()) {
            String firstArg = args.peekString().toLowerCase(Locale.US);

            // Handle "set" subcommand
            if ("set".equals(firstArg)) {
                args.getString(); // consume "set"
                if (!args.hasAny()) {
                    throw new CommandInvalidStateException("Usage: tunnel set <start|end>");
                }
                String action = args.getString().toLowerCase(Locale.US);
                handleSetCommand(action, settings);
                return;
            }

            // Handle "start" subcommand (for "start mining")
            if ("start".equals(firstArg)) {
                args.getString(); // consume "start"
                if (args.hasAny() && "mining".equals(args.peekString().toLowerCase(Locale.US))) {
                    args.getString(); // consume "mining"
                    handleStartMining(settings);
                    return;
                } else {
                    throw new CommandInvalidStateException("Usage: tunnel start mining");
                }
            }

            // Handle "clear" subcommand
            if ("clear".equals(firstArg)) {
                args.getString(); // consume "clear"
                handleClear(settings);
                return;
            }

            // Handle "status" subcommand
            if ("status".equals(firstArg)) {
                args.getString(); // consume "status"
                handleStatus(settings);
                return;
            }

            // Check if it might be the legacy height/width/depth format
            // If first arg is a number, try legacy format
            try {
                Integer.parseInt(firstArg);
                // It's a number, try legacy format
                handleLegacyTunnel(args);
                return;
            } catch (NumberFormatException e) {
                // Not a number, unknown subcommand
                throw new CommandInvalidStateException("Unknown tunnel subcommand: " + firstArg + 
                    ". Use: set start, set end, start mining, clear, status, or <height> <width> <depth>");
            }
        }

        // No arguments - use the simple 1x2 tunnel with GoalStrictDirection
        Goal goal = new GoalStrictDirection(
                ctx.playerFeet(),
                ctx.player().getDirection()
        );
        baritone.getCustomGoalProcess().setGoalAndPath(goal);
        logDirect(String.format("Goal: %s", goal.toString()));
    }

    /**
     * Handle the "set start" and "set end" commands.
     */
    private void handleSetCommand(String action, Settings settings) throws CommandException {
        if ("start".equals(action)) {
            BlockPos pos = getLookedAtBlock();
            if (pos == null) {
                throw new CommandInvalidStateException("You must be looking at a block to set the start position");
            }
            settings.tunnelStartPos.value = new Vec3i(pos.getX(), pos.getY(), pos.getZ());
            settings.tunnelShowOutline.value = true;
            SettingsUtil.save(settings);
            logDirect(String.format("Tunnel start position set to: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ()));
            
            // If end is also set, show the area info
            if (isPositionSet(settings.tunnelEndPos.value)) {
                showAreaInfo(settings);
            } else {
                logDirect("Now look at another block and use 'tunnel set end' to define the mining area.");
            }
        } else if ("end".equals(action)) {
            BlockPos pos = getLookedAtBlock();
            if (pos == null) {
                throw new CommandInvalidStateException("You must be looking at a block to set the end position");
            }
            settings.tunnelEndPos.value = new Vec3i(pos.getX(), pos.getY(), pos.getZ());
            settings.tunnelShowOutline.value = true;
            SettingsUtil.save(settings);
            logDirect(String.format("Tunnel end position set to: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ()));
            
            // If start is also set, show the area info
            if (isPositionSet(settings.tunnelStartPos.value)) {
                showAreaInfo(settings);
                logDirect("Use 'tunnel start mining' to begin mining, or 'tunnel clear' to reset.");
            } else {
                logDirect("Now look at another block and use 'tunnel set start' to define the mining area.");
            }
        } else {
            throw new CommandInvalidStateException("Usage: tunnel set <start|end>");
        }
    }

    /**
     * Handle the "start mining" command.
     */
    private void handleStartMining(Settings settings) throws CommandException {
        Vec3i startPos = settings.tunnelStartPos.value;
        Vec3i endPos = settings.tunnelEndPos.value;

        if (!isPositionSet(startPos) || !isPositionSet(endPos)) {
            throw new CommandInvalidStateException("Both start and end positions must be set first. Use 'tunnel set start' and 'tunnel set end'.");
        }

        // Calculate corner positions (normalize to min/max)
        BlockPos corner1 = new BlockPos(
                Math.min(startPos.getX(), endPos.getX()),
                Math.min(startPos.getY(), endPos.getY()),
                Math.min(startPos.getZ(), endPos.getZ())
        );
        BlockPos corner2 = new BlockPos(
                Math.max(startPos.getX(), endPos.getX()),
                Math.max(startPos.getY(), endPos.getY()),
                Math.max(startPos.getZ(), endPos.getZ())
        );

        // Calculate dimensions
        int width = corner2.getX() - corner1.getX() + 1;
        int height = corner2.getY() - corner1.getY() + 1;
        int depth = corner2.getZ() - corner1.getZ() + 1;

        logDirect(String.format("Starting tunnel mining: %d x %d x %d blocks (%d total blocks)", 
                width, height, depth, width * height * depth));

        // Use the TunnelMiningProcess for methodical spiral mining pattern
        baritone.getTunnelMiningProcess().setArea(corner1, corner2);
        
        logDirect("Mining started with spiral pattern from top to bottom...");
    }

    /**
     * Handle the "clear" command.
     */
    private void handleClear(Settings settings) {
        // Cancel any ongoing tunnel mining process
        baritone.getTunnelMiningProcess().cancel();
        
        // Reset the tunnel positions
        settings.tunnelStartPos.value = new Vec3i(0, Integer.MIN_VALUE, 0);
        settings.tunnelEndPos.value = new Vec3i(0, Integer.MIN_VALUE, 0);
        settings.tunnelShowOutline.value = false;
        SettingsUtil.save(settings);
        
        logDirect("Tunnel selection cleared.");
    }

    /**
     * Handle the "status" command.
     */
    private void handleStatus(Settings settings) {
        Vec3i startPos = settings.tunnelStartPos.value;
        Vec3i endPos = settings.tunnelEndPos.value;

        boolean startSet = isPositionSet(startPos);
        boolean endSet = isPositionSet(endPos);

        if (!startSet && !endSet) {
            logDirect("No tunnel positions set. Use 'tunnel set start' while looking at a block.");
        } else if (startSet && !endSet) {
            logDirect(String.format("Start position: %d, %d, %d", startPos.getX(), startPos.getY(), startPos.getZ()));
            logDirect("End position: not set. Use 'tunnel set end' while looking at a block.");
        } else if (!startSet && endSet) {
            logDirect("Start position: not set. Use 'tunnel set start' while looking at a block.");
            logDirect(String.format("End position: %d, %d, %d", endPos.getX(), endPos.getY(), endPos.getZ()));
        } else {
            showAreaInfo(settings);
        }

        logDirect(String.format("Outline visible: %s", settings.tunnelShowOutline.value ? "yes" : "no"));
    }

    /**
     * Show information about the selected area.
     */
    private void showAreaInfo(Settings settings) {
        Vec3i startPos = settings.tunnelStartPos.value;
        Vec3i endPos = settings.tunnelEndPos.value;

        int minX = Math.min(startPos.getX(), endPos.getX());
        int minY = Math.min(startPos.getY(), endPos.getY());
        int minZ = Math.min(startPos.getZ(), endPos.getZ());
        int maxX = Math.max(startPos.getX(), endPos.getX());
        int maxY = Math.max(startPos.getY(), endPos.getY());
        int maxZ = Math.max(startPos.getZ(), endPos.getZ());

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        logDirect(String.format("Start: %d, %d, %d", startPos.getX(), startPos.getY(), startPos.getZ()));
        logDirect(String.format("End: %d, %d, %d", endPos.getX(), endPos.getY(), endPos.getZ()));
        logDirect(String.format("Dimensions: %d x %d x %d = %d blocks", width, height, depth, width * height * depth));
    }

    /**
     * Handle the legacy tunnel command format: tunnel <height> <width> <depth>
     */
    private void handleLegacyTunnel(IArgConsumer args) throws CommandException {
        if (!args.hasExactly(3)) {
            throw new CommandInvalidStateException("Legacy tunnel format requires exactly 3 arguments: <height> <width> <depth>");
        }

        int height = Integer.parseInt(args.getString());
        int width = Integer.parseInt(args.getString());
        int depth = Integer.parseInt(args.getString());

        if (width < 1 || height < 2 || depth < 1 || height > ctx.world().getMaxY()) {
            logDirect("Width and depth must at least be 1 block; Height must at least be 2 blocks, and cannot be greater than the build limit.");
            return;
        }

        height--;
        width--;
        BlockPos corner1;
        BlockPos corner2;
        Direction enumFacing = ctx.player().getDirection();
        int addition = ((width % 2 == 0) ? 0 : 1);
        switch (enumFacing) {
            case EAST:
                corner1 = new BlockPos(ctx.playerFeet().x, ctx.playerFeet().y, ctx.playerFeet().z - width / 2);
                corner2 = new BlockPos(ctx.playerFeet().x + depth, ctx.playerFeet().y + height, ctx.playerFeet().z + width / 2 + addition);
                break;
            case WEST:
                corner1 = new BlockPos(ctx.playerFeet().x, ctx.playerFeet().y, ctx.playerFeet().z + width / 2 + addition);
                corner2 = new BlockPos(ctx.playerFeet().x - depth, ctx.playerFeet().y + height, ctx.playerFeet().z - width / 2);
                break;
            case NORTH:
                corner1 = new BlockPos(ctx.playerFeet().x - width / 2, ctx.playerFeet().y, ctx.playerFeet().z);
                corner2 = new BlockPos(ctx.playerFeet().x + width / 2 + addition, ctx.playerFeet().y + height, ctx.playerFeet().z - depth);
                break;
            case SOUTH:
                corner1 = new BlockPos(ctx.playerFeet().x + width / 2 + addition, ctx.playerFeet().y, ctx.playerFeet().z);
                corner2 = new BlockPos(ctx.playerFeet().x - width / 2, ctx.playerFeet().y + height, ctx.playerFeet().z + depth);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + enumFacing);
        }
        logDirect(String.format("Creating a tunnel %s block(s) high, %s block(s) wide, and %s block(s) deep", height + 1, width + 1, depth));
        baritone.getBuilderProcess().clearArea(corner1, corner2);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        try {
            if (args.hasExactlyOne()) {
                String partial = args.peekString().toLowerCase(Locale.US);
                return Stream.of("set", "start", "clear", "status")
                        .filter(s -> s.startsWith(partial));
            }
            if (args.hasExactly(2)) {
                String firstArg = args.getString().toLowerCase(Locale.US);
                String partial = args.peekString().toLowerCase(Locale.US);
                if ("set".equals(firstArg)) {
                    return Stream.of("start", "end")
                            .filter(s -> s.startsWith(partial));
                }
                if ("start".equals(firstArg)) {
                    return Stream.of("mining")
                            .filter(s -> s.startsWith(partial));
                }
            }
        } catch (Exception e) {
            // If we can't peek/get arguments, just return no completions
            return Stream.empty();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Set a goal to tunnel in your current direction, or define a mining area";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The tunnel command can be used in two ways:",
                "",
                "1. QUICK TUNNEL (original behavior):",
                "   > tunnel - Mines in a 1x2 radius in your facing direction",
                "   > tunnel <height> <width> <depth> - Tunnels with specified dimensions",
                "",
                "2. AREA SELECTION (new feature):",
                "   > tunnel set start - Set the first corner by looking at a block",
                "   > tunnel set end - Set the second corner by looking at a block",
                "   > tunnel start mining - Begin mining the selected area",
                "   > tunnel clear - Clear the selection and stop mining",
                "   > tunnel status - Show current selection status",
                "",
                "The area selection mode shows a green outline of the area to be mined.",
                "This allows you to visually confirm the selection before mining.",
                "Positions are saved and persist across game sessions."
        );
    }
}
