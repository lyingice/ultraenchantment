package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.AscensionTier;
import com.lyingice.ultraenchantment.content.LineageTier;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * 语言文件生成。
 *
 * <p><b>命名约定</b>：三种书的物品名一律为「进阶附魔书」/ "Advanced Enchanted Book"，
 * <b>不含阶级</b>。这与原版附魔书、锻造模板（Smithing Template）的做法一致——
 * 同名物品靠 <b>材质与提示框</b> 区分，而不是靠物品名本身。
 *
 * <p>本类只生成**静态键**：物品名、阶级词缀。
 * 阶段条目名（{@code enchantment.ultraenchantment.super.sharpness}）需要按数据包内容动态生成，
 * 因此不由本类负责——见 {@link UEStageNames}。
 */
public class UELang extends LanguageProvider {
    private final boolean chinese;

    public UELang(PackOutput output, String locale, boolean chinese) {
        super(output, Ultraenchantment.MODID, locale);
        this.chinese = chinese;
    }

    @Override
    protected void addTranslations() {
        // ── 物品名（单例，不含科目也不含阶级）──
        //
        // 进阶附魔书是**一个**物品，三大科目共用同一个本地化键。
        // 这与原版 enchanted_book / smithing_template 的做法一致。
        add(itemKey("advanced_enchanted_book"), this.chinese ? "进阶附魔书" : "Advanced Enchanted Book");
        add(itemKey("curative_stone"), this.chinese ? "祛咒石" : "Curative Stone");

        // ── 阶级词缀（tooltip 中附在附魔名之前）──
        for (AscensionTier tier : AscensionTier.values()) {
            add(tierKey(tier), tierName(tier));
        }

        // ── 进阶附魔书 tooltip（四套格式的标题与标签）──
        addTooltips();

        // ── 进阶附魔的独立阶梯名 ──
        // 与静态键同处一个提供器：DataGenerator 按「provider 名」去重，
        // 而 LanguageProvider 的名字就是 "Languages: <locale> for mod: <modid>"，
        // 同 locale 开两个提供器会直接抛 Duplicate provider（见 AGENT.md P1-19）。
        addStageNames();
    }

    /**
     * 进阶附魔的阶梯本地化名——<b>各自独立的键，不是拼串</b>。
     *
     * <h3>为什么独立</h3>
     *
     * <p>早先是「阶级词缀 + 原版附魔名」在 tooltip 里拼出来，那样有两个死角：
     * <ul>
     *   <li>整合包无法重命名——数据包作者想让超级锋利叫「裂空」，根本没这个位置</li>
     *   <li>语序被焊死——中文「超级锋利」与英文 "Super Sharpness" 恰好同序，
     *       换成别的语言就未必；拼串把语序固定在了代码里</li>
     * </ul>
     *
     * <p>独立键把命名权交还语言文件：整合包只需覆盖一个键就能改名。
     *
     * <h3>键的构造必须与运行期一致</h3>
     *
     * <p>{@code EnchantmentFactory.descriptionOf} 把阶段条目 id
     * {@code ultraenchantment:super/sharpness} 的斜杠换成点，得到
     * {@code enchantment.ultraenchantment.super.sharpness}。
     * {@link LineageTable.Lineage#langKey} 必须生成同一个键——
     * 这正是把谱系清单抽成单一表的原因。
     */
    private void addStageNames() {
        for (LineageTable.Lineage lineage : LineageTable.all()) {
            // 只给**本条谱系真正拥有的阶级**生成名字：没有超级阶的谱系不该留下悬空语言键。
            for (LineageTier tier : lineage.tiers()) {
                add(lineage.langKey(tier), stageName(lineage, tier));
            }
        }
    }

    /** 阶梯显示名：中文「高阶锋利」，英文 "Advanced Sharpness"。 */
    private String stageName(LineageTable.Lineage lineage, LineageTier tier) {
        if (this.chinese) {
            return lineageTierCn(tier) + lineage.cnName();
        }
        return lineageTierEn(tier) + " " + lineage.enName();
    }

    private static String lineageTierCn(LineageTier tier) {
        return switch (tier) {
            case NATIVE -> "原生";
            case ADVANCED -> "高阶";
            case SUPER -> "超级";
            case ULTRA -> "究极";
        };
    }

    private static String lineageTierEn(LineageTier tier) {
        return switch (tier) {
            case NATIVE -> "Native";
            case ADVANCED -> "Advanced";
            case SUPER -> "Super";
            case ULTRA -> "Ultra";
        };
    }

