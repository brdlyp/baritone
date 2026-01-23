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

package baritone.api.event.events;

import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Event fired when merchant trade offers are received from the server.
 * This occurs when a player opens a villager's trading GUI.
 *
 * @author Brady
 * @since 1/23/2026
 */
public final class MerchantOffersEvent {

    /**
     * The container ID for the merchant GUI
     */
    private final int containerId;

    /**
     * The list of trade offers available from this merchant
     */
    private final MerchantOffers offers;

    /**
     * The villager's profession level (1-5)
     */
    private final int villagerLevel;

    /**
     * The villager's experience points
     */
    private final int villagerXp;

    /**
     * Whether to show the progress bar in the GUI
     */
    private final boolean showProgress;

    /**
     * Whether the villager can restock their trades
     */
    private final boolean canRestock;

    public MerchantOffersEvent(int containerId, MerchantOffers offers, int villagerLevel,
                                int villagerXp, boolean showProgress, boolean canRestock) {
        this.containerId = containerId;
        this.offers = offers;
        this.villagerLevel = villagerLevel;
        this.villagerXp = villagerXp;
        this.showProgress = showProgress;
        this.canRestock = canRestock;
    }

    /**
     * @return The container ID for the merchant GUI
     */
    public int getContainerId() {
        return this.containerId;
    }

    /**
     * @return The list of trade offers available from this merchant
     */
    public MerchantOffers getOffers() {
        return this.offers;
    }

    /**
     * @return The villager's profession level (1-5)
     */
    public int getVillagerLevel() {
        return this.villagerLevel;
    }

    /**
     * @return The villager's experience points
     */
    public int getVillagerXp() {
        return this.villagerXp;
    }

    /**
     * @return Whether to show the progress bar in the GUI
     */
    public boolean showProgress() {
        return this.showProgress;
    }

    /**
     * @return Whether the villager can restock their trades
     */
    public boolean canRestock() {
        return this.canRestock;
    }
}
