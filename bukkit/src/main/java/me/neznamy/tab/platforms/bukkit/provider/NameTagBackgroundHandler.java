package me.neznamy.tab.platforms.bukkit.provider;

import me.neznamy.tab.shared.chat.component.TabComponent;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for managing Text Display entities above player heads
 * with transparent background, replacing the vanilla nametag rendering.
 */
public interface NameTagBackgroundHandler {

    /**
     * Sets the vertical offset (in blocks) for the Text Display entity.
     *
     * @param offset Y offset in blocks (positive = up, negative = down)
     */
    void setYOffset(float offset);

    /**
     * Spawns a Text Display entity above the target player's head,
     * visible only to the specified viewer.
     *
     * @param target The player above whose head the display appears
     * @param viewer The player who sees this display
     * @param text   The composed nametag text as a component (prefix + name + suffix)
     */
    void spawn(@NotNull TabPlayer target, @NotNull TabPlayer viewer, @NotNull TabComponent text);

    /**
     * Updates the text content of an existing Text Display entity.
     * If no entity exists yet, it will be spawned.
     *
     * @param target The target player
     * @param viewer The viewer
     * @param text   The new text component
     */
    void update(@NotNull TabPlayer target, @NotNull TabPlayer viewer, @NotNull TabComponent text);

    /**
     * Destroys the Text Display entity for a specific viewer.
     *
     * @param target The target player
     * @param viewer The viewer
     */
    void destroy(@NotNull TabPlayer target, @NotNull TabPlayer viewer);

    /**
     * Destroys all Text Display entities for a target player (all viewers).
     *
     * @param target  The target player
     * @param viewers All potential viewers
     */
    void destroyAll(@NotNull TabPlayer target, @NotNull Iterable<TabPlayer> viewers);

    /**
     * Cleans up all display entities seen by a viewer who is quitting.
     *
     * @param viewer The quitting viewer
     */
    void onViewerQuit(@NotNull TabPlayer viewer);

    /**
     * Checks if a display entity exists for the given target and viewer.
     *
     * @param target The target player
     * @param viewer The viewer
     * @return true if entity exists
     */
    boolean hasDisplay(@NotNull TabPlayer target, @NotNull TabPlayer viewer);
}

