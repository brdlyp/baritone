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

package baritone.utils;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

/**
 * Renderer for the tunnel mining selection area.
 * Draws a green outline around the area the user has selected for tunnel mining.
 */
public class TunnelSelectionRenderer implements IRenderer, AbstractGameEventListener {

    public static final double TUNNEL_BOX_EXPANSION = .005D;

    private final Baritone baritone;

    public TunnelSelectionRenderer(Baritone baritone) {
        this.baritone = baritone;
        baritone.getGameEventHandler().registerEventListener(this);
    }

    /**
     * Check if a position is valid (not the "not set" sentinel value).
     */
    private static boolean isPositionSet(Vec3i pos) {
        return pos != null && pos.getY() != Integer.MIN_VALUE;
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        Settings settings = BaritoneAPI.getSettings();
        
        // Check if outline rendering is enabled
        if (!settings.tunnelShowOutline.value) {
            return;
        }

        Vec3i startPos = settings.tunnelStartPos.value;
        Vec3i endPos = settings.tunnelEndPos.value;

        // Check if both positions are set
        if (!isPositionSet(startPos) || !isPositionSet(endPos)) {
            return;
        }

        renderTunnelSelection(event.getModelViewStack(), startPos, endPos);
    }

    /**
     * Render the tunnel selection box.
     */
    private void renderTunnelSelection(PoseStack stack, Vec3i startPos, Vec3i endPos) {
        Settings settings = BaritoneAPI.getSettings();
        
        // Create AABB from the two corner positions
        // We need to find min/max for each axis
        int minX = Math.min(startPos.getX(), endPos.getX());
        int minY = Math.min(startPos.getY(), endPos.getY());
        int minZ = Math.min(startPos.getZ(), endPos.getZ());
        int maxX = Math.max(startPos.getX(), endPos.getX()) + 1; // +1 because AABB is exclusive on max
        int maxY = Math.max(startPos.getY(), endPos.getY()) + 1;
        int maxZ = Math.max(startPos.getZ(), endPos.getZ()) + 1;

        AABB aabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        // Start drawing lines with the tunnel selection color
        BufferBuilder bufferBuilder = IRenderer.startLines(
                settings.colorTunnelSelection.value,
                settings.selectionOpacity.value,
                settings.tunnelSelectionLineWidth.value
        );

        // Emit the main selection box
        IRenderer.emitAABB(bufferBuilder, stack, aabb, TUNNEL_BOX_EXPANSION);

        // Optionally render corner markers (start corner in one color, end corner in another)
        if (settings.renderSelectionCorners.value) {
            // Render start position marker
            IRenderer.glColor(settings.colorSelectionPos1.value, settings.selectionOpacity.value);
            IRenderer.emitAABB(bufferBuilder, stack, new AABB(
                    startPos.getX(), startPos.getY(), startPos.getZ(),
                    startPos.getX() + 1, startPos.getY() + 1, startPos.getZ() + 1
            ), TUNNEL_BOX_EXPANSION * 2);

            // Render end position marker
            IRenderer.glColor(settings.colorSelectionPos2.value, settings.selectionOpacity.value);
            IRenderer.emitAABB(bufferBuilder, stack, new AABB(
                    endPos.getX(), endPos.getY(), endPos.getZ(),
                    endPos.getX() + 1, endPos.getY() + 1, endPos.getZ() + 1
            ), TUNNEL_BOX_EXPANSION * 2);
        }

        // Finish rendering (ignore depth so the outline is always visible)
        IRenderer.endLines(bufferBuilder, settings.renderSelectionIgnoreDepth.value);
    }
}