    /**
     * 进阶附魔书的四套 tooltip 文本。
     *
     * <p>全部使用 {@code %s} 占位符——阶级名与附魔名在运行时注入，
     * 让翻译者可以调整语序（中文「高阶升级 III」与英文 "Advanced Upgrade III" 语序不同）。
     */
    private void addTooltips() {
        if (this.chinese) {
            // ① 通用进阶
            add(tipKey("ascension.generic"), "附魔进阶");
            add(tipKey("applies_to"), "可应用于附魔：");
            add(tipKey("advances_to"), "进阶为：");

            // ② 定向进阶
            add(tipKey("ascension.targeted"), "定向进阶");

            // ③ 铭刻型
            add(tipKey("inscription.header"), "%s附魔");

            // ④ 升级型
            add(tipKey("upgrade.header"), "%s升级 %s");
            add(tipKey("tier_scope"), "%s附魔");

            // 阶级范围（h3 / h5）
            add(tipKey("scope.native"), "普通附魔");
            add(tipKey("scope.advanced"), "高阶附魔");
            add(tipKey("scope.super"), "超级附魔");
            add(tipKey("scope.ultra"), "究极附魔");

            // ── JEI：类别标题、三类操作、进阶要求与合并规则（见 compat/jei）──
            add(jeiKey("category.anvil"), "附魔铁砧");
            add(jeiKey("action.ascend"), "附魔进阶");
            add(jeiKey("action.merge"), "附魔合并");
            add(jeiKey("action.upgrade"), "附魔升级");
            add(jeiKey("require.max"), "进阶要求：%s 达到满级（%s 级）");
            add(jeiKey("require.level"), "进阶要求：%s 达到 %s 级（该阶级上限 %s 级）");
            add(jeiKey("note.ascend"), "进阶后曲线等级归 1，存储等级不变");
            add(jeiKey("note.upgrade"), "升级书把该阶级的曲线等级提到目标值；已达目标则不产出");
            add(jeiKey("note.merge.carrier"), "两本同阶级载体书：条目合并，等级相同则 +1（逐谱系夹取上限）");
            add(jeiKey("note.merge.advance"), "载体书 + 进阶书：整本推进一阶，每个条目都要够门槛");
            add(jeiKey("note.merge.upgrade"), "载体书 + 升级书：把条目提到该阶级上限");
            add(jeiKey("note.merge.vanilla"), "原版附魔书 + 进阶书：转印成本模组的载体书（条目从 1 级起）");
            add(jeiKey("note.merge.upgradebook"), "两本同阶级升级书：等级相同则 +1，否则取高");
        } else {
            add(tipKey("ascension.generic"), "Enchantment Ascension");
            add(tipKey("applies_to"), "Applicable to:");
            add(tipKey("advances_to"), "Advances to:");

            add(tipKey("ascension.targeted"), "Targeted Ascension");

            add(tipKey("inscription.header"), "%s Enchantment");

            add(tipKey("upgrade.header"), "%s Upgrade %s");
            add(tipKey("tier_scope"), "%s Enchantment");

            add(tipKey("scope.native"), "Normal Enchantments");
            add(tipKey("scope.advanced"), "Advanced Enchantments");
            add(tipKey("scope.super"), "Super Enchantments");
            add(tipKey("scope.ultra"), "Ultra Enchantments");

            add(jeiKey("category.anvil"), "Ultra Enchantment Anvil");
            add(jeiKey("action.ascend"), "Enchantment Ascension");
            add(jeiKey("action.merge"), "Enchantment Merging");
            add(jeiKey("action.upgrade"), "Enchantment Upgrade");
            add(jeiKey("require.max"), "Requires: %s at max level (%s)");
            add(jeiKey("require.level"), "Requires: %s at level %s (tier cap %s)");
            add(jeiKey("note.ascend"), "Ascending resets the curve level to 1; the stored level is unchanged");
            add(jeiKey("note.upgrade"), "Raises the curve level to the book's target; no output if already there");
            add(jeiKey("note.merge.carrier"), "Two carrier books of the same tier: entries merge, equal levels get +1");
            add(jeiKey("note.merge.advance"), "Carrier book + ascension book: advances the whole book one tier");
            add(jeiKey("note.merge.upgrade"), "Carrier book + upgrade book: raises entries to the tier cap");
            add(jeiKey("note.merge.vanilla"), "Vanilla enchanted book + ascension book: transcribes into a carrier book");
            add(jeiKey("note.merge.upgradebook"), "Two upgrade books of the same tier: equal levels get +1, otherwise the higher");
        }
    }

    private static String tipKey(String path) {
        return "tooltip." + Ultraenchantment.MODID + "." + path;
    }

    /** JEI 用的键：{@code jei.<modid>.<path>}。 */
    private static String jeiKey(String path) {
        return "jei." + Ultraenchantment.MODID + "." + path;
    }

    private String tierName(AscensionTier tier) {
        if (this.chinese) {
            return switch (tier) {
                case ADVANCED -> "高阶";
                case SUPER -> "超级";
                case ULTRA -> "究极";
            };
        }
        return switch (tier) {
            case ADVANCED -> "Advanced";
            case SUPER -> "Super";
            case ULTRA -> "Ultra";
        };
    }

    static String tierKey(AscensionTier tier) {
        return "tier." + Ultraenchantment.MODID + "." + tier.id();
    }

    private static String itemKey(String name) {
        return "item." + Ultraenchantment.MODID + "." + name;
    }
}
