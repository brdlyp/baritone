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
import baritone.api.event.events.MerchantOffersEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.IVillagerTradeProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Process for automating villager trade cycling.
 * Automates the cycle: place workstation → check trades → break workstation → repeat
 * until the desired trade is found.
 *
 * @author Brady
 * @since 1/23/2026
 */
public final class VillagerTradeProcess extends BaritoneProcessHelper implements IVillagerTradeProcess {

    // Setup state
    private Villager targetVillager = null;
    private java.util.UUID targetVillagerUUID = null;  // Store UUID for reliable comparison
    private BlockPos workstationPos = null;
    private Block workstationBlock = null;

    // Cycling state
    private CycleState state = CycleState.IDLE;
    private Predicate<MerchantOffer> desiredTradePredicate = null;
    private boolean autoLock = false;

    // Statistics
    private int cycleCount = 0;
    private long startTimeMs = 0;
    private int tradesChecked = 0;

    // Trade data
    private List<MerchantOffer> lastSeenOffers = new ArrayList<>();
    private MerchantOffer foundTrade = null;
    private MerchantOffers pendingOffers = null;

    // Timing
    private int ticksWaited = 0;
    private int ticksInState = 0;
    private CycleState previousState = CycleState.IDLE;

    // Workstation blocks that give professions
    private static final List<Block> WORKSTATION_BLOCKS = List.of(
            Blocks.LECTERN,
            Blocks.CARTOGRAPHY_TABLE,
            Blocks.BREWING_STAND,
            Blocks.SMITHING_TABLE,
            Blocks.BLAST_FURNACE,
            Blocks.GRINDSTONE,
            Blocks.LOOM,
            Blocks.BARREL,
            Blocks.SMOKER,
            Blocks.COMPOSTER,
            Blocks.FLETCHING_TABLE,
            Blocks.CAULDRON,
            Blocks.STONECUTTER
    );

