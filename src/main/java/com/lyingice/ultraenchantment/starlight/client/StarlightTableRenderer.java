package com.lyingice.ultraenchantment.starlight.client;

import com.lyingice.ultraenchantment.starlight.block.StarlightTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * 星光台的<b>悬浮书</b>——逐字照抄原版 {@code EnchantTableRenderer}。
 *
 * <p>模型用原版 {@link BookModel}，贴图用原版
 * {@code minecraft:entity/enchanting_table_book}，不需要新增美术；
 * 浮沉、翻页、朝向、开合与原版附魔台一致。
 */
public class StarlightTableRenderer implements BlockEntityRenderer<StarlightTableBlockEntity> {

    public static final Material BOOK_LOCATION = new Material(
            TextureAtlas.LOCATION_BLOCKS,
            ResourceLocation.withDefaultNamespace("entity/enchanting_table_book"));

    private final BookModel bookModel;

    /** 每个方块位置只打一次日志，确认 renderer 真的被调度到。 */
    private static final java.util.Set<net.minecraft.core.BlockPos> LOGGED = new java.util.HashSet<>();

    public StarlightTableRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(StarlightTableBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (LOGGED.add(be.getBlockPos().immutable())) {
            com.lyingice.ultraenchantment.Ultraenchantment.LOGGER.info(
                    "[starlight] rendering floating book at {}", be.getBlockPos());
        }
        pose.pushPose();
        pose.translate(0.5F, 0.75F, 0.5F);
        float time = (float) be.time + partialTick;
        pose.translate(0.0F, 0.1F + Mth.sin(time * 0.1F) * 0.01F, 0.0F);
        float rotDelta = be.rot - be.oRot;
        while (rotDelta >= (float) Math.PI) {
            rotDelta -= (float) (Math.PI * 2);
        }
        while (rotDelta < (float) -Math.PI) {
            rotDelta += (float) (Math.PI * 2);
        }
        float rot = be.oRot + rotDelta * partialTick;
        pose.mulPose(Axis.YP.rotation(-rot));
        pose.mulPose(Axis.ZP.rotationDegrees(80.0F));
        float flip = Mth.lerp(partialTick, be.oFlip, be.flip);
        float rightPage = Mth.clamp(Mth.frac(flip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float leftPage = Mth.clamp(Mth.frac(flip + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float open = Mth.lerp(partialTick, be.oOpen, be.open);
        this.bookModel.setupAnim(time, rightPage, leftPage, open);
        VertexConsumer consumer = BOOK_LOCATION.buffer(buffer, RenderType::entitySolid);
        this.bookModel.render(pose, consumer, light, overlay, -1);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(StarlightTableBlockEntity be) {
        var pos = be.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
    }
}
