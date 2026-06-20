package me.xjqsh.lrtactical.item;

import me.xjqsh.lrtactical.entity.GrenadeEntity;
import me.xjqsh.lrtactical.server.c4.C4ServerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class DetonatorItem extends Item {
    private static final String TAG_LINKED_ENTITY = "linked_entity";
    private static final String TAG_LINKED_DIMENSION = "linked_dimension";
    private static final String TAG_MANAGED_LINK = "managed_link";
    private static final String TAG_INVALID_LINK = "invalid_link";

    public DetonatorItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public void recordEntity(Entity entity, ItemStack detonatorStack) {
        detonatorStack.getOrCreateTag().putUUID(TAG_LINKED_ENTITY, entity.getUUID());
        detonatorStack.getOrCreateTag().putString(
                TAG_LINKED_DIMENSION,
                entity.level().dimension().location().toString()
        );
        detonatorStack.getOrCreateTag().putBoolean(TAG_MANAGED_LINK, true);
        detonatorStack.getOrCreateTag().remove(TAG_INVALID_LINK);
    }

    public UUID getLinkedEntityId(ItemStack detonatorStack) {
        if (detonatorStack.hasTag() && detonatorStack.getTag().hasUUID(TAG_LINKED_ENTITY)) {
            return detonatorStack.getTag().getUUID(TAG_LINKED_ENTITY);
        }
        return null;
    }

    public DetonationResult detonate(ItemStack detonatorStack, Entity detonator) {
        UUID linkedId = getLinkedEntityId(detonatorStack);
        if (linkedId == null) {
            return isInvalidLink(detonatorStack)
                    ? DetonationResult.INVALID
                    : DetonationResult.NO_SIGNAL;
        }
        if (detonator.level() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            ServerLevel preferredLevel = getLinkedLevel(detonatorStack, server);
            if (tryDetonate(preferredLevel, linkedId)) {
                return DetonationResult.SUCCESS;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (level != preferredLevel && tryDetonate(level, linkedId)) {
                    return DetonationResult.SUCCESS;
                }
            }
            C4ServerManager.LinkState state = C4ServerManager.getLinkState(
                    server,
                    linkedId,
                    isManagedLink(detonatorStack)
            );
            if (state == C4ServerManager.LinkState.INVALID) {
                invalidateLink(detonatorStack);
                return DetonationResult.INVALID;
            }
        }
        return DetonationResult.NO_SIGNAL;
    }

    private static ServerLevel getLinkedLevel(ItemStack stack, MinecraftServer server) {
        if (!stack.hasTag()) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(
                stack.getTag().getString(TAG_LINKED_DIMENSION)
        );
        if (dimensionId == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
    }

    private static boolean tryDetonate(ServerLevel level, UUID linkedId) {
        return level != null
                && level.getEntity(linkedId) instanceof GrenadeEntity grenade
                && grenade.tryRemoteDetonate();
    }

    public boolean markManaged(ItemStack stack) {
        if (isManagedLink(stack)) {
            return false;
        }
        stack.getOrCreateTag().putBoolean(TAG_MANAGED_LINK, true);
        return true;
    }

    public boolean invalidateLink(ItemStack stack) {
        if (!stack.hasTag()) {
            return false;
        }
        boolean changed = stack.getTag().contains(TAG_LINKED_ENTITY)
                || stack.getTag().contains(TAG_LINKED_DIMENSION)
                || !stack.getTag().getBoolean(TAG_INVALID_LINK);
        stack.getTag().remove(TAG_LINKED_ENTITY);
        stack.getTag().remove(TAG_LINKED_DIMENSION);
        stack.getTag().remove(TAG_MANAGED_LINK);
        stack.getTag().putBoolean(TAG_INVALID_LINK, true);
        return changed;
    }

    private static boolean isManagedLink(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(TAG_MANAGED_LINK);
    }

    private static boolean isInvalidLink(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(TAG_INVALID_LINK);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isInvalidLink(stack)) {
            tooltip.add(Component.translatable("tooltip.lrtactical.detonator.invalid")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        ItemStack detonatorStack = pPlayer.getItemInHand(pUsedHand);
        DetonationResult result = detonate(detonatorStack, pPlayer);
        if (result == DetonationResult.SUCCESS) {
            // The explosion callback may invalidate and synchronize this stack before use() returns.
            // Explicitly replace the hand slot and synchronize it again so the consumed detonator
            // cannot remain as a client-side ghost item.
            pPlayer.setItemInHand(pUsedHand, ItemStack.EMPTY);
            if (pPlayer instanceof ServerPlayer serverPlayer) {
                serverPlayer.inventoryMenu.broadcastChanges();
                if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
                    serverPlayer.containerMenu.broadcastChanges();
                }
                serverPlayer.sendSystemMessage(
                        Component.translatable("message.lrtactical.detonator.success")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
            return InteractionResultHolder.sidedSuccess(ItemStack.EMPTY, pLevel.isClientSide());
        } else if (pPlayer instanceof ServerPlayer serverPlayer) {
            if (result == DetonationResult.INVALID) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("message.lrtactical.detonator.invalid")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            } else {
                serverPlayer.sendSystemMessage(
                        Component.translatable("message.lrtactical.detonator.no_signal")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
        }
        return InteractionResultHolder.pass(detonatorStack);
    }

    public enum DetonationResult {
        SUCCESS,
        NO_SIGNAL,
        INVALID
    }
}
