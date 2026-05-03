package me.neznamy.tab.platforms.bukkit.v26_1;

import me.neznamy.tab.platforms.bukkit.BukkitTabPlayer;
import me.neznamy.tab.platforms.bukkit.provider.NameTagBackgroundHandler;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.chat.component.TabComponent;
import me.neznamy.tab.shared.platform.TabPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NMS implementation for Minecraft 26.1 of {@link NameTagBackgroundHandler}.
 * <p>
 * Spawns a {@code TextDisplay} entity as a passenger on the target player.
 * The entity's background color is set to fully transparent (ARGB 0x00000000),
 * effectively removing the semi-transparent black rectangle behind the nametag.
 * <p>
 * The vanilla scoreboard team nametag visibility is set to {@code NEVER} when
 * this handler is active, so only the custom Text Display is visible.
 */
public class NMSNameTagBackgroundHandler implements NameTagBackgroundHandler {

    /** Generates unique negative entity IDs to avoid collision with real server entities. */
    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(-500000);

    // ──────────────── Entity Data Accessors (extracted via reflection) ────────────────

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> getAccessor(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (EntityDataAccessor<T>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to get EntityDataAccessor: " + clazz.getName() + "." + fieldName, e);
        }
    }

    private static final EntityDataAccessor<Byte> ENTITY_FLAGS = getAccessor(Entity.class, "DATA_SHARED_FLAGS_ID");
    private static final EntityDataAccessor<Boolean> NO_GRAVITY = getAccessor(Entity.class, "DATA_NO_GRAVITY");
    private static final EntityDataAccessor<Byte> BILLBOARD = getAccessor(Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
    private static final EntityDataAccessor<Float> VIEW_RANGE = getAccessor(Display.class, "DATA_VIEW_RANGE_ID");
    private static final EntityDataAccessor<Vector3f> TRANSLATION = getAccessor(Display.class, "DATA_TRANSLATION_ID");
    private static final EntityDataAccessor<Component> TEXT = getAccessor(TextDisplay.class, "DATA_TEXT_ID");
    private static final EntityDataAccessor<Integer> LINE_WIDTH = getAccessor(TextDisplay.class, "DATA_LINE_WIDTH_ID");
    private static final EntityDataAccessor<Integer> BACKGROUND_COLOR = getAccessor(TextDisplay.class, "DATA_BACKGROUND_COLOR_ID");
    private static final EntityDataAccessor<Byte> TEXT_OPACITY = getAccessor(TextDisplay.class, "DATA_TEXT_OPACITY_ID");
    private static final EntityDataAccessor<Byte> STYLE_FLAGS = getAccessor(TextDisplay.class, "DATA_STYLE_FLAGS_ID");

    // ──────────────── Constants ────────────────

    /** ARGB: alpha=0 → fully transparent background */
    private static final int TRANSPARENT_BACKGROUND = 0x00000000;

    /** Billboard: CENTER (3) → always faces the camera */
    private static final byte BILLBOARD_CENTER = 3;

    /**
     * Text display flags:
     * bit 1 (0x02) = SEE_THROUGH
     * Alignment bits (3-4) = 0 → CENTER
     */
    private static final byte TEXT_DISPLAY_FLAGS = 0x02;


    // ──────────────── Reflection for passengers packet ────────────────

    private static final Field PASSENGERS_FIELD;

    static {
        Field passengers = null;
        try {
            for (Field f : ClientboundSetPassengersPacket.class.getDeclaredFields()) {
                if (f.getType() == int[].class) {
                    passengers = f;
                    passengers.setAccessible(true);
                    break;
                }
            }
        } catch (Exception ignored) {}
        PASSENGERS_FIELD = passengers;
    }

    // ──────────────── Entity tracking ────────────────

    /** targetUUID -> (viewerUUID -> fakeEntityId) */
    private final Map<UUID, Map<UUID, Integer>> entities = new ConcurrentHashMap<>();

    /** Vertical offset in blocks */
    private float yOffset = 0.0f;

    // ──────────────── Public API ────────────────

    @Override
    public void setYOffset(float offset) {
        this.yOffset = offset;
    }

    @Override
    public void spawn(@NotNull TabPlayer target, @NotNull TabPlayer viewer, @NotNull TabComponent text) {
        int entityId = ENTITY_ID_COUNTER.getAndDecrement();
        entities.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(viewer.getUniqueId(), entityId);

        CraftPlayer nmsTarget = (CraftPlayer) ((BukkitTabPlayer) target).getPlayer();
        CraftPlayer nmsViewer = (CraftPlayer) ((BukkitTabPlayer) viewer).getPlayer();

        // 1) Spawn the Text Display entity at the target's position
        sendPacket(nmsViewer, new ClientboundAddEntityPacket(
                entityId,
                UUID.randomUUID(),
                nmsTarget.getLocation().getX(),
                nmsTarget.getLocation().getY() + nmsTarget.getHeight() + 0.5,
                nmsTarget.getLocation().getZ(),
                0f, 0f,
                EntityType.TEXT_DISPLAY,
                0,
                Vec3.ZERO,
                0
        ));

        // 2) Send full metadata
        sendPacket(nmsViewer, new ClientboundSetEntityDataPacket(entityId, buildFullMetadata(text)));

        // 3) Mount the display entity on the target player
        sendPassengersPacket(nmsTarget, nmsViewer, entityId);
    }

    @Override
    public void update(@NotNull TabPlayer target, @NotNull TabPlayer viewer, @NotNull TabComponent text) {
        Map<UUID, Integer> viewerMap = entities.get(target.getUniqueId());
        if (viewerMap == null) {
            spawn(target, viewer, text);
            return;
        }
        Integer entityId = viewerMap.get(viewer.getUniqueId());
        if (entityId == null) {
            spawn(target, viewer, text);
            return;
        }

        CraftPlayer nmsViewer = (CraftPlayer) ((BukkitTabPlayer) viewer).getPlayer();

        // Only update the text component
        List<SynchedEntityData.DataValue<?>> metadata = List.of(
                SynchedEntityData.DataValue.create(TEXT, (Component) text.convert())
        );
        sendPacket(nmsViewer, new ClientboundSetEntityDataPacket(entityId, metadata));
    }

    @Override
    public void destroy(@NotNull TabPlayer target, @NotNull TabPlayer viewer) {
        Map<UUID, Integer> viewerMap = entities.get(target.getUniqueId());
        if (viewerMap == null) return;
        Integer entityId = viewerMap.remove(viewer.getUniqueId());
        if (entityId == null) return;

        CraftPlayer nmsViewer = (CraftPlayer) ((BukkitTabPlayer) viewer).getPlayer();
        sendPacket(nmsViewer, new ClientboundRemoveEntitiesPacket(entityId));
    }

    @Override
    public void destroyAll(@NotNull TabPlayer target, @NotNull Iterable<TabPlayer> viewers) {
        Map<UUID, Integer> viewerMap = entities.remove(target.getUniqueId());
        if (viewerMap == null) return;

        for (TabPlayer viewer : viewers) {
            Integer entityId = viewerMap.get(viewer.getUniqueId());
            if (entityId == null) continue;
            CraftPlayer nmsViewer = (CraftPlayer) ((BukkitTabPlayer) viewer).getPlayer();
            sendPacket(nmsViewer, new ClientboundRemoveEntitiesPacket(entityId));
        }
    }

    @Override
    public void onViewerQuit(@NotNull TabPlayer viewer) {
        UUID viewerId = viewer.getUniqueId();
        for (Map<UUID, Integer> map : entities.values()) {
            map.remove(viewerId);
        }
    }

    @Override
    public boolean hasDisplay(@NotNull TabPlayer target, @NotNull TabPlayer viewer) {
        Map<UUID, Integer> viewerMap = entities.get(target.getUniqueId());
        return viewerMap != null && viewerMap.containsKey(viewer.getUniqueId());
    }

    // ──────────────── Internal helpers ────────────────

    @NotNull
    private List<SynchedEntityData.DataValue<?>> buildFullMetadata(@NotNull TabComponent text) {
        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();

        // Entity flags: invisible (0x20)
        metadata.add(SynchedEntityData.DataValue.create(ENTITY_FLAGS, (byte) 0x20));

        // No gravity
        metadata.add(SynchedEntityData.DataValue.create(NO_GRAVITY, true));

        // Billboard: CENTER
        metadata.add(SynchedEntityData.DataValue.create(BILLBOARD, BILLBOARD_CENTER));

        // View range
        metadata.add(SynchedEntityData.DataValue.create(VIEW_RANGE, 1.0f));

        // Translation (vertical offset)
        if (yOffset != 0.0f) {
            metadata.add(SynchedEntityData.DataValue.create(TRANSLATION, new Vector3f(0, yOffset, 0)));
        }

        // Text
        metadata.add(SynchedEntityData.DataValue.create(TEXT, (Component) text.convert()));

        // Line width
        metadata.add(SynchedEntityData.DataValue.create(LINE_WIDTH, 200));

        // Background: FULLY TRANSPARENT
        metadata.add(SynchedEntityData.DataValue.create(BACKGROUND_COLOR, TRANSPARENT_BACKGROUND));

        // Text opacity: fully opaque text
        metadata.add(SynchedEntityData.DataValue.create(TEXT_OPACITY, (byte) -1));

        // Flags: SEE_THROUGH, CENTER alignment
        metadata.add(SynchedEntityData.DataValue.create(STYLE_FLAGS, TEXT_DISPLAY_FLAGS));

        return metadata;
    }

    private void sendPassengersPacket(@NotNull CraftPlayer target, @NotNull CraftPlayer viewer, int displayEntityId) {
        try {
            int[] currentPassengers = target.getHandle().getPassengers().stream()
                    .mapToInt(Entity::getId)
                    .toArray();

            int[] allPassengers = new int[currentPassengers.length + 1];
            System.arraycopy(currentPassengers, 0, allPassengers, 0, currentPassengers.length);
            allPassengers[currentPassengers.length] = displayEntityId;

            ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(target.getHandle());
            if (PASSENGERS_FIELD != null) {
                PASSENGERS_FIELD.set(packet, allPassengers);
            }
            sendPacket(viewer, packet);
        } catch (Exception e) {
            TAB.getInstance().getErrorManager().printError("Failed to send passengers packet for nametag background", e);
        }
    }

    private void sendPacket(@NotNull CraftPlayer player, @NotNull Packet<?> packet) {
        player.getHandle().connection.send(packet);
    }
}

