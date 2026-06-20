package me.xjqsh.lrtactical.server.c4;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.config.ServerConfig;
import me.xjqsh.lrtactical.entity.GrenadeEntity;
import me.xjqsh.lrtactical.item.DetonatorItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EquipmentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class C4ServerManager {
    private static final String DATA_NAME = EquipmentMod.MOD_ID + "_c4_registry";
    private static final long TOMBSTONE_LIFETIME_TICKS = 7L * 24L * 60L * 60L * 20L;
    private static final Deque<QueuedDetonation> CHAIN_QUEUE = new ArrayDeque<>();
    private static final Set<UUID> QUEUED_IDS = new HashSet<>();

    private C4ServerManager() {
    }

    public static boolean canDeploy(ServerLevel level, UUID ownerId) {
        pruneExpired(level.getServer());
        return data(level.getServer()).count(ownerId) < ServerConfig.getRemoteChargeMaxPerPlayer();
    }

    public static int getDeployedCount(ServerLevel level, UUID ownerId) {
        pruneExpired(level.getServer());
        return data(level.getServer()).count(ownerId);
    }

    public static void register(GrenadeEntity charge) {
        if (!(charge.level() instanceof ServerLevel level) || !charge.isRemoteDetonation()) {
            return;
        }
        RegistryData registry = data(level.getServer());
        registry.register(
                charge.getUUID(),
                charge.getDeploymentOwnerUUID(),
                charge.getCreatedGameTime()
        );
    }

    public static void unregister(GrenadeEntity charge) {
        if (!(charge.level() instanceof ServerLevel level) || !charge.isRemoteDetonation()) {
            return;
        }
        MinecraftServer server = level.getServer();
        UUID id = charge.getUUID();
        data(server).remove(id, level.getGameTime());
        invalidateOnlineDetonators(server, id);
        QUEUED_IDS.remove(id);
        CHAIN_QUEUE.removeIf(entry -> entry.entityId.equals(id));
    }

    public static boolean queueChainDetonation(GrenadeEntity charge) {
        if (!(charge.level() instanceof ServerLevel level)
                || charge.isRemoved()
                || !QUEUED_IDS.add(charge.getUUID())) {
            return false;
        }
        CHAIN_QUEUE.addLast(new QueuedDetonation(level.dimension(), charge.getUUID()));
        return true;
    }

    public static LinkState getLinkState(MinecraftServer server, UUID entityId, boolean managedLink) {
        RegistryData registry = data(server);
        if (registry.contains(entityId)) {
            return LinkState.KNOWN_UNLOADED;
        }
        if (registry.wasRemoved(entityId) || managedLink) {
            return LinkState.INVALID;
        }
        // Legacy detonators may point to a pre-registry C4 in an unloaded chunk.
        return LinkState.LEGACY_UNKNOWN;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof GrenadeEntity grenade
                && grenade.isRemoteDetonation()) {
            register(grenade);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        int limit = ServerConfig.getRemoteChargeChainDetonationsPerTick();
        for (int i = 0; i < limit && !CHAIN_QUEUE.isEmpty(); i++) {
            QueuedDetonation queued = CHAIN_QUEUE.removeFirst();
            QUEUED_IDS.remove(queued.entityId);
            ServerLevel level = server.getLevel(queued.dimension);
            if (level != null && level.getEntity(queued.entityId) instanceof GrenadeEntity grenade) {
                grenade.tryQueuedExplosionDetonate();
            }
        }

        if (server.getTickCount() % 200 == 0) {
            pruneExpired(server);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            synchronizePlayerDetonators(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CHAIN_QUEUE.clear();
        QUEUED_IDS.clear();
    }

    private static void pruneExpired(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        List<UUID> expired = data(server).prune(
                now,
                ServerConfig.getRemoteChargeForceCleanupTimeTicks()
        );
        for (UUID id : expired) {
            invalidateOnlineDetonators(server, id);
            QUEUED_IDS.remove(id);
        }
        if (!expired.isEmpty()) {
            CHAIN_QUEUE.removeIf(entry -> expired.contains(entry.entityId));
        }
    }

    private static void synchronizePlayerDetonators(ServerPlayer player) {
        RegistryData registry = data(player.server);
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof DetonatorItem detonator)) {
                continue;
            }
            UUID linkedId = detonator.getLinkedEntityId(stack);
            if (linkedId == null) {
                continue;
            }
            if (registry.contains(linkedId)) {
                changed |= detonator.markManaged(stack);
            } else if (registry.wasRemoved(linkedId)) {
                changed |= detonator.invalidateLink(stack);
            }
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void invalidateOnlineDetonators(MinecraftServer server, UUID entityId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean changed = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                var stack = player.getInventory().getItem(slot);
                if (stack.getItem() instanceof DetonatorItem detonator
                        && entityId.equals(detonator.getLinkedEntityId(stack))) {
                    changed |= detonator.invalidateLink(stack);
                }
            }
            if (changed) {
                player.inventoryMenu.broadcastChanges();
            }
        }
    }

    private static RegistryData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                RegistryData::load,
                RegistryData::new,
                DATA_NAME
        );
    }

    public enum LinkState {
        KNOWN_UNLOADED,
        LEGACY_UNKNOWN,
        INVALID
    }

    private record QueuedDetonation(ResourceKey<Level> dimension, UUID entityId) {
    }

    private record ChargeRecord(UUID ownerId, long createdGameTime) {
    }

    private static final class RegistryData extends SavedData {
        private final Map<UUID, ChargeRecord> charges = new HashMap<>();
        private final Map<UUID, Long> removed = new HashMap<>();

        private static RegistryData load(CompoundTag tag) {
            RegistryData data = new RegistryData();
            ListTag chargeList = tag.getList("Charges", Tag.TAG_COMPOUND);
            for (Tag entry : chargeList) {
                CompoundTag chargeTag = (CompoundTag) entry;
                if (!chargeTag.hasUUID("Id")) {
                    continue;
                }
                UUID owner = chargeTag.hasUUID("Owner") ? chargeTag.getUUID("Owner") : null;
                data.charges.put(
                        chargeTag.getUUID("Id"),
                        new ChargeRecord(owner, chargeTag.getLong("CreatedGameTime"))
                );
            }
            ListTag removedList = tag.getList("Removed", Tag.TAG_COMPOUND);
            for (Tag entry : removedList) {
                CompoundTag removedTag = (CompoundTag) entry;
                if (removedTag.hasUUID("Id")) {
                    data.removed.put(removedTag.getUUID("Id"), removedTag.getLong("RemovedAt"));
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag chargeList = new ListTag();
            for (Map.Entry<UUID, ChargeRecord> entry : this.charges.entrySet()) {
                CompoundTag chargeTag = new CompoundTag();
                chargeTag.putUUID("Id", entry.getKey());
                if (entry.getValue().ownerId != null) {
                    chargeTag.putUUID("Owner", entry.getValue().ownerId);
                }
                chargeTag.putLong("CreatedGameTime", entry.getValue().createdGameTime);
                chargeList.add(chargeTag);
            }
            tag.put("Charges", chargeList);

            ListTag removedList = new ListTag();
            for (Map.Entry<UUID, Long> entry : this.removed.entrySet()) {
                CompoundTag removedTag = new CompoundTag();
                removedTag.putUUID("Id", entry.getKey());
                removedTag.putLong("RemovedAt", entry.getValue());
                removedList.add(removedTag);
            }
            tag.put("Removed", removedList);
            return tag;
        }

        private void register(UUID id, UUID ownerId, long createdGameTime) {
            ChargeRecord previous = this.charges.putIfAbsent(
                    id,
                    new ChargeRecord(ownerId, createdGameTime)
            );
            if (previous == null) {
                this.removed.remove(id);
                this.setDirty();
            } else if (previous.ownerId == null && ownerId != null) {
                this.charges.put(id, new ChargeRecord(ownerId, previous.createdGameTime));
                this.setDirty();
            }
        }

        private void remove(UUID id, long now) {
            this.charges.remove(id);
            this.removed.put(id, now);
            this.setDirty();
        }

        private int count(UUID ownerId) {
            int count = 0;
            for (ChargeRecord record : this.charges.values()) {
                if (ownerId.equals(record.ownerId)) {
                    count++;
                }
            }
            return count;
        }

        private boolean contains(UUID id) {
            return this.charges.containsKey(id);
        }

        private boolean wasRemoved(UUID id) {
            return this.removed.containsKey(id);
        }

        private List<UUID> prune(long now, int cleanupTicks) {
            List<UUID> expired = new ArrayList<>();
            if (cleanupTicks > 0) {
                this.charges.entrySet().removeIf(entry -> {
                    long age = Math.max(0L, now - entry.getValue().createdGameTime);
                    if (age >= cleanupTicks) {
                        expired.add(entry.getKey());
                        this.removed.put(entry.getKey(), now);
                        return true;
                    }
                    return false;
                });
            }
            boolean removedOldTombstones = this.removed.entrySet().removeIf(entry ->
                    Math.max(0L, now - entry.getValue()) > TOMBSTONE_LIFETIME_TICKS
            );
            if (!expired.isEmpty() || removedOldTombstones) {
                this.setDirty();
            }
            return expired;
        }
    }
}
