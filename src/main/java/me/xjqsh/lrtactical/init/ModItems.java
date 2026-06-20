package me.xjqsh.lrtactical.init;


import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.client.resource.ClientContentAvailability;
import me.xjqsh.lrtactical.item.*;
import me.xjqsh.lrtactical.item.index.ConsumableIndex;
import me.xjqsh.lrtactical.item.index.MeleeWeaponIndex;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModItems {
    private static final ResourceLocation DEFAULT_THROWABLE_ICON = id("m67");
    private static final ResourceLocation DEFAULT_CONSUMABLE_ICON = id("blood_pack");
    private static final ResourceLocation DEFAULT_MELEE_ICON = id("karambit");
    private static final ResourceLocation C4_INDEX = id("c4");
    private static final ResourceLocation FLASH_SHIELD_DISPLAY =
            id("display/shield/flash_shield.json");
    private static final ResourceLocation DETONATOR_MODEL =
            id("models/item/detonator.json");

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EquipmentMod.MOD_ID);
    public static final RegistryObject<CreativeModeTab> THROWABLE_TAB = TABS.register("throwable",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("item_group.lrtactical.throwable"))
                    .icon(ModItems::getThrowableIcon)
                    .displayItems(ModItems::fillThrowables)
                    .build()
    );
    public static final RegistryObject<CreativeModeTab> CONSUMABLE_TAB = TABS.register("consumable",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("item_group.lrtactical.consumable"))
                    .icon(ModItems::getConsumableIcon)
                    .displayItems(ModItems::fillConsumables)
                    .withTabsBefore(THROWABLE_TAB.getId())
                    .build()
    );
    public static final RegistryObject<CreativeModeTab> MELEE_TAB = TABS.register("melee",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("item_group.lrtactical.melee"))
                    .icon(ModItems::getMeleeIcon)
                    .displayItems(ModItems::fillMeleeWeapons)
                    .withTabsBefore(CONSUMABLE_TAB.getId())
                    .build()
    );

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EquipmentMod.MOD_ID);
    public static RegistryObject<ConsumableItem> CONSUMABLE = ITEMS.register("consumable", ConsumableItem::new);
    public static RegistryObject<ThrowableItem> THROWABLE = ITEMS.register("throwable", ThrowableItem::new);
    public static RegistryObject<MeleeItem> MELEE = ITEMS.register("melee", MeleeItem::new);
    public static RegistryObject<FlashShieldItem> FLASH_SHIELD = ITEMS.register("flash_shield", FlashShieldItem::new);
    public static RegistryObject<DetonatorItem> DETONATOR = ITEMS.register("detonator", DetonatorItem::new);
    public static RegistryObject<Item> THROWABLE_TAB_ICON = ITEMS.register("throwable_tab_icon", () -> new Item(new Item.Properties()));
    public static RegistryObject<Item> CONSUMABLE_TAB_ICON = ITEMS.register("consumable_tab_icon", () -> new Item(new Item.Properties()));
    public static RegistryObject<Item> MELEE_TAB_ICON = ITEMS.register("melee_tab_icon", () -> new Item(new Item.Properties()));

    public static ItemStack getThrowableIcon() {
        if (!hasThrowableIndex(DEFAULT_THROWABLE_ICON)
                || !hasClientThrowableDisplay(DEFAULT_THROWABLE_ICON)) {
            return new ItemStack(THROWABLE_TAB_ICON.get());
        }
        ItemStack stack = new ItemStack(THROWABLE.get());
        IThrowable iThrowable = IThrowable.of(stack);
        if (iThrowable != null) {
            iThrowable.setId(stack, DEFAULT_THROWABLE_ICON);
        }
        return stack;
    }

    public static ItemStack getConsumableIcon() {
        if (!hasConsumableIndex(DEFAULT_CONSUMABLE_ICON)
                || !hasClientConsumableDisplay(DEFAULT_CONSUMABLE_ICON)) {
            return new ItemStack(CONSUMABLE_TAB_ICON.get());
        }
        ItemStack stack = new ItemStack(CONSUMABLE.get());
        IConsumable consumable = IConsumable.of(stack);
        if (consumable != null) {
            consumable.setId(stack, DEFAULT_CONSUMABLE_ICON);
        }
        return stack;
    }

    public static ItemStack getMeleeIcon() {
        if (!hasMeleeIndex(DEFAULT_MELEE_ICON)
                || !hasClientMeleeDisplay(DEFAULT_MELEE_ICON)) {
            return new ItemStack(MELEE_TAB_ICON.get());
        }
        ItemStack stack = new ItemStack(MELEE.get());
        IMeleeWeapon iMeleeWeapon = IMeleeWeapon.of(stack);
        if (iMeleeWeapon != null) {
            iMeleeWeapon.setId(stack, DEFAULT_MELEE_ICON);
        }
        return stack;
    }

    public static void fillConsumables(CreativeModeTab.ItemDisplayParameters pParameters, CreativeModeTab.Output pOutput) {
        for (ConsumableIndex index : LrTacticalAPI.getConsumableIndexes()) {
            if (!hasClientConsumableDisplay(index.getId())) {
                continue;
            }
            ItemStack stack = index.createItemStack();
            pOutput.accept(stack);
        }
    }

    public static void fillThrowables(CreativeModeTab.ItemDisplayParameters pParameters, CreativeModeTab.Output pOutput) {
        for (ThrowableIndex<?, ?> index : LrTacticalAPI.getThrowableIndexes()) {
            if (!hasClientThrowableDisplay(index.getId())) {
                continue;
            }
            ItemStack stack = index.createItemStack();
            pOutput.accept(stack);
        }
        if (hasThrowableIndex(C4_INDEX) && hasClientResource(DETONATOR_MODEL)) {
            pOutput.accept(new ItemStack(DETONATOR.get()));
        }
    }

    public static void fillMeleeWeapons(CreativeModeTab.ItemDisplayParameters pParameters, CreativeModeTab.Output pOutput) {
        for (MeleeWeaponIndex<?> index : LrTacticalAPI.getMeleeIndexes()) {
            if (!hasClientMeleeDisplay(index.getId())) {
                continue;
            }
            ItemStack stack = index.createItemStack();
            pOutput.accept(stack);
        }
        if (hasMeleeIndex(DEFAULT_MELEE_ICON) && hasClientResource(FLASH_SHIELD_DISPLAY)) {
            pOutput.accept(new ItemStack(FLASH_SHIELD.get()));
        }
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(EquipmentMod.MOD_ID, path);
    }

    private static boolean hasConsumableIndex(ResourceLocation id) {
        return CommonAssetsManager.get().getConsumableIndex(id) != null;
    }

    private static boolean hasThrowableIndex(ResourceLocation id) {
        return CommonAssetsManager.get().getThrowableIndex(id) != null;
    }

    private static boolean hasMeleeIndex(ResourceLocation id) {
        return CommonAssetsManager.get().getMeleeIndex(id) != null;
    }

    private static boolean hasClientConsumableDisplay(ResourceLocation id) {
        return FMLEnvironment.dist != Dist.CLIENT
                || ClientContentAvailability.hasConsumableDisplay(id);
    }

    private static boolean hasClientThrowableDisplay(ResourceLocation id) {
        return FMLEnvironment.dist != Dist.CLIENT
                || ClientContentAvailability.hasThrowableDisplay(id);
    }

    private static boolean hasClientMeleeDisplay(ResourceLocation id) {
        return FMLEnvironment.dist != Dist.CLIENT
                || ClientContentAvailability.hasMeleeDisplay(id);
    }

    private static boolean hasClientResource(ResourceLocation id) {
        return FMLEnvironment.dist != Dist.CLIENT
                || ClientContentAvailability.hasResource(id);
    }
}
