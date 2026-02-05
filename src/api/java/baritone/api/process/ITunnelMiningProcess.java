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

/**
 * Interface for the tunnel mining process, which clears an area
 * using a methodical spiral pattern from top to bottom.
 */
public interface ITunnelMiningProcess extends IBaritoneProcess {

    /**
     * Set the area to mine.
     *
     * @param corner1 One corner of the area
     * @param corner2 The opposite corner of the area
     */
    void setArea(BlockPos corner1, BlockPos corner2);

    /**
     * Cancel the mining process.
     */
    void cancel();
}
