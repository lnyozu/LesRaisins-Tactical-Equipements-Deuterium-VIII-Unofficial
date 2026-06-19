package me.xjqsh.lrtactical.resource;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 首次启动时自动将 mod JAR 中的默认资源文件解压到 tacz/default_melee/ 目录。
 * 参考 TACZ 的 GunPackLoader 机制，让用户可以方便地修改配置文件。
 * <p>
 * 幂等操作：若目标目录已存在 gunpack.meta.json 则跳过。
 */
public final class DefaultPackExtractor {

    private DefaultPackExtractor() {
    }

    /**
     * 检查并执行资源解压。已存在则跳过。
     */
    public static void extractIfNeeded() {
        Path packDir = FMLPaths.GAMEDIR.get().resolve("tacz/default_melee");
        Path metaFile = packDir.resolve("gunpack.meta.json");

        if (Files.exists(metaFile)) {
            return;
        }

        try {
            Files.createDirectories(packDir);

            // 写入 pack 元数据
            Files.writeString(metaFile, "{\"namespace\": \"lrtactical\"}");

            EquipmentMod.LOGGER.info("Extracting default pack to {}", packDir);

            // 解压 assets 和 data 目录
            extractDirectory("assets/lrtactical", packDir.resolve("assets/lrtactical"));
            extractDirectory("data/lrtactical", packDir.resolve("data/lrtactical"));

            EquipmentMod.LOGGER.info("Default pack extracted successfully to {}", packDir);
        } catch (Exception e) {
            EquipmentMod.LOGGER.error("Failed to extract default pack to {}", packDir, e);
            // 清理失败的提取
            cleanup(packDir);
        }
    }

    /**
     * 从 classpath 中递归复制整个目录到目标路径。
     * 同时处理 JAR 内文件（生产环境）和文件系统（开发环境）。
     */
    private static void extractDirectory(String classpathBase, Path targetDir) throws Exception {
        ClassLoader cl = DefaultPackExtractor.class.getClassLoader();
        Enumeration<URL> resources = cl.getResources(classpathBase);
        if (!resources.hasMoreElements()) {
            EquipmentMod.LOGGER.warn("No resources found for {}", classpathBase);
            return;
        }

        URL baseUrl = resources.nextElement();
        URI uri = baseUrl.toURI();

        if ("jar".equals(uri.getScheme())) {
            // 生产环境：资源在 JAR 内部
            extractFromJar(classpathBase, targetDir, uri);
        } else {
            // 开发环境：资源在文件系统
            copyRecursive(Paths.get(uri), targetDir);
        }
    }

    /**
     * 从 JAR 内提取目录
     */
    private static void extractFromJar(String classpathBase, Path targetDir, URI jarResourceUri) throws IOException {
        // jar:file:/path/to/mod.jar!/assets/lrtactical
        String spec = jarResourceUri.getSchemeSpecificPart();
        int sep = spec.lastIndexOf('!');
        String jarUrl = spec.substring(0, sep);
        String entryPath = spec.substring(sep + 1);
        if (entryPath.startsWith("/")) {
            entryPath = entryPath.substring(1);
        }

        URI jarUri = URI.create(jarUrl);

        try {
            // 尝试使用已有的 FileSystem（Forge SecureJar）
            FileSystem fs;
            try {
                fs = FileSystems.getFileSystem(jarUri);
            } catch (FileSystemNotFoundException e) {
                fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap());
            }

            try {
                Path sourceRoot = fs.getPath("/", entryPath);
                if (Files.exists(sourceRoot)) {
                    copyRecursive(sourceRoot, targetDir);
                }
            } finally {
                // 不关闭 fs，因为可能是 Forge 的共享 SecureJar FileSystem
            }
        } catch (Exception e) {
            // JAR FileSystem 失败时，回退到逐个文件提取
            EquipmentMod.LOGGER.warn("JAR FileSystem extraction failed for {}, trying fallback", classpathBase, e);
            extractFromJarFallback(classpathBase, targetDir);
        }
    }

    /**
     * 回退方案：通过 ClassLoader.getResources 逐个文件提取
     */
    private static void extractFromJarFallback(String classpathBase, Path targetDir) throws IOException {
        ClassLoader cl = DefaultPackExtractor.class.getClassLoader();
        extractResourceRecursive(cl, classpathBase, targetDir);
    }

    private static void extractResourceRecursive(ClassLoader cl, String path, Path targetDir) throws IOException {
        Enumeration<URL> resources;
        try {
            resources = cl.getResources(path);
        } catch (IOException e) {
            return;
        }

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try {
                // 如果是目录，URL 以 / 结尾
                Path target = targetDir;
                String urlPath = url.getPath();
                String fileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);

                if (fileName.isEmpty() || urlPath.endsWith("/")) {
                    // 这是一个目录 — 跳过
                } else {
                    // 这是一个文件
                    Files.createDirectories(target);
                    try (InputStream is = url.openStream()) {
                        Files.copy(is, target.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (Exception ignored) {
                // 跳过无法访问的资源
            }
        }

        // 对于目录结构，递归扫描已知的子目录
        String[] knownSubDirs = {
                "display", "animations", "geo_models", "models", "sounds", "tacz_sounds",
                "textures", "scripts", "lang", "particles", "player_animator",
                "index", "recipes", "recipe_filters", "data"
        };

        for (String subDir : knownSubDirs) {
            String subPath = path + "/" + subDir;
            Path subTarget = targetDir.resolve(subDir);
            try {
                extractRecursiveInternal(cl, subPath, subTarget);
            } catch (Exception ignored) {
            }
        }
    }

    private static void extractRecursiveInternal(ClassLoader cl, String path, Path targetDir) throws IOException {
        Enumeration<URL> resources;
        try {
            resources = cl.getResources(path);
        } catch (IOException e) {
            return;
        }

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try {
                String urlPath = url.getPath();
                // JAR 内路径格式: jar:file:...jar!/path/to/resource
                if (url.getProtocol().equals("jar")) {
                    String innerPath = urlPath.substring(urlPath.lastIndexOf('!') + 1);
                    if (innerPath.startsWith("/")) innerPath = innerPath.substring(1);
                    Files.createDirectories(targetDir);

                    String fileName = innerPath.substring(innerPath.lastIndexOf('/') + 1);
                    if (!fileName.isEmpty() && !innerPath.endsWith("/")) {
                        try (InputStream is = url.openStream()) {
                            Files.copy(is, targetDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } else {
                    // 文件系统中的路径
                    Path source = Paths.get(url.toURI());
                    if (Files.isDirectory(source)) {
                        copyRecursive(source, targetDir);
                    } else {
                        Files.createDirectories(targetDir);
                        try (InputStream is = url.openStream()) {
                            Files.copy(is, targetDir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 递归复制目录或文件
     */
    private static void copyRecursive(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            try (Stream<Path> stream = Files.walk(source)) {
                stream.forEach(src -> {
                    Path dst = target.resolve(source.relativize(src).toString());
                    try {
                        if (Files.isDirectory(src)) {
                            Files.createDirectories(dst);
                        } else {
                            Files.createDirectories(dst.getParent());
                            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        EquipmentMod.LOGGER.warn("Failed to copy {} to {}", src, dst, e);
                    }
                });
            }
        } else {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 清理失败的提取
     */
    private static void cleanup(Path packDir) {
        try {
            if (Files.exists(packDir)) {
                try (Stream<Path> walk = Files.walk(packDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException ignored) {
        }
    }
}
