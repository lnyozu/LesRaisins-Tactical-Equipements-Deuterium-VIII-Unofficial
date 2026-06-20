package me.xjqsh.lrtactical.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.util.Set;

public final class ConfigReloadUtil {
    private ConfigReloadUtil() {
    }

    public static int reload(ModConfig.Type... types) {
        int reloaded = 0;
        for (ModConfig.Type type : types) {
            Set<ModConfig> configs = ConfigTracker.INSTANCE.configSets().get(type);
            if (configs == null) {
                continue;
            }
            synchronized (configs) {
                for (ModConfig config : configs) {
                    if (!EquipmentMod.MOD_ID.equals(config.getModId())
                            || !(config.getConfigData() instanceof CommentedFileConfig fileConfig)) {
                        continue;
                    }
                    fileConfig.load();
                    CommentedConfig data = fileConfig;
                    IConfigSpec<?> spec = config.getSpec();
                    if (!spec.isCorrect(data)) {
                        spec.correct(data);
                        fileConfig.save();
                    }
                    spec.afterReload();
                    reloaded++;
                }
            }
        }
        return reloaded;
    }
}
