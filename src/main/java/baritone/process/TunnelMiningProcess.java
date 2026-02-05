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

package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.ITunnelMiningProcess;
import baritone.api.process.MiningPattern;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * A mining process that clears an area using a methodical spiral pattern.
 * Mines from top to bottom in layers, with each layer using a spiral pattern
 * from the center outward. This creates a human-like, predictable mining behavior.
 */
public final class TunnelMiningProcess extends BaritoneProcessHelper implements ITunnelMiningProcess {

    private BlockPos corner1;
    private BlockPos corner2;
    private List<BlockPos> orderedMiningPositions;
    private int currentPositionIndex;
    private boolean active;
    private int layerHeight; // How many blocks high each layer is (2-3 for standing reach)
    private MiningPattern currentPattern;

    public TunnelMiningProcess(Baritone baritone) {
        super(baritone);
        this.active = false;
        this.layerHeight = 3; // Mine 3 blocks high from each position
        this.currentPattern = MiningPattern.SPIRAL_INWARDS; // Default pattern
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setArea(BlockPos pos1, BlockPos pos2) {
        setArea(pos1, pos2, MiningPattern.SPIRAL_INWARDS);
    }

    @Override
    public void setArea(BlockPos pos1, BlockPos pos2, MiningPattern pattern) {
        // Normalize to min/max corners
        this.corner1 = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
        this.corner2 = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );

        this.currentPattern = pattern != null ? pattern : MiningPattern.SPIRAL_INWARDS;

        // Generate the ordered list of mining positions based on pattern
        this.orderedMiningPositions = generateMiningOrder(this.currentPattern);
        this.currentPositionIndex = 0;
        this.active = true;

        logDirect(String.format("Tunnel mining started with %s pattern: %d positions to process", 
                currentPattern.getDisplayName(), orderedMiningPositions.size()));
    }

    @Override
    public MiningPattern getCurrentPattern() {
        return active ? currentPattern : null;
    }

    /**
     * Generate the mining order based on the selected pattern.
     */
    private List<BlockPos> generateMiningOrder(MiningPattern pattern) {
        switch (pattern) {
            case SPIRAL_OUTWARDS:
                return generateSpiralOutwardsMiningOrder();
            case ZIGZAG:
                return generateZigzagMiningOrder();
            case SPIRAL_INWARDS:
            default:
                return generateSpiralInwardsMiningOrder();
        }
    }

