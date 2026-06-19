package me.xjqsh.lrtactical.client.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.manager.JsonDataManager;
import me.xjqsh.lrtactical.client.resource.display.MeleeDisplayInstance;
import me.xjqsh.lrtactical.resource.ExternalResourceOverlay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public class MeleeDisplayManager extends JsonDataManager<MeleeDisplayInstance> {
    public MeleeDisplayManager(Gson pGson) {
        super(null, pGson, "display/melee", "MeleeDisplay");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        dataMap.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();
            try {
                var pojo = getGson().fromJson(element, MeleeDisplayInstance.MeleeDisplay.class);
                MeleeDisplayInstance data = MeleeDisplayInstance.create(pojo, id);
                dataMap.put(id, data);
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(getMarker(), "Failed to load display file {}", id, e);
            }
        }

        // Overlay external pack files (they take priority over built-in)
        ExternalResourceOverlay.overlayDisplay("display/melee", dataMap, (json, id) -> {
            var pojo = getGson().fromJson(json, MeleeDisplayInstance.MeleeDisplay.class);
            return MeleeDisplayInstance.create(pojo, id);
        });
    }
}
