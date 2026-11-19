package com.lyingice.ultraenchantment.datagen;

import com.lyingice.ultraenchantment.Ultraenchantment;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Lightstar 的语言键。
 *
 * <p>只保留当前分支真正用到的键：附魔书、星光台、星图 GUI、四档阶级、
 * 星座 / 分支 / 阶段附魔名。旧书体系的长篇 tooltip 键已随旧系统删除。
 */
public class UELang extends LanguageProvider {
    private final boolean chinese;

    public UELang(PackOutput output, String locale, boolean chinese) {
        super(output, Ultraenchantment.MODID, locale);
        this.chinese = chinese;
    }

    @Override
    protected void addTranslations() {
        // ── 方块 ──
        add("block." + Ultraenchantment.MODID + ".starlight_table",
                this.chinese ? "星光附魔台" : "Starlight Enchanting Table");
        add("container." + Ultraenchantment.MODID + ".starlight_table",
                this.chinese ? "星光附魔台" : "Starlight Enchanting Table");

        // ── 星图界面 ──
        add("gui." + Ultraenchantment.MODID + ".starlight.title",
                this.chinese ? "星光附魔台 · 星图" : "Starlight Table · Star Chart");
        add("gui." + Ultraenchantment.MODID + ".starlight.empty",
                this.chinese ? "放入一件带附魔的物品" : "Insert an enchanted item");
        add("gui." + Ultraenchantment.MODID + ".starlight.select",
                this.chinese ? "点击一颗星查看详情" : "Click a star for details");
        add("gui." + Ultraenchantment.MODID + ".starlight.offering",
                this.chinese ? "祭品" : "Offering");
        add("gui." + Ultraenchantment.MODID + ".starlight.material",
                this.chinese ? "材料" : "Material");
        add("gui." + Ultraenchantment.MODID + ".starlight.material_cost",
                this.chinese ? "%s × %s" : "%s × %s");
        add("gui." + Ultraenchantment.MODID + ".starlight.not_enough_material",
                this.chinese ? "材料不足" : "Not enough materials");
        add("gui." + Ultraenchantment.MODID + ".starlight.current",
                this.chinese ? "当前节点" : "Current node");
        add("gui." + Ultraenchantment.MODID + ".starlight.upgrade",
                this.chinese ? "提升等级" : "Upgrade");
        add("gui." + Ultraenchantment.MODID + ".starlight.upgrade_cost",
                this.chinese ? "消耗 %s 级经验" : "Costs %s XP levels");
        add("gui." + Ultraenchantment.MODID + ".starlight.light",
                this.chinese ? "点亮此星" : "Light this star");
        add("gui." + Ultraenchantment.MODID + ".starlight.requires",
                this.chinese ? "需要前置等级 %s" : "Requires level %s");
        add("gui." + Ultraenchantment.MODID + ".starlight.cost",
                this.chinese ? "消耗 %s 级经验" : "Costs %s XP levels");
        add("gui." + Ultraenchantment.MODID + ".starlight.cost_discount",
                this.chinese ? "经验 %s 级 (-%s%%)" : "XP %s (-%s%%)");
        add("gui." + Ultraenchantment.MODID + ".starlight.shelves",
                this.chinese ? "书架 %s/%s" : "Shelves %s/%s");
        add("gui." + Ultraenchantment.MODID + ".starlight.shelf_discount",
                this.chinese ? "经验 -%s%%" : "XP -%s%%");
        add("gui." + Ultraenchantment.MODID + ".starlight.shelf_hint",
                this.chinese ? "摆书架降经验" : "Shelves cut XP");
        add("gui." + Ultraenchantment.MODID + ".starlight.chance",
                this.chinese ? "成功率 %s%%" : "Success %s%%");
        add("gui." + Ultraenchantment.MODID + ".starlight.unreachable",
                this.chinese ? "不可达：先点亮前置星" : "Unreachable: light the predecessor first");
        add("gui." + Ultraenchantment.MODID + ".starlight.max_level",
                this.chinese ? "已满级" : "Max level");
        add("gui." + Ultraenchantment.MODID + ".starlight.success",
                this.chinese ? "星点点亮：%s" : "Star lit: %s");
        add("gui." + Ultraenchantment.MODID + ".starlight.failure",
                this.chinese ? "星光黯淡，这次没有成功" : "The star dims; the attempt failed");
        add("gui." + Ultraenchantment.MODID + ".starlight.failure_drop",
                this.chinese ? "失败：等级 -%s~%s" : "Failure: -%s~%s levels");
        add("gui." + Ultraenchantment.MODID + ".starlight.failure.drop",
                this.chinese ? "进阶失败：%s 等级 -%s（当前 %s 级）"
                        : "Evolution failed: %s -%s levels (now %s)");
        add("gui." + Ultraenchantment.MODID + ".starlight.failure.no_drop",
                this.chinese ? "进阶失败，等级已是最低" : "Evolution failed; level is already at minimum");
        add("tooltip." + Ultraenchantment.MODID + ".starlight.from",
                this.chinese ? "进阶自：" : "Evolved from: ");
        add("tooltip." + Ultraenchantment.MODID + ".starlight.to",
                this.chinese ? "可进阶为：" : "Can evolve into: ");
        add("tooltip." + Ultraenchantment.MODID + ".starlight.terminal",
                this.chinese ? "已是最终形态" : "Final form");
        add("tooltip." + Ultraenchantment.MODID + ".starlight.hold_shift",
                this.chinese ? "按住 Shift 查看进阶路线" : "Hold Shift for the evolution path");
        add("gui." + Ultraenchantment.MODID + ".starlight.upgraded",
                this.chinese ? "%s 提升到 %s 级" : "%s raised to level %s");
        add("gui." + Ultraenchantment.MODID + ".starlight.not_enough_levels",
                this.chinese ? "经验等级不够" : "Not enough XP levels");
        add("gui." + Ultraenchantment.MODID + ".starlight.invalid",
                this.chinese ? "这颗星现在无法点亮" : "This star cannot be lit now");

        // ── 附魔进阶总览 ──
        add("key.categories." + Ultraenchantment.MODID,
                this.chinese ? "附魔进阶" : "Ultra Enchantment");
        add("key." + Ultraenchantment.MODID + ".open_index",
                this.chinese ? "打开附魔进阶总览" : "Open Ascension Index");
        add("gui." + Ultraenchantment.MODID + ".index.title",
                this.chinese ? "附魔进阶总览" : "Ascension Index");
        add("gui." + Ultraenchantment.MODID + ".index.search",
                this.chinese ? "搜索附魔" : "Search enchantments");
        add("gui." + Ultraenchantment.MODID + ".index.empty",
                this.chinese ? "当前世界没有附魔进阶树" : "No ascension trees in this world");
        add("gui." + Ultraenchantment.MODID + ".index.no_match",
                this.chinese ? "没有匹配的附魔" : "No matching enchantment");
        add("gui." + Ultraenchantment.MODID + ".index.path",
                this.chinese ? "路线 %s" : "Route %s");
        add("gui." + Ultraenchantment.MODID + ".index.edge",
                this.chinese ? "前置 %s 级 · 成功率 %s%% · 经验 %s 级"
                        : "Req. Lv%s · %s%% · %s XP levels");
        add("gui." + Ultraenchantment.MODID + ".index.material",
                this.chinese ? "%s × %s" : "%s × %s");
        add("gui." + Ultraenchantment.MODID + ".index.max_level",
                this.chinese ? "最高 %s 级" : "Max level %s");
        add("gui." + Ultraenchantment.MODID + ".index.key_hint",
                this.chinese ? "按键：%s（可在 选项 → 控制 中设置）"
                        : "Key: %s (set it in Options → Controls)");
        add("gui." + Ultraenchantment.MODID + ".index.unbound",
                this.chinese ? "未绑定" : "Unbound");
        add("gui." + Ultraenchantment.MODID + ".index.shelf_note",
                this.chinese ? "台面书架（至多 15 个）最高减免 50% 经验"
                        : "Bookshelves (up to 15) cut XP costs by up to 50%");

        // ── 阶级 ──
        // 具体星座 / 阶段附魔名由内容包提供（联动包使用来源模组自己的 description 键）。
        add("tier." + Ultraenchantment.MODID + ".native", this.chinese ? "基础" : "Native");
        add("tier." + Ultraenchantment.MODID + ".advanced", this.chinese ? "高阶" : "Advanced");
        add("tier." + Ultraenchantment.MODID + ".super", this.chinese ? "超级" : "Super");
        add("tier." + Ultraenchantment.MODID + ".ultra", this.chinese ? "究极" : "Ultra");
    }

}
