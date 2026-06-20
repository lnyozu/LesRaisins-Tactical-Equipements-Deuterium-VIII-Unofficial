package me.xjqsh.lrtactical.resource;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the optional official resource pack embedded by the local
 * {@code buildBundled} task. Public core builds do not contain this resource,
 * so they simply continue without installing anything.
 */
public final class DefaultPackExtractor {
    public static final String EMBEDDED_PACK_RESOURCE =
            "embedded_packs/lrtactical_official_resources.zip";
    public static final String EXTERNAL_PACK_DIRECTORY =
            "lrtactical_official_resources";

    private static final String MANAGED_STATE_FILE = ".lrtactical-managed.properties";
    private static final String STATE_PACK_HASH = "pack_sha256";
    private static final String STATE_CONTENT_HASH = "content_sha256";
    private static final long MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_ENTRY_COUNT = 10_000;

    private DefaultPackExtractor() {
    }

    public static void extractIfNeeded() {
        ClassLoader classLoader = DefaultPackExtractor.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(EMBEDDED_PACK_RESOURCE)) {
            if (input == null) {
                EquipmentMod.LOGGER.debug(
                        "No embedded official resource pack found; external TACZ packs remain available"
                );
                return;
            }

            byte[] embeddedPack = input.readAllBytes();
            String embeddedPackHash = sha256(embeddedPack);
            Path taczDirectory = FMLPaths.GAMEDIR.get().resolve("tacz");
            Path targetDirectory = taczDirectory.resolve(EXTERNAL_PACK_DIRECTORY);
            Files.createDirectories(taczDirectory);

            InstallDecision decision = inspectTarget(targetDirectory, embeddedPackHash);
            switch (decision) {
                case UP_TO_DATE -> {
                    return;
                }
                case USER_MANAGED -> {
                    EquipmentMod.LOGGER.info(
                            "Using user-managed external official resource pack directory at {}",
                            targetDirectory
                    );
                    return;
                }
                case MODIFIED -> {
                    EquipmentMod.LOGGER.warn(
                            "The previously extracted official resource pack directory was modified; " +
                                    "the bundled copy will not overwrite it: {}",
                            targetDirectory
                    );
                    return;
                }
                case INVALID_TARGET -> {
                    EquipmentMod.LOGGER.warn(
                            "Official resource pack target is not a directory, leaving it untouched: {}",
                            targetDirectory
                    );
                    return;
                }
                case INSTALL -> {
                    // Continue below.
                }
            }

            installDirectoryPack(targetDirectory, embeddedPack, embeddedPackHash);
            EquipmentMod.LOGGER.info(
                    "Installed bundled official resource pack directory to {}",
                    targetDirectory
            );
        } catch (Exception e) {
            // Resource pack installation is optional and must never prevent the
            // core mod from loading.
            EquipmentMod.LOGGER.error(
                    "Failed to install bundled official resource pack; continuing with external packs only",
                    e
            );
        }
    }

    private static InstallDecision inspectTarget(Path targetDirectory, String embeddedPackHash)
            throws IOException {
        if (!Files.exists(targetDirectory)) {
            return InstallDecision.INSTALL;
        }
        if (!Files.isDirectory(targetDirectory)) {
            return InstallDecision.INVALID_TARGET;
        }

        Path stateFile = targetDirectory.resolve(MANAGED_STATE_FILE);
        if (!Files.isRegularFile(stateFile)) {
            return InstallDecision.USER_MANAGED;
        }

        Properties state = readState(stateFile);
        String previousPackHash = state.getProperty(STATE_PACK_HASH, "");
        String previousContentHash = state.getProperty(STATE_CONTENT_HASH, "");
        String currentContentHash = hashDirectory(targetDirectory);

        if (!currentContentHash.equalsIgnoreCase(previousContentHash)) {
            return InstallDecision.MODIFIED;
        }

        return embeddedPackHash.equalsIgnoreCase(previousPackHash)
                ? InstallDecision.UP_TO_DATE
                : InstallDecision.INSTALL;
    }

    private static void installDirectoryPack(
            Path targetDirectory,
            byte[] embeddedPack,
            String embeddedPackHash
    ) throws IOException {
        Path parent = targetDirectory.getParent();
        Files.createDirectories(parent);
        Path stagingDirectory = Files.createTempDirectory(parent, "lrtactical-pack-");
        Path backupDirectory = parent.resolve(
                targetDirectory.getFileName() + ".backup-" + UUID.randomUUID()
        );
        boolean targetMovedToBackup = false;

        try {
            extractZip(embeddedPack, stagingDirectory);
            validatePackRoot(stagingDirectory);

            String contentHash = hashDirectory(stagingDirectory);
            writeState(
                    stagingDirectory.resolve(MANAGED_STATE_FILE),
                    embeddedPackHash,
                    contentHash
            );

            if (Files.exists(targetDirectory)) {
                moveDirectory(targetDirectory, backupDirectory);
                targetMovedToBackup = true;
            }

            moveDirectory(stagingDirectory, targetDirectory);
            if (targetMovedToBackup) {
                deleteRecursively(backupDirectory);
            }
        } catch (Exception e) {
            if (!Files.exists(targetDirectory) && targetMovedToBackup
                    && Files.exists(backupDirectory)) {
                try {
                    moveDirectory(backupDirectory, targetDirectory);
                } catch (IOException restoreError) {
                    e.addSuppressed(restoreError);
                }
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to install official resource pack directory", e);
        } finally {
            deleteRecursively(stagingDirectory);
            if (Files.exists(targetDirectory)) {
                deleteRecursively(backupDirectory);
            }
        }
    }

    private static void extractZip(byte[] archive, Path targetDirectory) throws IOException {
        int entryCount = 0;
        long extractedBytes = 0L;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRY_COUNT) {
                    throw new IOException("Embedded resource pack contains too many entries");
                }

                Path output = targetDirectory.resolve(entry.getName()).normalize();
                if (!output.startsWith(targetDirectory)) {
                    throw new IOException("Unsafe path in embedded resource pack: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }

                Files.createDirectories(output.getParent());
                try (var outputStream = Files.newOutputStream(output)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        extractedBytes += read;
                        if (extractedBytes > MAX_EXTRACTED_BYTES) {
                            throw new IOException("Embedded resource pack exceeds extraction limit");
                        }
                        outputStream.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static void validatePackRoot(Path directory) throws IOException {
        if (!Files.isRegularFile(directory.resolve("gunpack.meta.json"))) {
            throw new IOException("Embedded resource pack is missing gunpack.meta.json");
        }
        if (!Files.isDirectory(directory.resolve("assets"))) {
            throw new IOException("Embedded resource pack is missing assets directory");
        }
        if (!Files.isDirectory(directory.resolve("data"))) {
            throw new IOException("Embedded resource pack is missing data directory");
        }
    }

    private static Properties readState(Path stateFile) throws IOException {
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(Files.readString(stateFile))) {
            properties.load(reader);
        }
        return properties;
    }

    private static void writeState(Path stateFile, String packHash, String contentHash)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty(STATE_PACK_HASH, packHash);
        properties.setProperty(STATE_CONTENT_HASH, contentHash);
        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, "Managed by LesRaisins Tactical bundled build");
            Files.writeString(stateFile, writer.toString(), StandardCharsets.UTF_8);
        }
    }

    private static String hashDirectory(Path directory) throws IOException {
        MessageDigest digest = newDigest();
        List<Path> files;
        try (var stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(MANAGED_STATE_FILE))
                    .sorted(Comparator.comparing(path -> normalizedRelativePath(directory, path)))
                    .toList();
        }

        byte[] separator = new byte[]{0};
        byte[] buffer = new byte[8192];
        for (Path file : files) {
            digest.update(normalizedRelativePath(directory, file).getBytes(StandardCharsets.UTF_8));
            digest.update(separator);
            try (InputStream input = Files.newInputStream(file);
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                while (digestInput.read(buffer) != -1) {
                    // DigestInputStream updates the digest.
                }
            }
            digest.update(separator);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String normalizedRelativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String sha256(byte[] content) {
        MessageDigest digest = newDigest();
        return HexFormat.of().formatHex(digest.digest(content));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private enum InstallDecision {
        INSTALL,
        UP_TO_DATE,
        USER_MANAGED,
        MODIFIED,
        INVALID_TARGET
    }
}
