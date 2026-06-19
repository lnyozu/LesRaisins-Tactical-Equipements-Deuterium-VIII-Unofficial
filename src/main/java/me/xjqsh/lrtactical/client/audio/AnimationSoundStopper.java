package me.xjqsh.lrtactical.client.audio;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.AnimationSoundChannelContent;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationSoundChannel;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.sound.GunSoundInstance;
import me.xjqsh.lrtactical.client.renderer.item.MeleeItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;

/**
 * 修复检视音效的两个问题：
 * 1. 重复按检视键时音效叠加重复播放
 * 2. 切换到其他物品/空手后检视音效不停止
 * <p>
 * 原理：追踪动画状态机产生的 GunSoundInstance，
 * 在相同 ResourceLocation 的新音效触发时停止旧音效；
 * 在手动物品变化时停止所有关联音效。
 *
 * @author Codex (originally in fixed JAR, decompiled and restored)
 */
@OnlyIn(Dist.CLIENT)
public final class AnimationSoundStopper {

    private static final Field ANIMATION_PROTOTYPES;
    private static final Field SOUND_ENGINE;
    private static final Field PLAYING_SOUNDS;
    private static final Field BOUND_ENTITY;

    @Nullable
    private static ItemStack previousStack;
    @Nullable
    private static LuaAnimationStateMachine<?> previousStateMachine;
    @Nullable
    private static LocalPlayer previousPlayer;

    /** 当前正在观察的音效集合 */
    private static final Set<GunSoundInstance> observedSounds;
    /** 每个 ResourceLocation 对应的最新音效实例 */
    private static final Map<ResourceLocation, GunSoundInstance> latestSounds;

    static {
        ANIMATION_PROTOTYPES = findDeclaredField(AnimationController.class, "prototypes");
        SOUND_ENGINE = findSrgField(SoundManager.class, "f_120349_");
        PLAYING_SOUNDS = findSrgField(SoundEngine.class, "f_120226_");
        BOUND_ENTITY = findSrgField(EntityBoundSoundInstance.class, "f_119675_");
        observedSounds = Collections.newSetFromMap(new IdentityHashMap<>());
        latestSounds = new HashMap<>();
    }

    private AnimationSoundStopper() {
    }

    /**
     * 每客户端 tick 调用。检测玩家手持物品变化以停止旧音效，
     * 并防止同一动画音效的重复播放。
     */
    public static void onClientTick(@Nullable LocalPlayer player) {
        if (player == null) {
            stopPrevious();
            clearPrevious();
            return;
        }

        ItemStack currentStack = player.getMainHandItem();
        LuaAnimationStateMachine<?> currentStateMachine = getMeleeStateMachine(currentStack);

        // 检测物品或状态机是否变化
        if (previousStateMachine != null &&
                (currentStack != previousStack || currentStateMachine != previousStateMachine)) {
            stopAnimationSounds(previousStateMachine, previousPlayer);
            clearObservedSounds();
        }

        // 处理音效重叠
        if (currentStateMachine != null) {
            stopSupersededSounds(currentStateMachine, player);
        } else {
            clearObservedSounds();
        }

        previousStack = currentStack;
        previousStateMachine = currentStateMachine;
        previousPlayer = player;
    }

    @Nullable
    private static LuaAnimationStateMachine<?> getMeleeStateMachine(ItemStack stack) {
        var renderer = IClientItemExtensions.of(stack).getCustomRenderer();
        if (renderer instanceof MeleeItemRenderer meleeRenderer) {
            return meleeRenderer.getStateMachine(stack);
        }
        return null;
    }

    /**
     * 停止所有之前的动画音效（切物品时调用）
     */
    private static void stopPrevious() {
        if (previousStateMachine != null) {
            stopAnimationSounds(previousStateMachine, previousPlayer);
        }
    }

    /**
     * 清除之前的状态追踪
     */
    private static void clearPrevious() {
        previousStack = null;
        previousStateMachine = null;
        previousPlayer = null;
        clearObservedSounds();
    }

    /**
     * 防止音效叠加：当同一个 ResourceLocation 的新音效出现时，停止旧音效。
     */
    @SuppressWarnings("unchecked")
    private static void stopSupersededSounds(LuaAnimationStateMachine<?> stateMachine, LocalPlayer player) {
        if (stateMachine == null || player == null) return;
        if (SOUND_ENGINE == null || PLAYING_SOUNDS == null || BOUND_ENTITY == null) return;

        // 收集当前状态机所有动画的音效 ResourceLocation
        Set<ResourceLocation> animationSoundIds = collectAnimationSoundIds(stateMachine);
        if (animationSoundIds.isEmpty()) {
            clearObservedSounds();
            return;
        }

        Map<Object, Object> playingSounds;
        try {
            playingSounds = getPlayingSounds();
        } catch (IllegalAccessException | RuntimeException e) {
            return;
        }
        if (playingSounds == null) return;

        // 收集当前与此玩家相关的 GunSoundInstance
        Set<GunSoundInstance> soundSet = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Object soundObj : new ArrayList<>(playingSounds.keySet())) {
            if (!(soundObj instanceof GunSoundInstance gunSound)) continue;

            if (!animationSoundIds.contains(gunSound.getRegistryName())) continue;

            try {
                if (BOUND_ENTITY.get(gunSound) != player) continue;
            } catch (IllegalAccessException e) {
                continue;
            }

            soundSet.add(gunSound);
        }