    public VillagerTradeProcess(Baritone baritone) {
        super(baritone);

        // Register as event listener to receive merchant offers
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onMerchantOffersReceived(MerchantOffersEvent event) {
                VillagerTradeProcess.this.handleMerchantOffers(event);
            }
        });
    }

    private void handleMerchantOffers(MerchantOffersEvent event) {
        if (state == CycleState.OPENING_TRADE_GUI || state == CycleState.READING_TRADES) {
            pendingOffers = event.getOffers();
            lastSeenOffers = new ArrayList<>(event.getOffers());
            tradesChecked += event.getOffers().size();
        }
    }

    /**
     * Select the villager the player is currently looking at.
     * @return true if a villager was found and selected
     */
    public boolean selectLookedAtVillager() {
        // Use Minecraft's hitResult which includes entity hits (not ctx.objectMouseOver() which only does block raytrace)
        var hitResult = ctx.minecraft().hitResult;
        if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            var entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
            if (entityHit.getEntity() instanceof Villager villager) {
                setTargetVillager(villager);
                return true;
            }
        }
        return false;
    }

    /**
     * Select the block position the player is currently looking at as the workstation position.
     * @return true if a valid position was found and selected
     */
    public boolean selectLookedAtPosition() {
        var hitResult = ctx.objectMouseOver();
        if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var blockHit = (net.minecraft.world.phys.BlockHitResult) hitResult;
            BlockPos clickedPos = blockHit.getBlockPos();
            // Set workstation position to the block above if clicked on solid block
            BlockState clickedState = ctx.world().getBlockState(clickedPos);
            if (clickedState.isSolid()) {
                setWorkstationPosition(clickedPos.above());
            } else {
                setWorkstationPosition(clickedPos);
            }
            return true;
        }
        return false;
    }

    // ==================== IVillagerTradeProcess Implementation ====================

    @Override
    public void setTargetVillager(Villager villager) {
        this.targetVillager = villager;
        this.targetVillagerUUID = villager != null ? villager.getUUID() : null;
    }

    @Override
    public void setWorkstationPosition(BlockPos pos) {
        this.workstationPos = pos;
    }

    @Override
    public SetupStatus getSetupStatus() {
        boolean isReady = targetVillager != null && workstationPos != null;
        return new SetupStatus(targetVillager, workstationPos, workstationBlock, isReady);
    }

    @Override
    public void startCycling(Predicate<MerchantOffer> desiredTrade, boolean autoLock) {
        // Validate setup
        if (targetVillager == null) {
            logDirect("Error: No villager selected. Run #trade setup first.");
            return;
        }
        if (workstationPos == null) {
            logDirect("Error: No workstation position set. Run #trade setup first.");
            return;
        }

        // Get workstation block from player's hand
        ItemStack heldItem = ctx.player().getMainHandItem();
        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof BlockItem blockItem)) {
            logDirect("Error: Hold a workstation block in your main hand.");
            return;
        }

        Block heldBlock = blockItem.getBlock();
        if (!WORKSTATION_BLOCKS.contains(heldBlock)) {
            logDirect("Error: That's not a valid workstation block.");
            return;
        }

        this.workstationBlock = heldBlock;
        this.desiredTradePredicate = desiredTrade;
        this.autoLock = autoLock;
        this.cycleCount = 0;
        this.startTimeMs = System.currentTimeMillis();
        this.tradesChecked = 0;
        this.foundTrade = null;
        this.lastSeenOffers = new ArrayList<>();
        this.ticksWaited = 0;
        this.ticksInState = 0;

        // Start the cycle - check daytime first
        logDirect("Starting trade cycling...");
        logDirect("Looking for matching trade. Will cycle until found.");
        if (autoLock) {
            logDirect("Auto-lock enabled: will trade once to lock profession when found.");
        }
        
        // Check if it's work time for villagers
        if (isVillagerWorkTime()) {
            state = CycleState.PLACING_WORKSTATION;
        } else {
            state = CycleState.WAITING_FOR_DAYTIME;
            logDirect("It's nighttime - waiting for villagers to wake up...");
        }
    }

    /**
     * Check if it's daytime when villagers work (roughly 0-12000 ticks).
     * Villagers sleep from about 12000 to 24000 (or 0) ticks.
     */
    private boolean isVillagerWorkTime() {
        long dayTime = ctx.world().getDayTime() % 24000;
        // Villagers work roughly from 0 (6 AM) to 12000 (6 PM)
        // They sleep from about 12000 to 23999
        return dayTime < 12000;
    }

    @Override
    public void stopCycling() {
        if (state != CycleState.IDLE) {
            logDirect("Stopped after " + cycleCount + " cycles (" + getStats().getElapsedFormatted() + ")");
        }
        state = CycleState.IDLE;
        desiredTradePredicate = null;
        baritone.getInputOverrideHandler().clearAllKeys();
    }

    @Override
    public boolean isCycling() {
        return state != CycleState.IDLE && state != CycleState.FOUND && state != CycleState.FAILED;
    }

    @Override
    public CycleState getState() {
        return state;
    }

    @Override
    public CycleStats getStats() {
        long elapsed = state == CycleState.IDLE ? 0 : System.currentTimeMillis() - startTimeMs;
        return new CycleStats(cycleCount, startTimeMs, elapsed, tradesChecked);
    }

    @Override
    public List<MerchantOffer> getLastSeenOffers() {
        return new ArrayList<>(lastSeenOffers);
    }

    @Override
    @Nullable
    public MerchantOffer getFoundTrade() {
        return foundTrade;
    }

    // ==================== IBaritoneProcess Implementation ====================

    @Override
    public boolean isActive() {
        return state != CycleState.IDLE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        // Clear inputs when transitioning to a new state
        if (state != previousState) {
            baritone.getInputOverrideHandler().clearAllKeys();
            previousState = state;
            ticksInState = 0;
        }
        ticksInState++;

        switch (state) {
            case WAITING_FOR_DAYTIME:
                return handleWaitingForDaytime();

            case PLACING_WORKSTATION:
                return handlePlacingWorkstation(isSafeToCancel);

            case WAITING_FOR_PROFESSION:
                return handleWaitingForProfession();

            case MOVING_TO_VILLAGER:
                return handleMovingToVillager();

            case OPENING_TRADE_GUI:
                return handleOpeningTradeGui(isSafeToCancel);

            case READING_TRADES:
                return handleReadingTrades();

            case CLOSING_TRADE_GUI:
                return handleClosingTradeGui();

            case BREAKING_WORKSTATION:
                return handleBreakingWorkstation(isSafeToCancel);

            case WAITING_FOR_RESET:
                return handleWaitingForReset();

            case FOUND:
                // Stay paused, player can see the trade
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);

            case AUTO_LOCKING:
                return handleAutoLocking(isSafeToCancel);

            case FAILED:
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);

            default:
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
    }

    private PathingCommand handleWaitingForDaytime() {
        // Check every second (20 ticks)
        if (ticksInState % 20 == 0) {
            if (isVillagerWorkTime()) {
                logDirect("Daytime! Resuming trade cycling...");
                state = CycleState.PLACING_WORKSTATION;
                ticksWaited = 0;
            } else if (ticksInState % 200 == 0) {
                // Log every 10 seconds
                long dayTime = ctx.world().getDayTime() % 24000;
                logDirect("Waiting for daytime... (current time: " + dayTime + "/24000)");
            }
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand handlePlacingWorkstation(boolean isSafeToCancel) {
        if (workstationPos == null || workstationBlock == null) {
            logDirect("Error: Workstation position or block not set");
            state = CycleState.FAILED;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Check if we're close enough to place
        BetterBlockPos playerPos = ctx.playerFeet();
        double distSq = playerPos.distSqr(workstationPos);
        double reachDist = ctx.playerController().getBlockReachDistance();

        if (distSq > reachDist * reachDist) {
            // Need to move closer
            Goal goal = new GoalNear(workstationPos, 3);
            return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
        }

        // Check if block is already there
        BlockState currentState = ctx.world().getBlockState(workstationPos);
        if (currentState.getBlock() == workstationBlock) {
            // Already placed, move to waiting
            state = CycleState.WAITING_FOR_PROFESSION;
            ticksWaited = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Need to place the block
        if (!isSafeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Make sure we have the workstation in hand (allow searching full inventory)
        if (!baritone.getInventoryBehavior().throwaway(true, stack ->
                stack.getItem() instanceof BlockItem bi && bi.getBlock() == workstationBlock, true)) {
            logDirect("Error: No workstation block in inventory");
            state = CycleState.FAILED;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Look at the position below workstation and right-click
        BlockPos placeOn = workstationPos.below();
        Optional<Rotation> rot = RotationUtils.reachableOffset(ctx, placeOn,
                new Vec3(placeOn.getX() + 0.5, placeOn.getY() + 1, placeOn.getZ() + 0.5),
                reachDist, false);

        if (rot.isPresent()) {
            baritone.getLookBehavior().updateTarget(rot.get(), true);
            if (ctx.isLookingAt(placeOn)) {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            }
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand handleWaitingForProfession() {
        ticksWaited++;

        // Check if villager has gained profession (use .is() for Holder comparison)
        if (targetVillager != null && !targetVillager.getVillagerData().profession().is(VillagerProfession.NONE)) {
            // Villager has profession, wait a bit more for trades to initialize
            if (ticksWaited > Baritone.settings().villagerProfessionWaitTicks.value) {
                state = CycleState.MOVING_TO_VILLAGER;
                ticksWaited = 0;
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Timeout check
        if (ticksWaited > Baritone.settings().villagerClaimTimeoutTicks.value) {
            logDirect("Timeout waiting for villager to claim workstation. Retrying...");
            // Break and re-place
            state = CycleState.BREAKING_WORKSTATION;
            ticksWaited = 0;
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand handleMovingToVillager() {
        if (targetVillager == null || !targetVillager.isAlive()) {
            logDirect("Error: Target villager is no longer valid");
            state = CycleState.FAILED;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        BlockPos villagerPos = targetVillager.blockPosition();
        BetterBlockPos playerPos = ctx.playerFeet();
        double distSq = playerPos.distSqr(villagerPos);

        // Check if we're close enough to interact (3 blocks)
        if (distSq <= 9) {
            state = CycleState.OPENING_TRADE_GUI;
            ticksWaited = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Need to move closer
        Goal goal = new GoalNear(villagerPos, 2);
        return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
    }

    private PathingCommand handleOpeningTradeGui(boolean isSafeToCancel) {
        if (targetVillager == null || !targetVillager.isAlive()) {
            logDirect("Error: Target villager is no longer valid");
            state = CycleState.FAILED;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Check if trade GUI is already open
        if (ctx.minecraft().screen instanceof MerchantScreen) {
            state = CycleState.READING_TRADES;
            ticksWaited = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        ticksWaited++;

        // Timeout check
        if (ticksWaited > 100) {
            logDirect("Timeout waiting for trade GUI to open. Retrying...");
            state = CycleState.MOVING_TO_VILLAGER;
            ticksWaited = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (!isSafeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Look at villager and interact
        Vec3 villagerEyes = targetVillager.getEyePosition();
        Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), villagerEyes, ctx.playerRotations());

        baritone.getLookBehavior().updateTarget(rot, true);

        // Check if we're looking at the villager (use minecraft's hitResult which includes entities)
        var hitResult = ctx.minecraft().hitResult;
        if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            var entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
            // Use UUID comparison instead of reference equality (entity objects can be recreated)
            if (entityHit.getEntity() instanceof Villager hitVillager && 
                    hitVillager.getUUID().equals(targetVillagerUUID)) {
                // Only interact once every few ticks to avoid spam
                if (ticksWaited % 5 == 0) {
                    // Use direct entity interaction instead of input override
                    ctx.minecraft().gameMode.interact(ctx.player(), hitVillager, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
            } else if (ticksWaited % 20 == 0) {
                // Debug: log which entity we're looking at
                logDirect("Looking at wrong entity: " + entityHit.getEntity().getClass().getSimpleName() + 
                        " (UUID match: " + entityHit.getEntity().getUUID().equals(targetVillagerUUID) + ")");
            }
        } else if (ticksWaited % 20 == 0) {
            // Debug: not looking at entity
            String hitType = hitResult != null ? hitResult.getType().toString() : "null";
            logDirect("Not looking at entity, hitType=" + hitType);
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand handleReadingTrades() {
        ticksWaited++;

        // Wait for trade offers to be received
        if (pendingOffers == null) {
            if (ticksWaited > Baritone.settings().villagerTradeReadDelayTicks.value + 20) {
                logDirect("No trade offers received. Retrying...");
                state = CycleState.CLOSING_TRADE_GUI;
                ticksWaited = 0;
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Log what enchantment we saw (if any)
        logTradeEnchantment(pendingOffers);

        // Check offers against predicate
        for (MerchantOffer offer : pendingOffers) {
            if (desiredTradePredicate != null && desiredTradePredicate.test(offer)) {
                foundTrade = offer;
                cycleCount++;
                logDirect("§a✓ FOUND after " + cycleCount + " cycles (" + getStats().getElapsedFormatted() + "):");
                logDirect("§a  " + formatOffer(offer));

                if (Baritone.settings().villagerTradeFoundSound.value) {
                    // Play a notification sound
                    ctx.player().playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
                }

                if (autoLock) {
                    state = CycleState.AUTO_LOCKING;
                } else {
                    state = CycleState.FOUND;
                }
                pendingOffers = null;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        // No match found, continue cycling
        cycleCount++;
        pendingOffers = null;
        state = CycleState.CLOSING_TRADE_GUI;
        ticksWaited = 0;

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /**
     * Log what enchantment book trade was seen (if any)
     */
    private void logTradeEnchantment(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            if (result.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
                // Found an enchanted book, log it
                var storedEnchants = result.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
                if (storedEnchants != null && !storedEnchants.isEmpty()) {
                    for (var enchant : storedEnchants.keySet()) {
                        int level = storedEnchants.getLevel(enchant);
                        String name = enchant.getRegisteredName();
                        if (name != null) {
                            name = name.replace("minecraft:", "");
                        } else {
                            name = enchant.value().description().getString();
                        }
                        int cost = offer.getCostA().getCount();
                        logDirect("Cycle " + (cycleCount + 1) + ": " + name + " " + level + " (" + cost + " emeralds)");
                        return;
                    }
                }
            }
        }
        // No enchanted book found
        logDirect("Cycle " + (cycleCount + 1) + ": No book trade");
    }

    private PathingCommand handleClosingTradeGui() {
        ticksWaited++;

        // Close the GUI
        if (ctx.minecraft().screen instanceof MerchantScreen) {
            ctx.player().closeContainer();
        }

        // Wait a bit after closing
        if (ticksWaited > Baritone.settings().villagerTradeCloseDelayTicks.value) {
            state = CycleState.BREAKING_WORKSTATION;
            ticksWaited = 0;
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand handleBreakingWorkstation(boolean isSafeToCancel) {
        if (workstationPos == null) {
            state = CycleState.FAILED;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Check if block is already broken
        BlockState currentState = ctx.world().getBlockState(workstationPos);
        if (currentState.isAir() || currentState.getBlock() != workstationBlock) {
            // Block is broken, move to next state
            state = CycleState.WAITING_FOR_RESET;
            ticksWaited = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Check if we're close enough to break
        BetterBlockPos playerPos = ctx.playerFeet();
        double distSq = playerPos.distSqr(workstationPos);
        double reachDist = ctx.playerController().getBlockReachDistance();

        if (distSq > reachDist * reachDist) {
            Goal goal = new GoalNear(workstationPos, 3);
            return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
        }

        if (!isSafeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Look at the block and break it
        Optional<Rotation> rot = RotationUtils.reachable(ctx, workstationPos, reachDist);
        if (rot.isPresent()) {
            baritone.getLookBehavior().updateTarget(rot.get(), true);
            MovementHelper.switchToBestToolFor(ctx, currentState);
            if (ctx.isLookingAt(workstationPos)) {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            }
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand handleWaitingForReset() {
        ticksWaited++;

        // Debug logging every second (20 ticks)
        if (ticksWaited % 20 == 0) {
            String profession = targetVillager != null ? 
                    targetVillager.getVillagerData().profession().toString() : "null villager";
            logDirect("WAITING_FOR_RESET: ticks=" + ticksWaited + ", profession=" + profession);
        }

        // Wait for villager to lose profession (use .is() for Holder comparison)
        if (targetVillager != null && targetVillager.getVillagerData().profession().is(VillagerProfession.NONE)) {
            if (ticksWaited > Baritone.settings().villagerWorkstationBreakDelayTicks.value) {
                // Ready to start next cycle - but check if it's daytime first
                if (isVillagerWorkTime()) {
                    state = CycleState.PLACING_WORKSTATION;
                } else {
                    logDirect("Night time - waiting for villagers to wake up...");
                    state = CycleState.WAITING_FOR_DAYTIME;
                }
                ticksWaited = 0;
                ticksInState = 0;
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Check for max cycles
        int maxCycles = Baritone.settings().villagerMaxCycles.value;
        if (maxCycles > 0 && cycleCount >= maxCycles) {
            logDirect("Max cycles reached (" + maxCycles + "). Stopping.");
            state = CycleState.FAILED;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Timeout - villager might have already traded and profession is locked
        if (ticksWaited > Baritone.settings().villagerClaimTimeoutTicks.value) {
            logDirect("Error: Villager's profession appears to be locked (already traded).");
            state = CycleState.FAILED;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand handleAutoLocking(boolean isSafeToCancel) {
        // TODO: Implement auto-lock trading
        // This would involve selecting the found trade and completing one transaction
        logDirect("Auto-lock not yet implemented. Trade found but not locked.");
        state = CycleState.FOUND;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private String formatOffer(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        ItemStack cost = offer.getCostA();
        String resultName = result.getHoverName().getString();
        int emeraldCost = cost.getCount();
        return resultName + " - " + emeraldCost + " emeralds";
    }

    @Override
    public void onLostControl() {
        state = CycleState.IDLE;
        desiredTradePredicate = null;
        baritone.getInputOverrideHandler().clearAllKeys();
    }

    @Override
    public String displayName0() {
        return "VillagerTrade " + state + " (cycle " + cycleCount + ")";
    }
}
