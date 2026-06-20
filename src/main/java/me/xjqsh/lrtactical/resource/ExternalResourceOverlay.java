package me.xjqsh.lrtactical.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 在 Manager 的 apply() 方法末尾扫描外部目录 (tacz/lrtactical_official_resources/) 中的 JSON 文件，
 * 并覆盖内置资源。外部文件优先于 JAR 内置资源。
 * <p>
 * 仅处理 namespace=lrtactical 的官方资源包。
 * 其他 namespace 的外部包由 TACZ 的 GunPackLoader 处理。
 */
public final class ExternalResourceOverlay {

    private static final Path PACK_ROOT = FMLPaths.GAMEDIR.get()
            .resolve("tacz")
            .resolve(DefaultPackExtractor.EXTERNAL_PACK_DIRECTORY);

    private ExternalResourceOverlay() {
    }

    /**
     * 为 Index Manager 覆盖外部文件。
     * 外部文件在 tacz/lrtactical_official_resources/data/lrtactical/{subPath}/*.json
     * 注意：cacheMap 必须是可覆写的 Map（如 HashMap），不能是 ImmutableMap.Builder
     *
     * @param subPath 子路径，如 "index/melee"
     * @param dataMap 目标数据 Map（可覆写）
     * @param cacheMap 网络缓存 Map（可覆写），key 相同的条目会被外部版本覆盖
     * @param parser  解析函数 (JsonObject, ResourceLocation) -> T
     */
    public static <T> void overlayIndex(
            String subPath,
            Map<ResourceLocation, T> dataMap,
            Map<ResourceLocation, String> cacheMap,
            BiFunction<JsonObject, ResourceLocation, T> parser
    ) {
        Path externalDir = PACK_ROOT.resolve("data/lrtactical").resolve(subPath);
        scanAndOverlayIndex(externalDir, dataMap, cacheMap, parser);
    }

    /**
     * 为 Display Manager 覆盖外部文件。
     * 外部文件在 tacz/lrtactical_official_resources/assets/lrtactical/{subPath}/*.json
     *
     * @param subPath 子路径，如 "display/melee"
     * @param dataMap 目标数据 Map
     * @param factory 工厂函数 (JsonObject, ResourceLocation) -> T
     */
    public static <T> void overlayDisplay(
            String subPath,
            Map<ResourceLocation, T> dataMap,
            BiFunction<com.google.gson.JsonObject, ResourceLocation, T> factory
    ) {
        Path externalDir = PACK_ROOT.resolve("assets/lrtactical").resolve(subPath);
        scanAndOverlayDisplay(externalDir, dataMap, factory);
    }

    /**
     * 扫描 index 外部目录并覆盖 dataMap 和 cache
     */
    private static <T> void scanAndOverlayIndex(
            Path directory,
            Map<ResourceLocation, T> dataMap,
            Map<ResourceLocation, String> cacheMap,
            BiFunction<JsonObject, ResourceLocation, T> parser
    ) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String id = fileName.substring(0, fileName.length() - 5); // remove ".json"
                ResourceLocation key = new ResourceLocation("lrtactical", id);

                try {
                    String rawJson = Files.readString(file, StandardCharsets.UTF_8);
                    JsonObject jsonObject = CommonAssetsManager.GSON.fromJson(rawJson, JsonObject.class);
                    T parsed = parser.apply(jsonObject, key);
                    if (parsed != null) {
                        dataMap.put(key, parsed);
                        cacheMap.put(key, rawJson); // HashMap.put() 允许覆写，不会抛异常
                    }
                } catch (JsonParseException | IllegalArgumentException e) {
                    GunMod.LOGGER.error("ExternalResourceOverlay", "Failed to parse external index file {}: {}", key, e.getMessage());
                }
            }
        } catch (IOException e) {
            EquipmentMod.LOGGER.warn("Failed to scan external index directory {}", directory, e);
        }
    }

    /**
     * 扫描 display 外部目录并覆盖 dataMap
     */
    private static <T> void scanAndOverlayDisplay(
            Path directory,
            Map<ResourceLocation, T> dataMap,
            BiFunction<JsonObject, ResourceLocation, T> factory
    ) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String id = fileName.substring(0, fileName.length() - 5); // remove ".json"
                ResourceLocation key = new ResourceLocation("lrtactical", id);

                try {
                    String rawJson = Files.readString(file, StandardCharsets.UTF_8);
                    JsonObject jsonObject = CommonAssetsManager.GSON.fromJson(rawJson, JsonObject.class);
                    T instance = factory.apply(jsonObject, key);
                    if (instance != null) {
                        dataMap.put(key, instance);
                    }
                } catch (JsonParseException | IllegalArgumentException e) {
                    GunMod.LOGGER.error("ExternalResourceOverlay", "Failed to parse external display file {}: {}", key, e.getMessage());
                }
            }
        } catch (IOException e) {
            EquipmentMod.LOGGER.warn("Failed to scan external display directory {}", directory, e);
        }
    }
}
