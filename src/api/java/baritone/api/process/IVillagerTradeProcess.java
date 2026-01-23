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

package baritone.api.process;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * Process for automating villager trade cycling.
 * <p>
 * Primary use case: Cycling librarian enchanted book trades to find specific enchantments.
 * The process automates: place workstation → check trades → break workstation → repeat
 * until the desired trade is found.
 *
 * @author Brady
 * @since 1/23/2026
 */
public interface IVillagerTradeProcess extends IBaritoneProcess {

    // ==================== SETUP ====================

    /**
     * Select the villager the player is currently looking at.
     * @return true if a villager was found and selected
     */
    boolean selectLookedAtVillager();

    /**
     * Select the block position the player is currently looking at as the workstation position.
     * @return true if a valid position was found and selected
     */
    boolean selectLookedAtPosition();

    /**
     * Set the target villager to cycle.
     *
     * @param villager The villager to target
     */
    void setTargetVillager(Villager villager);

    /**
     * Set where to place/break the workstation.
     *
     * @param pos The block position for the workstation
     */
    void setWorkstationPosition(BlockPos pos);

    /**
     * Get current setup status.
     *
     * @return The current setup status
     */
    SetupStatus getSetupStatus();

    // ==================== CYCLING ====================

    /**
     * Start cycling trades until predicate matches.
     *
     * @param desiredTrade Predicate that returns true for desired trades
     * @param autoLock     If true, make one trade to lock profession when found
     */
    void startCycling(Predicate<MerchantOffer> desiredTrade, boolean autoLock);

    /**
     * Start cycling trades until predicate matches, without auto-locking.
     *
     * @param desiredTrade Predicate that returns true for desired trades
     */
    default void startCycling(Predicate<MerchantOffer> desiredTrade) {
        startCycling(desiredTrade, false);
    }

    /**
     * Stop cycling.
     */
    void stopCycling();

    /**
     * Check if currently cycling.
     *
     * @return true if currently cycling trades
     */
    boolean isCycling();

    // ==================== STATUS ====================

    /**
     * Get current state.
     *
     * @return The current cycle state
     */
    CycleState getState();

    /**
     * Get cycle statistics.
     *
     * @return Statistics about the current/last cycle operation
     */
    CycleStats getStats();

    /**
     * Get the last trades seen (for display).
     *
     * @return List of the most recently seen trade offers, or empty list if none
     */
    List<MerchantOffer> getLastSeenOffers();

    /**
     * Get the matching trade if found.
     *
     * @return The trade that matched the predicate, or null if not found yet
     */
    @Nullable
    MerchantOffer getFoundTrade();

    /**
     * Cancels the trade cycling process.
     */
    default void cancel() {
        onLostControl();
    }

    // ==================== TYPES ====================

    /**
     * Setup status record containing the current setup state.
     */
    record SetupStatus(
            @Nullable Villager villager,
            @Nullable BlockPos workstationPos,
            @Nullable Block workstationBlock,
            boolean isReady
    ) {
        /**
         * Check if a villager has been selected.
         */
        public boolean hasVillager() {
            return villager != null;
        }

        /**
         * Check if a workstation position has been set.
         */
        public boolean hasWorkstationPos() {
            return workstationPos != null;
        }
    }

    /**
     * Statistics about the cycling operation.
     */
    record CycleStats(
            int cycleCount,
            long startTimeMs,
            long elapsedMs,
            int tradesChecked
    ) {
        /**
         * Get elapsed time formatted as a string.
         */
        public String getElapsedFormatted() {
            long seconds = elapsedMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds);
            }
            return String.format("%ds", seconds);
        }
    }

    /**
     * The current state of the cycling process.
     */
    enum CycleState {
        /** Idle, not cycling */
        IDLE,
        /** Waiting for daytime when villagers can work */
        WAITING_FOR_DAYTIME,
        /** Placing the workstation block */
        PLACING_WORKSTATION,
        /** Waiting for villager to claim the workstation and get a profession */
        WAITING_FOR_PROFESSION,
        /** Moving to interact with villager */
        MOVING_TO_VILLAGER,
        /** Opening trade GUI by right-clicking villager */
        OPENING_TRADE_GUI,
        /** Reading and checking trade offers */
        READING_TRADES,
        /** Closing the trade GUI */
        CLOSING_TRADE_GUI,
        /** Breaking workstation to reset villager's profession */
        BREAKING_WORKSTATION,
        /** Waiting after breaking workstation for villager to lose profession */
        WAITING_FOR_RESET,
        /** Desired trade found! */
        FOUND,
        /** Auto-locking the trade by making a purchase */
        AUTO_LOCKING,
        /** Something went wrong */
        FAILED
    }
}
