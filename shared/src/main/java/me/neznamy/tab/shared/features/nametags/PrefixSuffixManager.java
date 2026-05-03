package me.neznamy.tab.shared.features.nametags;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.shared.Property;
import me.neznamy.tab.shared.cpu.ThreadExecutor;
import me.neznamy.tab.shared.data.Server;
import me.neznamy.tab.shared.data.World;
import me.neznamy.tab.shared.features.types.*;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-feature for NameTags for managing prefix/suffix.
 */
@RequiredArgsConstructor
public class PrefixSuffixManager extends RefreshableFeature implements GroupListener, WorldSwitchListener,
        ServerSwitchListener, CustomThreaded {

    /** Parent feature */
    private final NameTag feature;

    @Override
    @NotNull
    public String getRefreshDisplayName() {
        return "Updating prefix/suffix";
    }

    @Override
    public void refresh(@NotNull TabPlayer refreshed, boolean force) {
        if (force) {
            updateProperties(refreshed);
            updatePrefixSuffix(refreshed);
        } else {
            boolean changed = refreshed.teamData.prefix.update();
            if (refreshed.teamData.suffix.update()) changed = true;
            // Check if any extra line changed
            for (Property line : refreshed.teamData.aboveLines) {
                if (line.update()) changed = true;
            }
            for (Property line : refreshed.teamData.belowLines) {
                if (line.update()) changed = true;
            }
            if (changed) updatePrefixSuffix(refreshed);
        }
    }

    @Override
    @NotNull
    public String getFeatureName() {
        return feature.getFeatureName();
    }

    @Override
    public void onGroupChange(@NotNull TabPlayer player) {
        if (updateProperties(player)) updatePrefixSuffix(player);
    }

    @Override
    public void onServerChange(@NonNull TabPlayer player, @NotNull Server from, @NotNull Server to) {
        if (updateProperties(player)) updatePrefixSuffix(player);
    }

    @Override
    public void onWorldChange(@NotNull TabPlayer player, @NotNull World from, @NotNull World to) {
        if (updateProperties(player)) updatePrefixSuffix(player);
    }

    /**
     * Loads all properties from config and returns {@code true} if at least
     * one of them either wasn't loaded or changed value, {@code false} otherwise.
     *
     * @param   p
     *          Player to update properties of
     * @return  {@code true} if at least one property changed, {@code false} if not
     */
    private boolean updateProperties(@NotNull TabPlayer p) {
        boolean changed = p.updatePropertyFromConfig(p.teamData.prefix, "");
        if (p.updatePropertyFromConfig(p.teamData.suffix, "")) changed = true;
        for (Property line : p.teamData.aboveLines) {
            if (line.update()) changed = true;
        }
        for (Property line : p.teamData.belowLines) {
            if (line.update()) changed = true;
        }
        return changed;
    }

    /**
     * Updates team prefix and suffix of given player.
     *
     * @param   player
     *          Player to update prefix/suffix of
     */
    public void updatePrefixSuffix(@NonNull TabPlayer player) {
        for (TabPlayer viewer : feature.getOnlinePlayers().getPlayers()) {
            if (viewer.teamData.hasTeamRegistered(player)) {
                viewer.getScoreboard().updateTeam(
                        player.teamData.teamName,
                        feature.getPrefixCache().get(player.teamData.prefix.getFormat(viewer)),
                        feature.getSuffixCache().get(player.teamData.suffix.getFormat(viewer)),
                        feature.getLastColorCache().get(player.teamData.prefix.getFormat(viewer)).getLastStyle().toEnumChatFormat()
                );
                // Also update the Text Display text (includes multi-line)
                if (feature.getBackgroundManager() != null) {
                    feature.getBackgroundManager().updateDisplay(player, viewer,
                            feature.buildNameTagText(player, viewer));
                }
            }
        }
        feature.getProxyHandler().sendProxyMessage(player);
    }

    /**
     * Loads properties from config.
     *
     * @param   player
     *          Player to load properties for
     */
    public void loadProperties(@NotNull TabPlayer player) {
        player.teamData.prefix = player.loadPropertyFromConfig(this, "tagprefix", "");
        player.teamData.suffix = player.loadPropertyFromConfig(this, "tagsuffix", "");

        // Load extra above/below lines from config
        // Use config.yml defaults where available, but always check groups.yml for overrides
        List<String> aboveDefaults = feature.getConfiguration().getAboveLines();
        List<String> belowDefaults = feature.getConfiguration().getBelowLines();

        // Load at least as many lines as defined in config.yml, or up to 10 if groups define more
        int aboveCount = Math.max(aboveDefaults.size(), 10);
        int belowCount = Math.max(belowDefaults.size(), 10);

        player.teamData.aboveLines = new ArrayList<>();
        for (int i = 0; i < aboveCount; i++) {
            String defaultValue = i < aboveDefaults.size() ? aboveDefaults.get(i) : "";
            Property prop = player.loadPropertyFromConfig(this, "above-line-" + i, defaultValue);
            // Only add if either the default or the group-specific value is non-empty
            if (!prop.getCurrentRawValue().isEmpty() || i < aboveDefaults.size()) {
                player.teamData.aboveLines.add(prop);
            } else {
                break; // No more lines defined
            }
        }

        player.teamData.belowLines = new ArrayList<>();
        for (int i = 0; i < belowCount; i++) {
            String defaultValue = i < belowDefaults.size() ? belowDefaults.get(i) : "";
            Property prop = player.loadPropertyFromConfig(this, "below-line-" + i, defaultValue);
            if (!prop.getCurrentRawValue().isEmpty() || i < belowDefaults.size()) {
                player.teamData.belowLines.add(prop);
            } else {
                break; // No more lines defined
            }
        }
    }

    @Override
    @NotNull
    public ThreadExecutor getCustomThread() {
        return feature.getCustomThread();
    }
}