    @Override
    public void cancel() {
        this.active = false;
        this.orderedMiningPositions = null;
        this.currentPositionIndex = 0;
        baritone.getInputOverrideHandler().clearAllKeys();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (!active || orderedMiningPositions == null || orderedMiningPositions.isEmpty()) {
            active = false;
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        // Skip positions that are already air
        while (currentPositionIndex < orderedMiningPositions.size()) {
            BlockPos targetPos = orderedMiningPositions.get(currentPositionIndex);
            if (needsMining(targetPos)) {
                break;
            }
            currentPositionIndex++;
        }

        // Check if we're done
        if (currentPositionIndex >= orderedMiningPositions.size()) {
            logDirect("Tunnel mining complete!");
            cancel();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        baritone.getInputOverrideHandler().clearAllKeys();

        // Find a reachable block following the pattern
        if (isSafeToCancel && ctx.player().onGround()) {
            Optional<BlockPos> reachableBlock = findReachableBlockFollowingPattern();
            if (reachableBlock.isPresent()) {
                BlockPos breakPos = reachableBlock.get();
                Optional<Rotation> rot = RotationUtils.reachable(ctx, breakPos, ctx.playerController().getBlockReachDistance());
                if (rot.isPresent()) {
                    // HUMAN-LIKE BEHAVIOR: Check if we should move closer to the mining face
                    // Humans don't stand at max reach - they walk right up to blocks
                    Optional<BlockPos> closerPosition = findCloserMiningPosition(breakPos);
                    if (closerPosition.isPresent()) {
                        // There's cleared space closer to the block - walk into it while mining
                        Goal walkCloser = new GoalBlock(closerPosition.get());
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        MovementHelper.switchToBestToolFor(ctx, ctx.world().getBlockState(breakPos));
                        if (ctx.isLookingAt(breakPos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        }
                        // Walk closer while mining - this creates the natural "walking forward" behavior
                        return new PathingCommand(walkCloser, PathingCommandType.SET_GOAL_AND_PATH);
                    }
                    
                    // Already close enough - just mine
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    MovementHelper.switchToBestToolFor(ctx, ctx.world().getBlockState(breakPos));
                    if (ctx.isLookingAt(breakPos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }
        }

        // Need to move closer - find the best target position
        BlockPos targetPos = findNextMiningTarget();

        // Find a good goal position - walk right up to the blocks, not just within reach
        Goal goal = createMiningGoal(targetPos);
        return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
    }

    /**
     * Check if there's a cleared position closer to the target block that we should walk to.
     * This creates the human-like behavior of walking right up to blocks instead of
     * standing at max reach distance.
     * 
     * @param targetBlock The block we're trying to mine
     * @return A closer position to walk to, or empty if we're already close enough
     */
    private Optional<BlockPos> findCloserMiningPosition(BlockPos targetBlock) {
        BlockPos playerPos = ctx.playerFeet();
        
        // Calculate horizontal distance to the target block
        double currentDistSq = Math.pow(playerPos.getX() - targetBlock.getX(), 2) + 
                               Math.pow(playerPos.getZ() - targetBlock.getZ(), 2);
        
        // If already adjacent (within ~1.5 blocks horizontally), no need to move closer
        if (currentDistSq <= 2.25) { // 1.5^2
            return Optional.empty();
        }
        
        // Find the best position that's:
        // 1. Cleared (can stand there)
        // 2. Closer to the target than we currently are
        // 3. Still inside or adjacent to the mining area
        BlockPos bestPos = null;
        double bestDistToTarget = currentDistSq;
        
        // Check positions between player and target
        int dx = Integer.compare(targetBlock.getX(), playerPos.getX());
        int dz = Integer.compare(targetBlock.getZ(), playerPos.getZ());
        
        // Try positions stepping toward the target
        for (int step = 1; step <= 3; step++) {
            // Try direct path
            BlockPos candidate = playerPos.offset(dx * step, 0, dz * step);
            if (isGoodCloserPosition(candidate, targetBlock, bestDistToTarget)) {
                double distToTarget = candidate.distSqr(targetBlock);
                if (distToTarget < bestDistToTarget) {
                    bestDistToTarget = distToTarget;
                    bestPos = candidate;
                }
            }
            
            // Also try cardinal directions if diagonal isn't clear
            if (dx != 0) {
                candidate = playerPos.offset(dx * step, 0, 0);
                if (isGoodCloserPosition(candidate, targetBlock, currentDistSq)) {
                    double distToTarget = candidate.distSqr(targetBlock);
                    if (distToTarget < bestDistToTarget) {
                        bestDistToTarget = distToTarget;
                        bestPos = candidate;
                    }
                }
            }
            if (dz != 0) {
                candidate = playerPos.offset(0, 0, dz * step);
                if (isGoodCloserPosition(candidate, targetBlock, currentDistSq)) {
                    double distToTarget = candidate.distSqr(targetBlock);
                    if (distToTarget < bestDistToTarget) {
                        bestDistToTarget = distToTarget;
                        bestPos = candidate;
                    }
                }
            }
        }
        
        return Optional.ofNullable(bestPos);
    }
    
    /**
     * Check if a position is a good candidate for moving closer to mine.
     */
    private boolean isGoodCloserPosition(BlockPos pos, BlockPos targetBlock, double currentDistSq) {
        // Must be cleared (can stand there)
        if (!isPositionClear(pos)) {
            return false;
        }
        
        // Must be closer to target than current position
        double distToTarget = pos.distSqr(targetBlock);
        if (distToTarget >= currentDistSq) {
            return false;
        }
        
        // Don't walk into the target block itself
        if (pos.getX() == targetBlock.getX() && pos.getZ() == targetBlock.getZ()) {
            return false;
        }
        
        // Must have solid ground below (don't walk into pits)
        BlockPos below = pos.below();
        BlockState belowState = ctx.world().getBlockState(below);
        if (isAirOrLiquid(belowState)) {
            return false;
        }
        
        return true;
    }

    /**
     * Find a reachable block that follows the mining pattern order.
     * Looks ahead in the pattern to find blocks we can mine without moving,
     * prioritizing earlier positions in the pattern order.
     * This prevents unnecessary movement while still respecting the mining pattern.
     */
    private Optional<BlockPos> findReachableBlockFollowingPattern() {
        double reachDistance = ctx.playerController().getBlockReachDistance();
        List<BlockPos> reachableInPattern = new ArrayList<>();

        // Look through upcoming positions in pattern order
        // Check a reasonable window ahead (not the entire list for performance)
        int lookAhead = Math.min(100, orderedMiningPositions.size() - currentPositionIndex);
        
        for (int i = 0; i < lookAhead; i++) {
            int idx = currentPositionIndex + i;
            if (idx >= orderedMiningPositions.size()) break;
            
            BlockPos patternPos = orderedMiningPositions.get(idx);
            
            // Check all blocks in the column at this position (up to layerHeight)
            for (int dy = 0; dy < layerHeight && patternPos.getY() + dy <= corner2.getY(); dy++) {
                BlockPos checkPos = patternPos.above(dy);
                BlockState state = ctx.world().getBlockState(checkPos);
                
                if (!isAirOrLiquid(state)) {
                    Optional<Rotation> rot = RotationUtils.reachable(ctx, checkPos, reachDistance);
                    if (rot.isPresent()) {
                        reachableInPattern.add(checkPos);
                    }
                }
            }
        }

        if (reachableInPattern.isEmpty()) {
            return Optional.empty();
        }

        // Sort by Y (higher first for stability), then by pattern order implicitly (already ordered)
        reachableInPattern.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        
        return Optional.of(reachableInPattern.get(0));
    }

    /**
     * Find the next mining target position.
     * Follows the pattern order but allows skipping ahead if the next position
     * is very far and there are much closer unmined positions.
     * This prevents the "walking the whole zigzag" problem while maintaining pattern structure.
     */
    private BlockPos findNextMiningTarget() {
        BlockPos playerPos = ctx.playerFeet();
        BlockPos patternTarget = orderedMiningPositions.get(currentPositionIndex);
        double patternDistSq = playerPos.distSqr(patternTarget);
        
        // If the pattern target is reasonably close (within 6 blocks), just go there
        if (patternDistSq <= 36) { // 6^2 = 36
            return patternTarget;
        }
        
        // Pattern target is far - look for closer unmined positions in the pattern
        // But only consider positions that are "ahead" in the pattern (not going backwards)
        BlockPos bestTarget = patternTarget;
        double bestDistSq = patternDistSq;
        
        // Look ahead in the pattern for closer positions
        int lookAhead = Math.min(50, orderedMiningPositions.size() - currentPositionIndex);
        for (int i = 1; i < lookAhead; i++) {
            int idx = currentPositionIndex + i;
            if (idx >= orderedMiningPositions.size()) break;
            
            BlockPos candidate = orderedMiningPositions.get(idx);
            if (!needsMining(candidate)) continue;
            
            double candidateDistSq = playerPos.distSqr(candidate);
            
            // Prefer this candidate if it's significantly closer (at least 3 blocks closer)
            // This prevents constant switching but allows skipping very far positions
            if (candidateDistSq < bestDistSq - 9) { // 3^2 = 9
                bestTarget = candidate;
                bestDistSq = candidateDistSq;
            }
        }
        
        return bestTarget;
    }

    /**
     * Check if any blocks at this position need mining (current Y and up to layerHeight above).
     */
    private boolean needsMining(BlockPos basePos) {
        for (int dy = 0; dy < layerHeight && basePos.getY() + dy <= corner2.getY(); dy++) {
            BlockPos pos = basePos.above(dy);
            BlockState state = ctx.world().getBlockState(pos);
            if (!isAirOrLiquid(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find a block that can be broken at the target position.
     */
    private Optional<BlockPos> findBreakableBlock(BlockPos basePos) {
        // Check blocks from top to bottom for stability
        for (int dy = Math.min(layerHeight - 1, corner2.getY() - basePos.getY()); dy >= 0; dy--) {
            BlockPos pos = basePos.above(dy);
            BlockState state = ctx.world().getBlockState(pos);
            if (!isAirOrLiquid(state)) {
                // Check if we can reach this block
                Optional<Rotation> rot = RotationUtils.reachable(ctx, pos, ctx.playerController().getBlockReachDistance());
                if (rot.isPresent()) {
                    return Optional.of(pos);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Check if a block state is air or liquid (don't need to mine).
     */
    private boolean isAirOrLiquid(BlockState state) {
        return state.getBlock() instanceof AirBlock ||
               state.getBlock() == Blocks.WATER ||
               state.getBlock() == Blocks.LAVA;
    }

    /**
     * Create a goal to get to a position for mining.
     * HUMAN-LIKE BEHAVIOR: Prefers standing directly adjacent to unmined blocks,
     * not at max reach distance. This creates natural "walk up to the wall" behavior.
     */
    private Goal createMiningGoal(BlockPos targetPos) {
        List<BlockPos> candidatePositions = new ArrayList<>();
        BlockPos playerPos = ctx.playerFeet();

        // Strategy 1: Find positions DIRECTLY ADJACENT to the target (1 block away)
        // Prefer positions that are inside the cleared mining area (walking into the tunnel)
        List<BlockPos> adjacentInside = new ArrayList<>();
        List<BlockPos> adjacentOutside = new ArrayList<>();
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos adjacentPos = targetPos.offset(dx, 0, dz);
                
                if (isPositionClear(adjacentPos)) {
                    if (isInsideMiningArea(adjacentPos)) {
                        adjacentInside.add(adjacentPos);
                    } else {
                        adjacentOutside.add(adjacentPos);
                    }
                }
            }
        }
        
        // Prefer inside positions (walk into the tunnel), then outside positions
        candidatePositions.addAll(adjacentInside);
        candidatePositions.addAll(adjacentOutside);

        // Strategy 2: If no adjacent positions, try positions BELOW the target
        if (candidatePositions.isEmpty()) {
            for (int dy = -1; dy >= -3; dy--) {
                BlockPos belowPos = targetPos.offset(0, dy, 0);
                if (isInsideMiningArea(belowPos) && isPositionClear(belowPos)) {
                    candidatePositions.add(belowPos);
                }
            }
        }

        // Strategy 3: Try cleared edge positions as last resort
        if (candidatePositions.isEmpty()) {
            candidatePositions.addAll(findClearedEdgePositions(targetPos, 5));
        }

        // Strategy 4: Last resort - use GoalNear
        if (candidatePositions.isEmpty()) {
            return new GoalNear(targetPos, 2); // Reduced from 5 to 2 - get closer!
        }

        // Sort candidates: prefer positions that are
        // 1. Directly in line with the mining direction (not at corners)
        // 2. Closer to the player (less walking)
        candidatePositions.sort((a, b) -> {
            // Prefer cardinal directions over diagonals (more natural movement)
            boolean aCardinal = (a.getX() == targetPos.getX()) || (a.getZ() == targetPos.getZ());
            boolean bCardinal = (b.getX() == targetPos.getX()) || (b.getZ() == targetPos.getZ());
            if (aCardinal && !bCardinal) return -1;
            if (bCardinal && !aCardinal) return 1;
            
            // Then prefer closer to player
            double distA = playerPos.distSqr(a);
            double distB = playerPos.distSqr(b);
            return Double.compare(distA, distB);
        });

        return new GoalBlock(candidatePositions.get(0));
    }

    /**
     * Check if a position is inside the mining area.
     */
    private boolean isInsideMiningArea(BlockPos pos) {
        return pos.getX() >= corner1.getX() && pos.getX() <= corner2.getX() &&
               pos.getY() >= corner1.getY() && pos.getY() <= corner2.getY() &&
               pos.getZ() >= corner1.getZ() && pos.getZ() <= corner2.getZ();
    }

    /**
     * Check if a position is clear (all air from floor to ceiling).
     */
    private boolean isPositionClear(BlockPos pos) {
        // A position is clear if the player can stand there (2 blocks of air)
        BlockState feet = ctx.world().getBlockState(pos);
        BlockState head = ctx.world().getBlockState(pos.above());
        return isAirOrLiquid(feet) && isAirOrLiquid(head);
    }

    /**
     * Find cleared positions at the edges of the mining area that are relatively close to the target.
     * These positions can be used as entry points when the target is unreachable from outside.
     */
    private List<BlockPos> findClearedEdgePositions(BlockPos targetPos, int maxDistance) {
        List<BlockPos> edgePositions = new ArrayList<>();
        
        // Check positions at the boundaries of the mining area
        int minX = corner1.getX();
        int maxX = corner2.getX();
        int minY = corner1.getY();
        int maxY = corner2.getY();
        int minZ = corner1.getZ();
        int maxZ = corner2.getZ();
        
        // Helper to check and add edge positions
        List<BlockPos> candidates = new ArrayList<>();
        
        // Add positions along the X edges
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                candidates.add(new BlockPos(x, y, minZ)); // Front edge
                candidates.add(new BlockPos(x, y, maxZ)); // Back edge
            }
        }
        
        // Add positions along the Z edges
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                candidates.add(new BlockPos(minX, y, z)); // Left edge
                candidates.add(new BlockPos(maxX, y, z)); // Right edge
            }
        }
        
        // Filter candidates: must be clear and within distance threshold
        for (BlockPos candidate : candidates) {
            if (isPositionClear(candidate)) {
                double distSq = candidate.distSqr(targetPos);
                if (distSq <= maxDistance * maxDistance) {
                    edgePositions.add(candidate);
                }
            }
        }
        
        // Sort by distance to target
        edgePositions.sort(Comparator.comparingDouble(targetPos::distSqr));
        
        // Return top 5 closest edge positions
        return edgePositions.subList(0, Math.min(5, edgePositions.size()));
    }

    /**
     * Check if a position is accessible from cleared areas.
     * A position is considered accessible if it's clear and has at least one adjacent clear position,
     * indicating it's part of a connected cleared area rather than an isolated air pocket.
     */
    private boolean isAccessibleFromClearedAreas(BlockPos pos) {
        // If the position itself isn't clear, it's not accessible
        if (!isPositionClear(pos)) {
            return false;
        }
        
        // Check if at least one adjacent position is also clear
        // This indicates the position is part of a connected cleared area
        BlockPos[] adjacent = {
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west(),
            pos.below(),
            pos.above()
        };
        
        for (BlockPos adj : adjacent) {
            if (isPositionClear(adj)) {
                return true;
            }
        }
        
        // If no adjacent positions are clear, this might be an isolated air pocket
        // Still return true if the position is outside the mining area (external access)
        return !isInsideMiningArea(pos);
    }

    /**
     * Generate the ordered list of mining positions using spiral inwards pattern.
     * Starts from the outside edges and spirals toward the center.
     * Works from top to bottom in layers.
     */
    private List<BlockPos> generateSpiralInwardsMiningOrder() {
        List<BlockPos> positions = new ArrayList<>();

        int minX = corner1.getX();
        int maxX = corner2.getX();
        int minY = corner1.getY();
        int maxY = corner2.getY();
        int minZ = corner1.getZ();
        int maxZ = corner2.getZ();

        // Determine starting corner based on player position
        BlockPos playerPos = ctx.playerFeet();
        int startCornerX = (Math.abs(playerPos.getX() - minX) <= Math.abs(playerPos.getX() - maxX)) ? minX : maxX;
        int startCornerZ = (Math.abs(playerPos.getZ() - minZ) <= Math.abs(playerPos.getZ() - maxZ)) ? minZ : maxZ;

        // Work from top to bottom in layers
        // Each layer covers 'layerHeight' blocks
        for (int layerTop = maxY; layerTop >= minY; layerTop -= layerHeight) {
            int layerY = layerTop; // Y coordinate for this layer's base positions

            // Generate spiral pattern for this layer
            List<BlockPos> layerPositions = generateSpiralForLayer(
                    minX, maxX, minZ, maxZ, layerY, startCornerX, startCornerZ
            );
            positions.addAll(layerPositions);
        }

        return positions;
    }

    /**
     * Generate a spiral pattern for a single layer, starting from the specified corner.
     */
    private List<BlockPos> generateSpiralForLayer(int minX, int maxX, int minZ, int maxZ, int y,
                                                   int startX, int startZ) {
        List<BlockPos> positions = new ArrayList<>();

        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;

        // For very small areas, just do a simple iteration
        if (width * length <= 4) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
            return positions;
        }

        // Determine spiral direction based on starting corner
        boolean startFromMinX = (startX == minX);
        boolean startFromMinZ = (startZ == minZ);

        // Generate spiral from outside in (starting from the corner)
        int left = minX, right = maxX, top = minZ, bottom = maxZ;

        while (left <= right && top <= bottom) {
            if (startFromMinX && startFromMinZ) {
                // Start from min,min corner - go right, down, left, up
                // Top row (left to right)
                for (int x = left; x <= right; x++) {
                    positions.add(new BlockPos(x, y, top));
                }
                top++;

                // Right column (top to bottom)
                for (int z = top; z <= bottom; z++) {
                    positions.add(new BlockPos(right, y, z));
                }
                right--;

                // Bottom row (right to left)
                if (top <= bottom) {
                    for (int x = right; x >= left; x--) {
                        positions.add(new BlockPos(x, y, bottom));
                    }
                    bottom--;
                }

                // Left column (bottom to top)
                if (left <= right) {
                    for (int z = bottom; z >= top; z--) {
                        positions.add(new BlockPos(left, y, z));
                    }
                    left++;
                }
            } else if (!startFromMinX && startFromMinZ) {
                // Start from max,min corner - go left, down, right, up
                // Top row (right to left)
                for (int x = right; x >= left; x--) {
                    positions.add(new BlockPos(x, y, top));
                }
                top++;

                // Left column (top to bottom)
                for (int z = top; z <= bottom; z++) {
                    positions.add(new BlockPos(left, y, z));
                }
                left++;

                // Bottom row (left to right)
                if (top <= bottom) {
                    for (int x = left; x <= right; x++) {
                        positions.add(new BlockPos(x, y, bottom));
                    }
                    bottom--;
                }

                // Right column (bottom to top)
                if (left <= right) {
                    for (int z = bottom; z >= top; z--) {
                        positions.add(new BlockPos(right, y, z));
                    }
                    right--;
                }
            } else if (startFromMinX && !startFromMinZ) {
                // Start from min,max corner - go right, up, left, down
                // Bottom row (left to right)
                for (int x = left; x <= right; x++) {
                    positions.add(new BlockPos(x, y, bottom));
                }
                bottom--;

                // Right column (bottom to top)
                for (int z = bottom; z >= top; z--) {
                    positions.add(new BlockPos(right, y, z));
                }
                right--;

                // Top row (right to left)
                if (top <= bottom) {
                    for (int x = right; x >= left; x--) {
                        positions.add(new BlockPos(x, y, top));
                    }
                    top++;
                }

                // Left column (top to bottom)
                if (left <= right) {
                    for (int z = top; z <= bottom; z++) {
                        positions.add(new BlockPos(left, y, z));
                    }
                    left++;
                }
            } else {
                // Start from max,max corner - go left, up, right, down
                // Bottom row (right to left)
                for (int x = right; x >= left; x--) {
                    positions.add(new BlockPos(x, y, bottom));
                }
                bottom--;

                // Left column (bottom to top)
                for (int z = bottom; z >= top; z--) {
                    positions.add(new BlockPos(left, y, z));
                }
                left++;

                // Top row (left to right)
                if (top <= bottom) {
                    for (int x = left; x <= right; x++) {
                        positions.add(new BlockPos(x, y, top));
                    }
                    top++;
                }

                // Right column (top to bottom)
                if (left <= right) {
                    for (int z = top; z <= bottom; z++) {
                        positions.add(new BlockPos(right, y, z));
                    }
                    right--;
                }
            }
        }

        return positions;
    }

    /**
     * Generate the ordered list of mining positions using spiral outwards pattern.
     * Starts from the center and spirals outward toward the edges.
     * Works from top to bottom in layers.
     */
    private List<BlockPos> generateSpiralOutwardsMiningOrder() {
        List<BlockPos> positions = new ArrayList<>();

        int minX = corner1.getX();
        int maxX = corner2.getX();
        int minY = corner1.getY();
        int maxY = corner2.getY();
        int minZ = corner1.getZ();
        int maxZ = corner2.getZ();

        // Work from top to bottom in layers
        for (int layerTop = maxY; layerTop >= minY; layerTop -= layerHeight) {
            int layerY = layerTop;

            // Generate spiral outwards pattern for this layer
            List<BlockPos> layerPositions = generateSpiralOutwardsForLayer(
                    minX, maxX, minZ, maxZ, layerY
            );
            positions.addAll(layerPositions);
        }

        return positions;
    }

    /**
     * Generate a spiral outwards pattern for a single layer, starting from the center.
     */
    private List<BlockPos> generateSpiralOutwardsForLayer(int minX, int maxX, int minZ, int maxZ, int y) {
        List<BlockPos> positions = new ArrayList<>();

        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;

        // For very small areas, just do a simple iteration
        if (width * length <= 4) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
            return positions;
        }

        // Calculate center position
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        // Track visited positions
        boolean[][] visited = new boolean[width][length];

        // Start at center
        int x = centerX;
        int z = centerZ;

        // Direction vectors: right, down, left, up
        int[] dx = {1, 0, -1, 0};
        int[] dz = {0, 1, 0, -1};
        int dir = 0; // Start going right

        int stepsInDirection = 1;
        int stepsTaken = 0;
        int directionChanges = 0;

        int totalPositions = width * length;
        int positionsAdded = 0;

        while (positionsAdded < totalPositions) {
            // Add current position if valid and not visited
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                int arrayX = x - minX;
                int arrayZ = z - minZ;
                if (!visited[arrayX][arrayZ]) {
                    visited[arrayX][arrayZ] = true;
                    positions.add(new BlockPos(x, y, z));
                    positionsAdded++;
                }
            }

            // Move to next position
            x += dx[dir];
            z += dz[dir];
            stepsTaken++;

            // Check if we need to turn
            if (stepsTaken >= stepsInDirection) {
                stepsTaken = 0;
                dir = (dir + 1) % 4; // Turn right
                directionChanges++;

                // Increase steps every 2 turns
                if (directionChanges % 2 == 0) {
                    stepsInDirection++;
                }
            }

            // Safety check to prevent infinite loop
            if (positionsAdded == 0 && stepsTaken > totalPositions * 4) {
                break;
            }
        }

        return positions;
    }

    /**
     * Generate the ordered list of mining positions using zigzag (lawn mower) pattern.
     * Mines row by row, alternating direction each row.
     * Works from top to bottom in layers.
     */
    private List<BlockPos> generateZigzagMiningOrder() {
        List<BlockPos> positions = new ArrayList<>();

        int minX = corner1.getX();
        int maxX = corner2.getX();
        int minY = corner1.getY();
        int maxY = corner2.getY();
        int minZ = corner1.getZ();
        int maxZ = corner2.getZ();

        // Determine starting corner based on player position
        BlockPos playerPos = ctx.playerFeet();
        boolean startFromMinX = Math.abs(playerPos.getX() - minX) <= Math.abs(playerPos.getX() - maxX);
        boolean startFromMinZ = Math.abs(playerPos.getZ() - minZ) <= Math.abs(playerPos.getZ() - maxZ);

        // Determine which axis to zigzag along (use shorter axis for rows = more efficient)
        int widthX = maxX - minX + 1;
        int widthZ = maxZ - minZ + 1;
        boolean zigzagAlongX = widthX >= widthZ; // Zigzag along the longer axis

        // Work from top to bottom in layers
        for (int layerTop = maxY; layerTop >= minY; layerTop -= layerHeight) {
            int layerY = layerTop;

            // Generate zigzag pattern for this layer
            List<BlockPos> layerPositions = generateZigzagForLayer(
                    minX, maxX, minZ, maxZ, layerY,
                    startFromMinX, startFromMinZ, zigzagAlongX
            );
            positions.addAll(layerPositions);
        }

        return positions;
    }

    /**
     * Generate a zigzag pattern for a single layer.
     *
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     * @param y Y coordinate for this layer
     * @param startFromMinX Whether to start from minX side
     * @param startFromMinZ Whether to start from minZ side
     * @param zigzagAlongX Whether to zigzag along the X axis (true) or Z axis (false)
     */
    private List<BlockPos> generateZigzagForLayer(int minX, int maxX, int minZ, int maxZ, int y,
                                                   boolean startFromMinX, boolean startFromMinZ,
                                                   boolean zigzagAlongX) {
        List<BlockPos> positions = new ArrayList<>();

        if (zigzagAlongX) {
            // Zigzag along X axis, rows are Z
            // Determine row iteration direction
            int zStart = startFromMinZ ? minZ : maxZ;
            int zEnd = startFromMinZ ? maxZ : minZ;
            int zStep = startFromMinZ ? 1 : -1;

            boolean goingPositiveX = startFromMinX;
            
            for (int z = zStart; startFromMinZ ? (z <= zEnd) : (z >= zEnd); z += zStep) {
                if (goingPositiveX) {
                    // Left to right
                    for (int x = minX; x <= maxX; x++) {
                        positions.add(new BlockPos(x, y, z));
                    }
                } else {
                    // Right to left
                    for (int x = maxX; x >= minX; x--) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
                // Reverse direction for next row
                goingPositiveX = !goingPositiveX;
            }
        } else {
            // Zigzag along Z axis, rows are X
            // Determine row iteration direction
            int xStart = startFromMinX ? minX : maxX;
            int xEnd = startFromMinX ? maxX : minX;
            int xStep = startFromMinX ? 1 : -1;

            boolean goingPositiveZ = startFromMinZ;
            
            for (int x = xStart; startFromMinX ? (x <= xEnd) : (x >= xEnd); x += xStep) {
                if (goingPositiveZ) {
                    // Front to back
                    for (int z = minZ; z <= maxZ; z++) {
                        positions.add(new BlockPos(x, y, z));
                    }
                } else {
                    // Back to front
                    for (int z = maxZ; z >= minZ; z--) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
                // Reverse direction for next row
                goingPositiveZ = !goingPositiveZ;
            }
        }

        return positions;
    }

    @Override
    public void onLostControl() {
        cancel();
    }

    @Override
    public String displayName0() {
        if (!active || orderedMiningPositions == null) {
            return "Tunnel Mining (inactive)";
        }
        int remaining = orderedMiningPositions.size() - currentPositionIndex;
        int total = orderedMiningPositions.size();
        int percent = (int) (((double) currentPositionIndex / total) * 100);
        return String.format("Tunnel Mining [%s] %d%% (%d/%d positions)", 
                currentPattern.getDisplayName(), percent, currentPositionIndex, total);
    }
}
