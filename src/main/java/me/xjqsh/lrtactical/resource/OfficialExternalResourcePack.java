package me.xjqsh.lrtactical.resource;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.resource.PathPackResources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

/**
 * Exposes external official TACZ packs as vanilla client resource packs.
 * <p>
 * TACZ can read the external gun pack on its own, but Minecraft's particle
 * atlas is built from the vanilla resource manager. Registering this finder
 * lets client-only assets such as {@code assets/lrtactical/particles} and
 * {@code textures/particle} override the core fallback resources without
 * embedding official assets in the public core jar.
 * <p>
 * TACZ accepts arbitrary pack folder names under {@code tacz/}, so this finder
 * scans for packs that actually contain the official smoke particle resources
 * instead of relying only on the bundled default directory name.
 */
@Mod.EventBusSubscriber(
        modid = EquipmentMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class OfficialExternalResourcePack {
    private static final String PACK_ID_PREFIX =
            "000_" + EquipmentMod.MOD_ID + "_official_external_resources";
    private static final Component TITLE =
            Component.literal("LesRaisins Tactical Official External Resources");
    private static final Component DESCRIPTION =
            Component.literal("External official resources for LesRaisins Tactical");
    private static final int PACK_FORMAT_1_20_1 = 15;
    private static final String SMOKE_PARTICLE_JSON =
            "assets/lrtactical/particles/smoke_cloud.json";
    private static final String FIRST_SMOKE_TEXTURE =
            "assets/lrtactical/textures/particle/smoke_1.png";

    private OfficialExternalResourcePack() {
    }

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        event.addRepositorySource(packConsumer -> {
            Path taczDirectory = FMLPaths.GAMEDIR.get().resolve("tacz");
            List<PackCandidate> candidates = findOfficialSmokeResourcePacks(taczDirectory);
            if (candidates.isEmpty()) {
                return;
            }

            Pack.Info info = new Pack.Info(
                    DESCRIPTION,
                    PACK_FORMAT_1_20_1,
                    PACK_FORMAT_1_20_1,
                    FeatureFlagSet.of(),
                    true
            );
            for (PackCandidate candidate : candidates) {
                Pack pack = Pack.create(
                        candidate.id(),
                        TITLE,
                        true,
                        candidate::open,
                        info,
                        PackType.CLIENT_RESOURCES,
                        Pack.Position.TOP,
                        true,
                        PackSource.BUILT_IN
                );
                packConsumer.accept(pack);
                EquipmentMod.LOGGER.info(
                        "Registered official external client resources from {}",
                        candidate.path()
                );
            }
        });
    }

    private static List<PackCandidate> findOfficialSmokeResourcePacks(Path taczDirectory) {
        List<Path> paths = new ArrayList<>();
        addIfExists(paths, taczDirectory.resolve(DefaultPackExtractor.EXTERNAL_PACK_DIRECTORY));
        addIfExists(paths, taczDirectory.resolve(DefaultPackExtractor.EXTERNAL_PACK_DIRECTORY + ".zip"));

        if (Files.isDirectory(taczDirectory)) {
            try (Stream<Path> children = Files.list(taczDirectory)) {
                children.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .filter(path -> !paths.contains(path))
                        .forEach(paths::add);
            } catch (IOException e) {
                EquipmentMod.LOGGER.warn(
                        "Failed to scan TACZ external pack directory for official resources: {}",
                        taczDirectory,
                        e
                );
            }
        }

        List<PackCandidate> candidates = new ArrayList<>();
        for (Path path : paths) {
            if (Files.isDirectory(path) && isOfficialSmokeDirectoryPack(path)) {
                candidates.add(PackCandidate.directory(buildPackId(path, candidates.size()), path));
            } else if (Files.isRegularFile(path) && isZip(path) && isOfficialSmokeZipPack(path)) {
                candidates.add(PackCandidate.zip(buildPackId(path, candidates.size()), path));
            }
        }
        return candidates;
    }

    private static void addIfExists(List<Path> paths, Path path) {
        if (Files.exists(path) && !paths.contains(path)) {
            paths.add(path);
        }
    }

    private static boolean isOfficialSmokeDirectoryPack(Path root) {
        return Files.isRegularFile(root.resolve(SMOKE_PARTICLE_JSON))
                && Files.isRegularFile(root.resolve(FIRST_SMOKE_TEXTURE));
    }

    private static boolean isOfficialSmokeZipPack(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            return zip.getEntry(SMOKE_PARTICLE_JSON) != null
                    && zip.getEntry(FIRST_SMOKE_TEXTURE) != null;
        } catch (IOException e) {
            EquipmentMod.LOGGER.warn(
                    "Failed to inspect TACZ external zip pack for official resources: {}",
                    zipPath,
                    e
            );
            return false;
        }
    }

    private static boolean isZip(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static String buildPackId(Path path, int index) {
        return PACK_ID_PREFIX + "_" + index + "_" + sanitizePackId(path.getFileName().toString());
    }

    private static String sanitizePackId(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private record PackCandidate(String id, Path path, boolean zip) {
        private static PackCandidate directory(String id, Path path) {
            return new PackCandidate(id, path, false);
        }

        private static PackCandidate zip(String id, Path path) {
            return new PackCandidate(id, path, true);
        }

        private PackResources open(String id) {
            if (zip) {
                return new FilePackResources(id, path.toFile(), true);
            }
            return new PathPackResources(id, true, path);
        }
    }
}
