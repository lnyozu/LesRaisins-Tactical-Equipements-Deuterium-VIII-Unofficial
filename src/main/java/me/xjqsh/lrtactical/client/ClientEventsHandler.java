package me.xjqsh.lrtactical.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Either;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.item.GunTooltipPart;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.client.audio.AnimationSoundStopper;
import me.xjqsh.lrtactical.api.collision.OBB;
import me.xjqsh.lrtactical.api.item.ICustomItem;
import me.xjqsh.lrtactical.client.renderer.item.FlashShieldItemRenderer;
import me.xjqsh.lrtactical.client.renderer.item.MeleeItemRenderer;
import me.xjqsh.lrtactical.client.tooltip.TooltipSpacer;
import me.xjqsh.lrtactical.config.ClientConfig;
import me.xjqsh.lrtactical.init.ModEffects;
import me.xjqsh.lrtactical.util.TooltipHideFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = EquipmentMod.MOD_ID)
public class ClientEventsHandler {
    private static final Style LORE_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true);
    private static final int LORE_SPACING_HEIGHT = 4;

    public static double shakeTime = 0;
    public static double shakeRadius = 0;
    public static double shakeAmplitude = 0;
    public static Vec3 shakePos = Vec3.ZERO;
    public static double shakeType = 0;

    public static void handleShakeClient(double time, double radius, double amplitude, Vec3 position) {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return;

        float shakeStrength = ClientConfig.EXPLODE_SCREEN_SHAKE_MULTIPLIER.get().floatValue();
        if (shakeStrength <= 0) {
            return;
        }

        shakeTime = time;
        shakeRadius = radius;
        shakeAmplitude = amplitude * Mth.DEG_TO_RAD * shakeStrength;
        shakePos = position;
        shakeType = 2 * (Math.random() - 0.5);
    }

    @SubscribeEvent
    public static void computeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        // 来自sbw的神秘配方
        Entity entity = event.getCamera().getEntity();
        if (!(entity instanceof LivingEntity)) return;

        LocalPlayer player = Minecraft.getInstance().player;

        float yaw = event.getYaw();
        float pitch = event.getPitch();
        float roll = event.getRoll();

        shakeTime = Mth.lerp(0.05 * event.getPartialTick(), shakeTime, 0);

        if (player != null && shakeTime > 0) {
            float shakeRadiusAmplitude = (float) Mth.clamp(1 - player.position().distanceTo(shakePos) / shakeRadius, 0, 1);

            boolean onVehicle = player.getVehicle() != null;
            double f = shakeTime * Math.sin(0.5 * Math.PI * shakeTime) * shakeAmplitude * shakeRadiusAmplitude * (onVehicle ? 0.1 : 1);

            if (shakeType > 0) {
                event.setYaw((float) (yaw + f * shakeType));
                event.setPitch((float) (pitch - f * shakeType));
                event.setRoll((float) (roll - f));
            } else {
                event.setYaw((float) (yaw - (f * shakeType)));
                event.setPitch((float) (pitch + (f * shakeType)));
                event.setRoll((float) (roll + f));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND) {
            ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
            if (stack.getItem() instanceof ICustomItem item && item.blockOffhandRendering(stack)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void tickAnimation(TickEvent.ClientTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        var renderer = IClientItemExtensions.of(mainHandItem).getCustomRenderer();
        if (renderer instanceof MeleeItemRenderer renderer1) {
            var animationStateMachine = renderer1.getStateMachine(mainHandItem);
            if (animationStateMachine != null) {
                tickMove(player, animationStateMachine);
            }
        } else if (renderer instanceof FlashShieldItemRenderer renderer2) {
            var animationStateMachine = renderer2.getStateMachine(mainHandItem);
            if (animationStateMachine != null) {
                tickMove(player, animationStateMachine);
            }
        }

        // 修复检视音效：防止重复叠加 + 切换物品后停止
        AnimationSoundStopper.onClientTick(player);
    }

    private static boolean tickMove(LocalPlayer player, LuaAnimationStateMachine<?> animationStateMachine) {
        // 群组服切世界导致的特殊 BUG 处理，正常情况不会遇到此问题
        if (player.input == null) {
            animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
            return true;
        }

        if (!player.isMovingSlowly() && player.isSprinting()) {
            // 如果玩家正在移动，播放移动动画，否则播放 idle 动画
            animationStateMachine.trigger(GunAnimationConstant.INPUT_RUN);
        } else if (!player.isMovingSlowly() && player.input.getMoveVector().length() > 0.01) {
            animationStateMachine.trigger(GunAnimationConstant.INPUT_WALK);
        } else {
            animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
        }
        return false;
    }


    public static void afterLevel(float pPartialTicks, long pNanoTime, boolean pRenderLevel) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!pRenderLevel || mc.level == null || player == null) {
            return;
        }
        MobEffectInstance effect = player.getEffect(ModEffects.BLIND.get());
        if (effect == null) {
            return;
        }

        int tickRemain = effect.getDuration();

        int alpha = tickRemain > 100 ? 255 : (int) (tickRemain / 100f * 255f);
        int color = (ClientConfig.BLACK_FLASH.get() ? 0x000000 : 0xFFFFFF) + (alpha << 24);

        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();

        graphics.fill(0, 0, width, height, color);

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    @SubscribeEvent
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        // 当交互键按下时，允许交互
        if (InteractKey.INTERACT_KEY.isDown()) {
            return;
        }

        // 只要主手有枪，那么禁止交互
        ItemStack itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (itemInHand.getItem() instanceof ICustomItem customItem) {
            boolean flag = (event.isAttack() && customItem.shouldBlockAttack())
                    || (event.isUseItem() && customItem.shouldBlockUse())
                    || (event.isPickBlock() && customItem.shouldBlockUse());
            if (flag) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void renderAttackHitBox(RenderLevelStageEvent event) {
//        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
//            Vec3 cameraPos = event.getCamera().getPosition();
//            Player p = Minecraft.getInstance().player;
//            var center = p.getEyePosition(event.getPartialTick()).add(p.getLookAngle().scale(2.5)).toVector3f();
//
//            PoseStack poseStack = event.getPoseStack();
//            var buffersource = Minecraft.getInstance().renderBuffers().bufferSource();
//            RenderType renderType = RenderType.lines();
//            var vertexConsumer = buffersource.getBuffer(renderType);
//
//            // 创建 OBB
//            OBB obb = new OBB(center, new Vector3f(0.5f, 0.5f, 2f), event.getCamera().rotation());
//            renderOBB(cameraPos, obb, poseStack, vertexConsumer, 1f, 0f, 0f, 1f);
//        }
    }

    public static void renderOBB(PoseStack poseStack, VertexConsumer buffer, float centerX, float centerY, float centerZ, Quaternionf rotation, float halfX, float halfY, float halfZ, float red, float green, float blue, float alpha) {
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, centerZ);
        poseStack.mulPose(rotation);
        LevelRenderer.renderLineBox(poseStack, buffer, -halfX, -halfY, -halfZ, halfX, halfY, halfZ, red, green, blue, alpha);
        poseStack.popPose();
    }

    public static void renderOBB(Vec3 position, OBB obb, PoseStack poseStack, VertexConsumer buffer, float red, float green, float blue, float alpha) {
        Vector3f center = obb.center();
        Vector3f halfExtents = obb.extents();
        Quaternionf rotation = obb.rotation();
        renderOBB(
                poseStack, buffer,
                (float) (center.x() - position.x()), (float) (center.y() - position.y()), (float) (center.z() - position.z()),
                rotation,
                halfExtents.x(), halfExtents.y(), halfExtents.z(),
                red, green, blue, alpha
        );
    }

    /**
     * 原版会在 Item#appendHoverText 之后追加 NBT display.Lore。
     * 对本模组的自定义物品，将 Lore 整体移动到物品名称下方，便于资源包/NBT自由控制文案。
     */
    @SubscribeEvent
    public static void moveCustomItemLoreBelowName(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ICustomItem)) {
            return;
        }

        CompoundTag display = stack.getTagElement(ItemStack.TAG_DISPLAY);
        if (display == null || display.getTagType(ItemStack.TAG_LORE) != Tag.TAG_LIST) {
            return;
        }

        ListTag rawLore = display.getList(ItemStack.TAG_LORE, Tag.TAG_STRING);
        if (rawLore.isEmpty()) {
            return;
        }

        List<Component> loreLines = new ArrayList<>(rawLore.size());
        for (int i = 0; i < rawLore.size(); i++) {
            var parsed = Component.Serializer.fromJson(rawLore.getString(i));
            if (parsed != null) {
                loreLines.add(ComponentUtils.mergeStyles(parsed, LORE_STYLE));
            }
        }
        if (loreLines.isEmpty()) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        int loreStart = findSequenceFromEnd(tooltip, loreLines);
        if (loreStart < 0) {
            return;
        }

        if (!TooltipHideFlags.shouldShow(stack, GunTooltipPart.DESCRIPTION)) {
            tooltip.subList(loreStart, loreStart + loreLines.size()).clear();
            return;
        }

        if (loreStart == 1) {
            return;
        }

        List<Component> matchedLore = new ArrayList<>(
                tooltip.subList(loreStart, loreStart + loreLines.size())
        );
        tooltip.subList(loreStart, loreStart + loreLines.size()).clear();
        tooltip.addAll(1, matchedLore);
    }

    private static int findSequenceFromEnd(List<Component> tooltip, List<Component> sequence) {
        for (int start = tooltip.size() - sequence.size(); start >= 1; start--) {
            boolean matches = true;
            for (int offset = 0; offset < sequence.size(); offset++) {
                if (!tooltip.get(start + offset).equals(sequence.get(offset))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return start;
            }
        }
        return -1;
    }

    /**
     * 在名称下方的 NBT Lore 与后续属性之间加入小于一整行的透明间距。
     */
    @SubscribeEvent
    public static void addSpacingAfterCustomItemLore(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ICustomItem)) {
            return;
        }
        if (!TooltipHideFlags.shouldShow(stack, GunTooltipPart.DESCRIPTION)) {
            return;
        }

        CompoundTag display = stack.getTagElement(ItemStack.TAG_DISPLAY);
        if (display == null || display.getTagType(ItemStack.TAG_LORE) != Tag.TAG_LIST) {
            return;
        }

        ListTag lore = display.getList(ItemStack.TAG_LORE, Tag.TAG_STRING);
        if (lore.isEmpty()) {
            return;
        }

        int validLoreLines = 0;
        for (int i = 0; i < lore.size(); i++) {
            if (Component.Serializer.fromJson(lore.getString(i)) != null) {
                validLoreLines++;
            }
        }
        if (validLoreLines == 0) {
            return;
        }

        List<Either<net.minecraft.network.chat.FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent>>
                elements = event.getTooltipElements();
        int insertionIndex = Math.min(1 + validLoreLines, elements.size());
        if (insertionIndex < elements.size()) {
            elements.add(insertionIndex, Either.right(new TooltipSpacer(LORE_SPACING_HEIGHT)));
        }
    }

}
