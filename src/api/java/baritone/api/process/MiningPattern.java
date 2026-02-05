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

import java.util.Locale;

/**
 * Defines the pattern used for mining an area in TunnelMiningProcess.
 */
public enum MiningPattern {
    /**
     * Mines from the outside edges spiraling inward toward the center.
     * Starting corner is determined by player position (closest corner).
     * This is the default pattern.
     */
    SPIRAL_INWARDS,

    /**
     * Mines from the center spiraling outward toward the edges.
     * Starts at the center of the area regardless of player position.
     */
    SPIRAL_OUTWARDS,

    /**
     * Mines in a zigzag pattern like mowing a lawn.
     * Rows alternate direction (left-to-right, then right-to-left).
     * Starting corner is determined by player position (closest corner).
     * Zigzags along the shorter axis for more efficient movement.
     */
    ZIGZAG;

    /**
     * Parse a pattern from a string, case-insensitive.
     *
     * @param name The pattern name (e.g., "zigzag", "spiral_inwards")
     * @return The matching pattern, or null if not found
     */
    public static MiningPattern fromString(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.toUpperCase(Locale.US).replace("-", "_");
        try {
            return MiningPattern.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get a user-friendly name for display.
     */
    public String getDisplayName() {
        return name().toLowerCase(Locale.US).replace("_", " ");
    }
}
