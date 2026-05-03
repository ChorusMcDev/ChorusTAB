package me.neznamy.tab.shared.features.nametags;

import lombok.RequiredArgsConstructor;
import me.neznamy.tab.shared.chat.component.TabComponent;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manager for nametag background hiding via Text Display entities.
 * <p>
 * This class lives in the shared module and delegates actual entity
 * operations to the platform-specific {@code NameTagBackgroundHandler}
 * obtained via reflection from the Bukkit implementation provider.
 */
@RequiredArgsConstructor
public class NameTagBackgroundManager {

    /** Parent NameTag feature */
    @NotNull
    private final NameTag nameTags;

    /** Vertical offset configured */
    private final float yOffset;

    /**
     * Spawns a Text Display above the target's head, visible to the viewer.
     *
     * @param target Target player
     * @param viewer Viewer player
     * @param text   Full nametag text (prefix + name + suffix, possibly multi-line with \n)
     */
    public void spawnDisplay(@NotNull TabPlayer target, @NotNull TabPlayer viewer, @NotNull String text) {
        Object handler = getHandler(viewer);
        if (handler == null) return;
        applyYOffset(handler);
        TabComponent component = nameTags.getNameTagTextCache().get(text);
        invokeSpawn(handler, target, viewer, component);
    }

    /**
     * Updates the Text Display text for a target player as seen by a viewer.
     *
     * @param target Target player
     * @param viewer Viewer player
     * @param text   New full nametag text
     */
    public void updateDisplay(@NotNull TabPlayer target, @NotNull TabPlayer viewer, @NotNull String text) {
        Object handler = getHandler(viewer);
        if (handler == null) return;
        TabComponent component = nameTags.getNameTagTextCache().get(text);
        invokeUpdate(handler, target, viewer, component);
    }

    /**
     * Destroys the Text Display for a specific viewer.
     *
     * @param target Target player
     * @param viewer Viewer player
     */
    public void destroyDisplay(@NotNull TabPlayer target, @NotNull TabPlayer viewer) {
        Object handler = getHandler(viewer);
        if (handler == null) return;
        invokeDestroy(handler, target, viewer);
    }

    /**
     * Destroys all Text Displays for a target player.
     *
     * @param target  Target player
     * @param viewers All potential viewers
     */
    public void destroyAllDisplays(@NotNull TabPlayer target, @NotNull Iterable<TabPlayer> viewers) {
        for (TabPlayer viewer : viewers) {
            Object handler = getHandler(viewer);
            if (handler != null) {
                invokeDestroyAll(handler, target, viewers);
                return;
            }
        }
    }

    /**
     * Cleans up all data for a viewer who is quitting.
     *
     * @param viewer The quitting viewer
     */
    public void onViewerQuit(@NotNull TabPlayer viewer) {
        Object handler = getHandler(viewer);
        if (handler == null) return;
        invokeOnViewerQuit(handler, viewer);
    }

    // ──────────── Platform bridge via reflection ────────────

    /**
     * Gets the NameTagBackgroundHandler for the viewer's platform.
     * Returns null if the platform doesn't support it.
     */
    @Nullable
    private Object getHandler(@NotNull TabPlayer viewer) {
        try {
            Object platform = viewer.getClass().getMethod("getPlatform").invoke(viewer);
            Object versionInfo = platform.getClass().getMethod("getServerVersionInfo").invoke(platform);
            Object provider = versionInfo.getClass().getMethod("getImplementationProvider").invoke(versionInfo);
            return provider.getClass().getMethod("getNameTagBackgroundHandler").invoke(provider);
        } catch (Exception e) {
            return null;
        }
    }

    private void invokeSpawn(Object handler, TabPlayer target, TabPlayer viewer, TabComponent text) {
        try {
            handler.getClass().getMethod("spawn", TabPlayer.class, TabPlayer.class, TabComponent.class)
                    .invoke(handler, target, viewer, text);
        } catch (Exception ignored) {}
    }

    private void invokeUpdate(Object handler, TabPlayer target, TabPlayer viewer, TabComponent text) {
        try {
            handler.getClass().getMethod("update", TabPlayer.class, TabPlayer.class, TabComponent.class)
                    .invoke(handler, target, viewer, text);
        } catch (Exception ignored) {}
    }

    private void invokeDestroy(Object handler, TabPlayer target, TabPlayer viewer) {
        try {
            handler.getClass().getMethod("destroy", TabPlayer.class, TabPlayer.class)
                    .invoke(handler, target, viewer);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void invokeDestroyAll(Object handler, TabPlayer target, Iterable<TabPlayer> viewers) {
        try {
            handler.getClass().getMethod("destroyAll", TabPlayer.class, Iterable.class)
                    .invoke(handler, target, viewers);
        } catch (Exception ignored) {}
    }

    private void invokeOnViewerQuit(Object handler, TabPlayer viewer) {
        try {
            handler.getClass().getMethod("onViewerQuit", TabPlayer.class)
                    .invoke(handler, viewer);
        } catch (Exception ignored) {}
    }

    private void applyYOffset(Object handler) {
        try {
            handler.getClass().getMethod("setYOffset", float.class)
                    .invoke(handler, yOffset);
        } catch (Exception ignored) {}
    }
}

