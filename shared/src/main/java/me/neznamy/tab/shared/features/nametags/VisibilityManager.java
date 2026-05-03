package me.neznamy.tab.shared.features.nametags;

import lombok.Getter;
import lombok.NonNull;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.cpu.ThreadExecutor;
import me.neznamy.tab.shared.cpu.TimedCaughtTask;
import me.neznamy.tab.shared.features.types.CustomThreaded;
import me.neznamy.tab.shared.features.types.JoinListener;
import me.neznamy.tab.shared.features.types.Loadable;
import me.neznamy.tab.shared.features.types.RefreshableFeature;
import me.neznamy.tab.shared.placeholders.conditions.Condition;
import me.neznamy.tab.shared.platform.Scoreboard;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Sub-feature for NameTag feature that manages nametag visibility.
 */
public class VisibilityManager extends RefreshableFeature implements JoinListener, Loadable, CustomThreaded {

    /** The main feature */
    @NotNull private final NameTag nameTags;

    /** Configured condition for invisible nametags */
    @Getter
    @NotNull
    private final Condition invisibleCondition;

    /**
     * Constructs new instance.
     *
     * @param   nameTags
     *          Parent feature
     */
    public VisibilityManager(@NotNull NameTag nameTags) {
        this.nameTags = nameTags;
        invisibleCondition = TAB.getInstance().getPlaceholderManager().getConditionManager().getByNameOrExpression(nameTags.getConfiguration().getInvisibleNameTags());
    }

    @Override
    public void load() {
        TAB.getInstance().getPlaceholderManager().registerPlayerPlaceholder(TabConstants.Placeholder.INVISIBLE, p -> {
            TabPlayer player = (TabPlayer) p;
            boolean newInvisibility = invisibleCondition.isMet((TabPlayer) p);
            if (newInvisibility) {
                player.teamData.hideNametag(NameTagInvisibilityReason.MEETING_CONFIGURED_CONDITION);
            } else {
                player.teamData.showNametag(NameTagInvisibilityReason.MEETING_CONFIGURED_CONDITION);
            }
            if (player.hasInvisibilityPotion()) {
                newInvisibility = true;
            }
            return Boolean.toString(newInvisibility);
        });
        addUsedPlaceholder(TabConstants.Placeholder.INVISIBLE);
        for (TabPlayer all : nameTags.getOnlinePlayers().getPlayers()) {
            onJoin(all);
        }
    }

    @Override
    public void onJoin(@NotNull TabPlayer connectedPlayer) {
        if (invisibleCondition.isMet(connectedPlayer)) {
            connectedPlayer.teamData.hideNametag(NameTagInvisibilityReason.MEETING_CONFIGURED_CONDITION);
            updateVisibility(connectedPlayer);
        }
    }

    @NotNull
    @Override
    public String getRefreshDisplayName() {
        return "Updating NameTag visibility";
    }

    @Override
    public void refresh(@NotNull TabPlayer p, boolean force) {
        if (p.teamData.isDisabled()) return;
        if (!nameTags.getOnlinePlayers().contains(p)) return; // player is not loaded by this feature yet
        updateVisibility(p);
    }

    @Override
    @NotNull
    public ThreadExecutor getCustomThread() {
        return nameTags.getCustomThread();
    }

    @NotNull
    @Override
    public String getFeatureName() {
        return nameTags.getFeatureName();
    }

    /**
     * Updates visibility of a player for everyone.
     *
     * @param   player
     *          Player to update visibility of
     */
    public void updateVisibility(@NonNull TabPlayer player) {
        for (TabPlayer viewer : nameTags.getOnlinePlayers().getPlayers()) {
            updateVisibility(player, viewer);
        }
        nameTags.getProxyHandler().sendProxyMessage(player);
    }

    /**
     * Updates visibility of a player for specified player.
     *
     * @param   player
     *          Player to update visibility of
     * @param   viewer
     *          Viewer to send update to
     */
    public void updateVisibility(@NonNull TabPlayer player, @NonNull TabPlayer viewer) {
        if (viewer.teamData.hasTeamRegistered(player)) {
            boolean visible = player.teamData.getTeamVisibility(viewer);

            if (nameTags.getBackgroundManager() != null) {
                // When background manager is active, vanilla nametag is always NEVER.
                // We control visibility via Text Display entity instead.
                if (visible) {
                    nameTags.getBackgroundManager().updateDisplay(player, viewer,
                            nameTags.buildNameTagText(player, viewer));
                } else {
                    nameTags.getBackgroundManager().destroyDisplay(player, viewer);
                }
            } else {
                viewer.getScoreboard().updateTeam(
                        player.teamData.teamName,
                        visible ? Scoreboard.NameVisibility.ALWAYS : Scoreboard.NameVisibility.NEVER
                );
            }
        }
    }

    public void hideNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                            boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.hideNametag(reason)) {
                updateVisibility(player);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
        }, getFeatureName(), cpuReason));
    }

    public void hideNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                            @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.hideNametag(viewer, reason)) {
                updateVisibility(player, viewer);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
        }, getFeatureName(), cpuReason));
    }

    public void showNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                            boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.showNametag(reason)) {
                updateVisibility(player);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
        }, getFeatureName(), cpuReason));
    }

    public void showNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                            @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.showNametag(viewer, reason)) {
                updateVisibility(player, viewer);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
        }, getFeatureName(), cpuReason));
    }

    public void toggleNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                              boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.hasHiddenNametag(reason)) {
                player.teamData.showNametag(reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
            } else {
                player.teamData.hideNametag(reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
            }
            updateVisibility(player);
        }, getFeatureName(), cpuReason));
    }

    public void toggleNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                              @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            if (player.teamData.hasHiddenNametag(viewer, reason)) {
                player.teamData.showNametag(viewer, reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
            } else {
                player.teamData.hideNametag(viewer, reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
            }
            updateVisibility(player, viewer);
        }, getFeatureName(), cpuReason));
    }
}