        // 清理 latestSounds 中不再播放的音效
        latestSounds.entrySet().removeIf(entry -> !soundSet.contains(entry.getValue()));

        // 处理每个正在播放的音效
        for (GunSoundInstance sound : soundSet) {
            if (observedSounds.contains(sound)) continue;

            ResourceLocation registryName = sound.getRegistryName();
            GunSoundInstance previous = latestSounds.put(registryName, sound);

            // 同一个 ResourceLocation 有更旧的音效 → 停止它
            if (previous != null && previous != sound && soundSet.contains(previous)) {
                previous.setStop();
            }
        }

        // 更新观察集合
        observedSounds.retainAll(soundSet);
        observedSounds.addAll(soundSet);
    }

    /**
     * 停止指定状态机产生的所有正在播放的动画音效（切物品时调用）
     */
    @SuppressWarnings("unchecked")
    private static void stopAnimationSounds(LuaAnimationStateMachine<?> stateMachine, @Nullable LocalPlayer player) {
        if (stateMachine == null || player == null) return;
        if (ANIMATION_PROTOTYPES == null || SOUND_ENGINE == null ||
                PLAYING_SOUNDS == null || BOUND_ENTITY == null) return;

        Set<ResourceLocation> animationSoundIds = collectAnimationSoundIds(stateMachine);
        if (animationSoundIds.isEmpty()) return;

        Map<Object, Object> playingSounds;
        try {
            playingSounds = getPlayingSounds();
        } catch (IllegalAccessException | RuntimeException e) {
            return;
        }
        if (playingSounds == null || playingSounds.isEmpty()) return;

        for (Object soundObj : new ArrayList<>(playingSounds.keySet())) {
            if (!(soundObj instanceof GunSoundInstance gunSound)) continue;

            if (!animationSoundIds.contains(gunSound.getRegistryName())) continue;

            try {
                if (BOUND_ENTITY.get(gunSound) != player) continue;
            } catch (IllegalAccessException e) {
                continue;
            }

            gunSound.setStop();
        }
    }

    /**
     * 从状态机的 AnimationController 收集所有动画关键帧音效的 ResourceLocation
     */
    @SuppressWarnings("unchecked")
    private static Set<ResourceLocation> collectAnimationSoundIds(LuaAnimationStateMachine<?> stateMachine) {
        Set<ResourceLocation> ids = new HashSet<>();

        Map<String, ObjectAnimation> prototypes;
        try {
            if (ANIMATION_PROTOTYPES == null) return ids;
            prototypes = (Map<String, ObjectAnimation>) ANIMATION_PROTOTYPES.get(
                    stateMachine.getAnimationController()
            );
        } catch (IllegalAccessException | RuntimeException e) {
            return ids;
        }

        if (prototypes == null) return ids;

        for (ObjectAnimation anim : prototypes.values()) {
            ObjectAnimationSoundChannel soundChannel = anim.getSoundChannel();
            AnimationSoundChannelContent content = soundChannel != null ? soundChannel.content : null;
            if (content == null) continue;

            ResourceLocation[] soundNames = content.keyframeSoundName;
            if (soundNames == null) continue;

            for (ResourceLocation soundName : soundNames) {
                if (soundName != null) {
                    ids.add(soundName);
                }
            }
        }

        return ids;
    }

    /**
     * 通过反射获取 SoundEngine 中正在播放的音效 Map
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<Object, Object> getPlayingSounds() throws IllegalAccessException {
        Minecraft mc = Minecraft.getInstance();
        SoundEngine engine = (SoundEngine) SOUND_ENGINE.get(mc.getSoundManager());
        if (engine == null) return null;
        return (Map<Object, Object>) PLAYING_SOUNDS.get(engine);
    }

    /**
     * 清除所有音效追踪状态
     */
    private static void clearObservedSounds() {
        observedSounds.clear();
        latestSounds.clear();
    }

    /**
     * 查找声明字段（用于非混淆名称，如 AnimationController.prototypes）
     */
    @Nullable
    private static Field findDeclaredField(Class<?> clazz, String name) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 通过 SRG 名称查找字段（用于 Minecraft/Forge 混淆类）
     */
    @Nullable
    private static Field findSrgField(Class<?> clazz, String srgName) {
        try {
            return ObfuscationReflectionHelper.findField(clazz, srgName);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
