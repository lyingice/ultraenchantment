package com.lyingice.ultraenchantment.client;

import com.lyingice.ultraenchantment.block.entity.AscensionTableBlockEntity;
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
 * 附魔进阶台的<b>悬浮书</b> —— 逐字照抄原版 {@code EnchantTableRenderer}。
 *
 * <h2>「原版同款」到什么程度</h2>
 *
 * <ul>
 *   <li>模型用原版的 {@link BookModel}（层 {@code ModelLayers.BOOK}）；</li>
 *   <li>贴图用<b>原版资源</b> `minecraft:entity/enchanting_table_book`
 *       （走方块图集，和原版同一个 {@link Material}）—— <b>不需要新增任何美术</b>；</li>
 *   <li>浮沉、翻页、朝向、开合的插值公式与偏移量（`translate(0.5, 0.75, 0.5)`、
 *       `0.1 + sin(t*0.1)*0.01`、`rotZ = 80°`…）全部照抄，
 *       所以手感与原版附魔台<b>一模一样</b>；</li>
 *   <li>连 {@code getRenderBoundingBox} 都照抄 —— 少了它，书在方块本身被视锥剔除时
 *       会跟着一起消失（书比方块高一截）。</li>
 * </ul>
 *
 * <p>唯一的差别：本模组的进阶台碰撞箱与原版附魔台<b>完全相同</b>（16×12×16），
 * 所以那个 `0.75` 的抬升量可以直接用，不用改。
 */
public class AscensionTableRenderer implements BlockEntityRenderer<AscensionTableBlockEntity> {

    public static final Material BOOK_LOCATION = new Material(
            TextureAtlas.LOCATION_BLOCKS,
            ResourceLocation.withDefaultNamespace("entity/enchanting_table_book"));

    private final BookModel bookModel;

    public AscensionTableRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(AscensionTableBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
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
    public AABB getRenderBoundingBox(AscensionTableBlockEntity be) {
        var pos = be.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
    }
}
