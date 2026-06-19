package me.xjqsh.lrtactical.mixin.client;

import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
        extends EffectRenderingInventoryScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    private static final String LRTACTICAL_ITEM_GROUP_PREFIX = "item_group.lrtactical.";

    @Shadow
    private static CreativeModeTab selectedTab;

    @Shadow
    @Final
    private static SimpleContainer CONTAINER;

    protected CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu,
                                               Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void lrtactical$moveItemGroupToBottom(ItemStack stack,
                                                  CallbackInfoReturnable<List<Component>> cir) {
        List<Component> tooltip = cir.getReturnValue();
        List<Component> reorderedTooltip = new ArrayList<>(tooltip);
        List<Component> itemGroupLines = reorderedTooltip.stream()
                .filter(CreativeModeInventoryScreenMixin::lrtactical$isItemGroupLine)
                .toList();

        Component expectedItemGroup = lrtactical$getExpectedItemGroup(stack);
        if (itemGroupLines.isEmpty()
                && expectedItemGroup != null
                && lrtactical$shouldShowItemGroups()) {
            itemGroupLines = List.of(expectedItemGroup);
        }

        if (itemGroupLines.isEmpty()) {
            return;
        }

        reorderedTooltip.removeAll(itemGroupLines);
        reorderedTooltip.addAll(itemGroupLines);
        if (!reorderedTooltip.equals(tooltip)) {
            cir.setReturnValue(reorderedTooltip);
        }
    }

    private static boolean lrtactical$isItemGroupLine(Component component) {
        return component.getContents() instanceof TranslatableContents contents
                && contents.getKey().startsWith(LRTACTICAL_ITEM_GROUP_PREFIX);
    }

    private boolean lrtactical$shouldShowItemGroups() {
        boolean hoveringCreativeItem = this.hoveredSlot != null
                && this.hoveredSlot.container == CONTAINER;
        return selectedTab.getType() != CreativeModeTab.Type.CATEGORY || !hoveringCreativeItem;
    }

    @Nullable
    private static Component lrtactical$getExpectedItemGroup(ItemStack stack) {
        if (IMeleeWeapon.of(stack) != null) {
            return ModItems.MELEE_TAB.get().getDisplayName().copy().withStyle(ChatFormatting.BLUE);
        }
        if (IConsumable.of(stack) != null) {
            return ModItems.CONSUMABLE_TAB.get().getDisplayName().copy().withStyle(ChatFormatting.BLUE);
        }
        if (IThrowable.of(stack) != null) {
            return ModItems.THROWABLE_TAB.get().getDisplayName().copy().withStyle(ChatFormatting.BLUE);
        }
        return null;
    }
}
