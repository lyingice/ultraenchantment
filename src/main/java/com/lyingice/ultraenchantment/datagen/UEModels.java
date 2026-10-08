package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.BookView;
import com.lyingice.ultraenchantment.content.UETier;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * 物品模型生成。
 *
 * <p>1.21.1 仍使用旧版物品模型体系（{@code models/item/*.json} + {@code overrides} + {@code predicate}），
 * 而非 1.21.4+ 的 {@code assets/<ns>/items/} ItemModel 体系。
 *
 * <h2>单例物品 + 多层覆盖</h2>
 *
 * <p>进阶附魔书是<b>一个</b>物品，但外观要区分「科目 × 阶级」。
 * 做法是 pieces 叠加：
 * <ul>
 *   <li>{@code layer0} = 阶级书底图（{@code <tier>_enchanted_book}，由 {@code custom_model_data} 切换）</li>
 *   <li>{@code layer1} = 科目覆盖层（{@code promotion} 进化 / {@code upgrade} 升级；铭刻型无覆盖层）</li>
 * </ul>
 *
 * <p>科目由组件决定、阶级由 {@code custom_model_data} 决定，因此模型组合数是
 * 「科目数 × 阶级数」——但<b>物品注册项始终是 1</b>。
 *
 * <h2>兜底行为</h2>
 *
 * <p>无 {@code custom_model_data} 时命中基础模型，显示<b>高阶</b>底图。
 * 这是刻意的兜底：任何未显式设置档位的进阶书看起来都属于最低档，
 * 而不是渲染成紫黑格子。
 *
 * <p>贴图约定：阶级书贴图为 {@code <tier>_enchanted_book}，祛咒石为 {@code <tier>_curative_stone}。
 */
public class UEModels extends ItemModelProvider {

    /** 模型谓词键。1.21.1 中它注册于 {@code ItemProperties} 的 generic properties。 */
    private static final ResourceLocation CUSTOM_MODEL_DATA =
            ResourceLocation.withDefaultNamespace("custom_model_data");

    private static final String BOOK_TEXTURE = "_enchanted_book";
    private static final String STONE_TEXTURE = "_curative_stone";

    /** 科目覆盖层贴图名（进化型 / 升级型）。 */
    private static final String OVERLAY_PROMOTION = "promotion";
    private static final String OVERLAY_UPGRADE = "upgrade";

    public UEModels(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Ultraenchantment.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // 祛咒石：三个物品，各用自己阶级的贴图（不再靠 custom_model_data 切档）
        stoneModel("curative_stone", UETier.ADVANCED);
        stoneModel("super_curative_stone", UETier.SUPER);
        stoneModel("ultra_curative_stone", UETier.ULTRA);

        // 进阶附魔书：单物品，三科目 × 三阶级
        //
        // 铭刻型是单层（layer0 = 阶级书底图），进化型与升级型是双层（多一个科目覆盖层）。
        // 每个科目各建一套 override 链，共用同一个物品模型文件。
        advancedBook();
    }

    /**
     * 进阶附魔书——一个物品模型，内含三条科目 × 三阶级的 override 链。
     *
     * <h2>predicate 的编码</h2>
     *
     * <p>{@code custom_model_data} 是单一整数谓词，无法直接表达「科目 × 阶级」二维。
     * 这里沿用既有编码：<b>只编码阶级</b>（1/2/3），科目靠 {@code layer1} 贴图区分
     * ——但同一物品只能有一条 override 链，因此实际做法是把科目<b>也</b>编进谓词：
     *
     * <pre>
     *   科目偏移 + 阶级：  inscription = 0, ascension = 10, upgrade = 20
     *   高阶 = 1, 超级 = 2, 究极 = 3
     *
     *   例：高阶进化书 = 11，究极升级书 = 23
     * </pre>
     *
     * <p>这样一条 override 链就能覆盖全部 9 种组合。
     * 无谓词命中时兜底为「高阶铭刻书」（谓词 0 的基础模型）。
     */
    private void advancedBook() {
        String itemName = "advanced_enchanted_book";

        // 科目偏移量与 UETier.modelData() 组合成完整谓词值。
        // 基础模型（无 override 命中）= 高阶铭刻书，即谓词 0 的形态。
        ItemModelBuilder base = singleTexture(
                itemName,
                mcLoc("item/generated"),
                "layer0",
                modLoc("item/" + UETier.ADVANCED.id() + BOOK_TEXTURE));
        // 铭刻型：只有阶级维（偏移 0）——高阶即基础模型本身，故从超级开始加 override
        for (UETier tier : UETier.values()) {
            if (tier == UETier.ADVANCED) {
                continue;
            }
            ItemModelBuilder variant = singleTexture(
                    variantName(itemName, "inscription", tier),
                    mcLoc("item/generated"),
                    "layer0",
                    modLoc("item/" + tier.id() + BOOK_TEXTURE));
            base.override()
                    .predicate(CUSTOM_MODEL_DATA, BookView.predicateOf("inscription", tier))
                    .model(variant)
                    .end();
        }

        // 进化型与升级型：双层（阶级底图 + 科目覆盖层）
        addDoubleLayerOverrides(base, itemName, "ascension", OVERLAY_PROMOTION);
        addDoubleLayerOverrides(base, itemName, "upgrade", OVERLAY_UPGRADE);
    }

    /** 为某个双层科目追加三档 override（高阶档因偏移非 0，也需要显式 override）。 */
    private void addDoubleLayerOverrides(ItemModelBuilder base, String itemName,
                                         String subject, String overlayTexture) {
        for (UETier tier : UETier.values()) {
            ItemModelBuilder variant = doubleLayer(
                    variantName(itemName, subject, tier), tier, BOOK_TEXTURE, overlayTexture);
            base.override()
                    .predicate(CUSTOM_MODEL_DATA, BookView.predicateOf(subject, tier))
                    .model(variant)
                    .end();
        }
    }

    /** 祛咒石：一个物品一个模型，贴图按物品自己的阶级取（{@code <tier>_curative_stone}）。 */
    private void stoneModel(String itemName, UETier tier) {
        singleTexture(itemName, mcLoc("item/generated"), "layer0",
                modLoc("item/" + tier.id() + STONE_TEXTURE));
    }

    private ItemModelBuilder doubleLayer(String modelName, UETier tier, String layer0Suffix, String overlayTexture) {
        return withExistingParent(modelName, mcLoc("item/generated"))
                .texture("layer0", modLoc("item/" + tier.id() + layer0Suffix))
                .texture("layer1", modLoc("item/" + overlayTexture));
    }

    private static String variantName(String itemName, UETier tier) {
        return itemName + "_" + tier.id();
    }

    private static String variantName(String itemName, String subject, UETier tier) {
        return itemName + "_" + subject + "_" + tier.id();
    }
}
