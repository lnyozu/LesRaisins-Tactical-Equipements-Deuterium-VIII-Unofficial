package me.xjqsh.lrtactical.resource.manager;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.manager.JsonDataManager;
import me.xjqsh.lrtactical.init.ModRegistries;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.resource.ExternalResourceOverlay;
import me.xjqsh.lrtactical.util.LoreTextParser;
import me.xjqsh.lrtactical.util.TooltipHideFlags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public class ThrowableIndexManager extends JsonDataManager<ThrowableIndex<?, ?>> {
    public ThrowableIndexManager(Gson pGson) {
        super(null, pGson, "index/throwable", "ThrowableIndex");
    }

    private Map<ResourceLocation, String> networkCache = new HashMap<>();

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        dataMap.clear();
        Map<ResourceLocation, String> cacheBuilder = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                GunMod.LOGGER.error(getMarker(), "Failed to load index file {}: Expected object, got {} ", id, element);
                continue;
            }
            JsonObject pJson = element.getAsJsonObject();

            try {
                ThrowableIndex<?, ?> index = ThrowableIndexManager.parse(pJson, id);
                dataMap.put(id, index);
                cacheBuilder.put(id, pJson.toString());
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(getMarker(), "Failed to load index file {}", id, e);
            }
        }

        // Overlay external pack files (they take priority over built-in)
        ExternalResourceOverlay.overlayIndex("index/throwable", dataMap, cacheBuilder, (json, id) -> {
            try {
                return ThrowableIndexManager.parse(json, id);
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(getMarker(), "Failed to load external index file {}", id, e);
                return null;
            }
        });

        this.networkCache = ImmutableMap.copyOf(cacheBuilder);
    }

    public Map<ResourceLocation, String> getCache() {
        return networkCache;
    }

    public static ThrowableIndex<?, ?> parse(JsonObject pJson, ResourceLocation id) throws JsonParseException {
        String name = GsonHelper.getAsString(pJson, "name", "unknown.lrtactical.name");
        var tooltip = LoreTextParser.parseTooltip(pJson);
        var hideTooltip = TooltipHideFlags.parse(pJson);

        String type_name = GsonHelper.getAsString(pJson, "type");
        var type = ModRegistries.THROWABLE_TYPE_SUPPLIER.get().getValue(new ResourceLocation(type_name));
        if (type == null) {
            throw new JsonParseException("Unknown type name \"" + type_name + "\"");
        }

        String baseItem = GsonHelper.getAsString(pJson, "base_item", "lrtactical:throwable");
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(baseItem));
        if (item == null) {
            throw new JsonParseException("Unknown item id \"" + type_name + "\"");
        }

        JsonObject data = GsonHelper.getAsJsonObject(pJson, "data");

        return ThrowableIndex.deserialize(type, data, name, tooltip, hideTooltip, id, item);
    }
}
