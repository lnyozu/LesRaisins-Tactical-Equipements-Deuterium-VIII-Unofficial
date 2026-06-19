package me.xjqsh.lrtactical.item.index;

import com.google.gson.*;
import me.xjqsh.lrtactical.api.index.ICustomItemIndex;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import me.xjqsh.lrtactical.util.LoreTextParser;
import me.xjqsh.lrtactical.util.TooltipHideFlags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ThrowableIndex<T extends ThrowableData, E extends ThrowableItemEntity> implements ICustomItemIndex {
    private final ThrowableType<T, E> type;
    private final Item baseItem;
    private final T data;
    private final ResourceLocation id;
    private final String name;
    private final List<String> tooltip;
    @Nullable
    private final Integer hideTooltip;

    private ThrowableIndex(@NotNull ThrowableType<T, E> type, T data,
                           String name, List<String> tooltip, @Nullable Integer hideTooltip,
                           ResourceLocation id, Item baseItem) {
        this.type = type;
        this.data = data;
        this.id = id;
        this.baseItem = baseItem;
        this.name = name;
        this.tooltip = List.copyOf(tooltip);
        this.hideTooltip = hideTooltip;
    }

    @Nullable
    public static <T extends ThrowableData, E extends ThrowableItemEntity> ThrowableIndex<T, E> deserialize(
            @NotNull ThrowableType<T, E> type, JsonElement data, String name, List<String> tooltip,
            @Nullable Integer hideTooltip, ResourceLocation id, Item baseItem
    ) {
        T throwableData = type.serializer().parse(data);
        if (throwableData == null) {
            return null;
        }
        return new ThrowableIndex<>(type, throwableData, name, tooltip, hideTooltip, id, baseItem);
    }

    public T getData() {
        return data;
    }

    @Override
    public int getMaxStackSize() {
        return data.getStackSize();
    }

    public ThrowableType<T, E> getType() {
        return type;
    }

    public String getDescriptionId() {
        return name;
    }

    public E createEntity(ItemStack stack, LivingEntity thrower) {
        return type.factory().create(stack, thrower, data);
    }

    public ResourceLocation getId() {
        return id;
    }

    public Item getBaseItem() {
        return baseItem;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(baseItem);
        if (stack.getItem() instanceof IThrowable iThrowable) {
            iThrowable.setId(stack, this.getId());
        }
        LoreTextParser.writeToStack(stack, tooltip);
        TooltipHideFlags.writeToStack(stack, hideTooltip);
        return stack;
    }
}
