package me.neznamy.tab.shared.features.nametags;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.shared.config.file.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Class storing teams feature configuration.
 */
@Getter
@RequiredArgsConstructor
public class TeamConfiguration {

    @NotNull private final ConfigurationSection section;
    @NotNull private final String enableCollision;
    @NotNull private final String invisibleNameTags;
    private final boolean canSeeFriendlyInvisibles;
    @NotNull private final String disableCondition;
    /** Whether to hide the semi-transparent background behind nametags using Text Display entities */
    private final boolean hideNameTagBackground;
    /** Whether the nametag text should render through translucent blocks (water, clouds, etc.) */
    private final boolean nametagSeeThrough;
    /** Vertical offset (in blocks) for the nametag Text Display entity */
    private final float nameTagYOffset;
    /** Lines displayed above the main nametag line (prefix+name+suffix). Supports placeholders. */
    @NotNull private final List<String> aboveLines;
    /** Lines displayed below the main nametag line (prefix+name+suffix). Supports placeholders. */
    @NotNull private final List<String> belowLines;

    /**
     * Returns instance of this class created from given configuration section. If there are
     * issues in the configuration, console warns are printed.
     *
     * @param   section
     *          Configuration section to load from
     * @return  Loaded instance from given configuration section
     */
    @NotNull
    public static TeamConfiguration fromSection(@NotNull ConfigurationSection section) {
        // Check keys
        section.checkForUnknownKey(Arrays.asList("enabled", "enable-collision", "invisible-nametags", "sorting-types",
                "case-sensitive-sorting", "can-see-friendly-invisibles", "disable-condition",
                "hide-nametag-background", "nametag-see-through", "nametag-y-offset", "above-lines", "below-lines"));

        return new TeamConfiguration(
                section,
                section.getObject("enable-collision", "true").toString(),
                section.getObject("invisible-nametags", "false").toString(),
                section.getBoolean("can-see-friendly-invisibles", false),
                section.getString("disable-condition", "%world%=disabledworld"),
                section.getBoolean("hide-nametag-background", false),
                section.getBoolean("nametag-see-through", true),
                ((Number) section.getObject("nametag-y-offset", 0.0)).floatValue(),
                section.getStringList("above-lines", Collections.emptyList()),
                section.getStringList("below-lines", Collections.emptyList())
        );
    }
}


