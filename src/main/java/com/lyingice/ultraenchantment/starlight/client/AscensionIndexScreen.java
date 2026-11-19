package com.lyingice.ultraenchantment.starlight.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.client.UEClientSetup;
import com.lyingice.ultraenchantment.starlight.content.TierDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeNode;
import com.lyingice.ultraenchantment.starlight.content.TreeDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeEdge;
import com.lyingice.ultraenchantment.starlight.logic.TreeIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * <b>附魔进阶总览</b>——把当前数据包里的所有进化树列成一个可查询的图鉴。
 *
 * <p>左侧是树列表（可按附魔名搜索），右侧把每棵树的所有根→叶路线纵向展开：
 * 节点按阶级色显示，节点之间是前置等级 / 成功率 / 经验 / 材料。
 *
 * <p>界面只读客户端已经同步的 {@code starlight_tree} 与
 * {@code minecraft:enchantment} 注册表；不向服务端发任何包。
 * 打开按键默认<b>不绑定</b>，玩家可在「选项 → 控制 → 按键绑定」里自行设置。
 */
public class AscensionIndexScreen extends Screen {

    private static final int W = 360;
    private static final int H = 240;
    private static final int LEFT_W = 132;
    private static final int HEADER_H = 20;
    private static final int FOOTER_H = 34;
    private static final int ROW_H = 20;
    private static final int LIST_TOP = 40;

    private static final int COLOR_PANEL = 0xEE101018;
    private static final int COLOR_HEADER = 0xFF173A3A;
    private static final int COLOR_BORDER = 0xFF3FBDBD;
    private static final int COLOR_SELECTED = 0x553FBDBD;
    private static final int COLOR_HOVER = 0x22FFFFFF;
    private static final int COLOR_TEXT = 0xFFB8B8D0;
    private static final int COLOR_TITLE = 0xFFFFD86A;
    private static final int COLOR_DIM = 0xFF7A7A96;
    private static final int COLOR_CYAN = 0xFF6AD8FF;

    private final List<ResourceLocation> allTrees = new ArrayList<>();
    private final List<ResourceLocation> filtered = new ArrayList<>();
    private final List<Hitbox> hitboxes = new ArrayList<>();

    private int leftPos;
    private int topPos;
    private EditBox search;
    private ResourceLocation selected;
    private int listScroll;
    private int routeScroll;
    private int routeContentHeight;
    /** 打开后的前几 tick 忽略点击，避免「进入世界」的那次点击误选列表。 */
    private int openTicks;

    public AscensionIndexScreen() {
        super(Component.translatable("gui.ultraenchantment.index.title"));
        this.allTrees.addAll(TreeIndex.treeIds());
        this.filtered.addAll(this.allTrees);
        this.selected = this.filtered.isEmpty() ? null : this.filtered.get(0);
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - W) / 2;
        this.topPos = (this.height - H) / 2;

        this.search = new EditBox(this.font, this.leftPos + 8, this.topPos + 23,
                LEFT_W - 16, 14, Component.translatable("gui.ultraenchantment.index.search"));
        this.search.setHint(Component.translatable("gui.ultraenchantment.index.search")
                .withStyle(ChatFormatting.DARK_GRAY));
        this.search.setResponder(this::applyFilter);
        this.addRenderableWidget(this.search);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.leftPos + W - 56, this.topPos + H - 21, 48, 16).build());
    }

    // ── 背景与主渲染 ──────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, COLOR_PANEL);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + HEADER_H, COLOR_HEADER);
        graphics.renderOutline(this.leftPos, this.topPos, W, H, COLOR_BORDER);
        graphics.drawString(this.font, this.title, this.leftPos + 8, this.topPos + 6,
                0xFFBFF7F7, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hitboxes.clear();
        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.allTrees.isEmpty()) {
            this.drawCentered(graphics,
                    Component.translatable("gui.ultraenchantment.index.empty"),
                    this.leftPos + W / 2, this.topPos + H / 2 - 4, COLOR_DIM);
            return;
        }

        renderTreeList(graphics, mouseX, mouseY);
        renderRoutes(graphics, mouseX, mouseY);
        renderFooter(graphics);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    // ── 左侧树列表 ────────────────────────────────────────────────────

    private void renderTreeList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.leftPos + 4;
        int y = this.topPos + LIST_TOP - 6;
        int w = LEFT_W - 8;
        int bottom = this.topPos + H - FOOTER_H;
        int visibleRows = Math.max(1, (bottom - y) / ROW_H);

        this.listScroll = Mth.clamp(this.listScroll, 0,
                Math.max(0, this.filtered.size() - visibleRows));

        graphics.enableScissor(x, y, x + w, bottom);
        if (this.filtered.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.ultraenchantment.index.no_match"),
                    x + 4, y + 4, COLOR_DIM, false);
        }
        for (int i = 0; i < visibleRows; i++) {
            int index = this.listScroll + i;
            if (index >= this.filtered.size()) {
                break;
            }
            ResourceLocation treeId = this.filtered.get(index);
            TreeDefinition tree = TreeIndex.tree(treeId).orElse(null);
            if (tree == null) {
                continue;
            }
            int rowY = y + i * ROW_H;
            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
            if (treeId.equals(this.selected)) {
                graphics.fill(x, rowY, x + w, rowY + ROW_H - 2, COLOR_SELECTED);
            } else if (hovered) {
                graphics.fill(x, rowY, x + w, rowY + ROW_H - 2, COLOR_HOVER);
            }

            Component name = Component.translatable(tree.display());
            String shown = this.font.plainSubstrByWidth(name.getString(), w - 12);
            if (shown.length() < name.getString().length()) {
                shown += "…";
            }
            graphics.drawString(this.font, shown, x + 6, rowY + 4,
                    treeId.equals(this.selected) ? COLOR_TITLE : COLOR_TEXT, false);
        }
        graphics.disableScissor();

        if (this.filtered.size() > visibleRows) {
            int barX = x + w - 2;
            int barTop = y;
            int barH = bottom - y;
            int thumbH = Math.max(12, barH * visibleRows / this.filtered.size());
            int thumbY = barTop + (barH - thumbH) * this.listScroll
                    / Math.max(1, this.filtered.size() - visibleRows);
            graphics.fill(barX, barTop, barX + 2, barTop + barH, 0x33FFFFFF);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_CYAN);
        }
    }

    // ── 右侧路线 ──────────────────────────────────────────────────────

    private void renderRoutes(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.leftPos + LEFT_W + 6;
        int top = this.topPos + HEADER_H + 4;
        int w = W - LEFT_W - 12;
        int bottom = this.topPos + H - FOOTER_H;

        TreeDefinition tree = this.selected == null ? null
                : TreeIndex.tree(this.selected).orElse(null);
        if (tree == null) {
            this.drawCentered(graphics,
                    Component.translatable("gui.ultraenchantment.index.no_match"),
                    this.leftPos + LEFT_W + (W - LEFT_W) / 2, this.topPos + H / 2, COLOR_DIM);
            return;
        }

        Component treeName = Component.translatable(tree.display());
        graphics.drawString(this.font, treeName, x, top, COLOR_CYAN, false);
        Component rootName = rootNode(tree) == null ? Component.empty()
                : nameOf(rootNode(tree));
        graphics.drawString(this.font, rootName, x, top + 12, COLOR_DIM, false);
        graphics.fill(x, top + 24, x + w, top + 25, 0xFF3FBDBD);

        int contentTop = top + 30;
        int viewH = bottom - contentTop;
        List<List<TreeNode>> paths = paths(tree);

        graphics.enableScissor(x, contentTop, x + w, bottom);
        int y = contentTop - this.routeScroll;
        for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++) {
            List<TreeNode> path = paths.get(pathIndex);
            if (paths.size() > 1) {
                graphics.drawString(this.font,
                        Component.translatable("gui.ultraenchantment.index.path", pathIndex + 1),
                        x, y, COLOR_DIM, false);
                y += 11;
            }
            for (int nodeIndex = 0; nodeIndex < path.size(); nodeIndex++) {
                TreeNode node = path.get(nodeIndex);
                y = renderNode(graphics, node, x, y, w, mouseX, mouseY);
                if (nodeIndex + 1 < path.size()) {
                    TreeNode next = path.get(nodeIndex + 1);
                    TreeEdge edge = tree.edge(node.id(), next.id()).orElse(null);
                    if (edge != null) {
                        y = renderEdge(graphics, tree, edge, x, y, w);
                    }
                }
            }
            if (pathIndex + 1 < paths.size()) {
                graphics.fill(x, y + 2, x + w, y + 3, 0x33FFFFFF);
                y += 8;
            }
        }
        graphics.disableScissor();

        this.routeContentHeight = y + this.routeScroll - contentTop;
        this.routeScroll = Mth.clamp(this.routeScroll, 0,
                Math.max(0, this.routeContentHeight - viewH));
    }

    private int renderNode(GuiGraphics graphics, TreeNode node, int x, int y, int w,
                           int mouseX, int mouseY) {
        TierDefinition tier = TreeIndex.tier(node.tier());
        int color = 0xFF000000 | (tier.color() & 0xFFFFFF);
        Component name = nameOf(node);
        String tierName = Component.translatable(tier.display()).getString();
        int tierW = this.font.width(tierName) + 4;
        int nameW = Math.max(20, w - 10 - tierW);

        String shown = this.font.plainSubstrByWidth(name.getString(), nameW);
        if (shown.length() < name.getString().length()) {
            shown += "…";
        }

        graphics.fill(x, y + 3, x + 5, y + 8, color);
        graphics.drawString(this.font, shown, x + 9, y, color, false);
        graphics.drawString(this.font, tierName, x + w - tierW + 4, y, COLOR_DIM, false);

        Holder<Enchantment> holder = holderOf(node);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(name.copy().withStyle(style -> style.withColor(color)));
        tooltip.add(Component.translatable(tier.display()).withStyle(ChatFormatting.GRAY));
        if (holder != null) {
            tooltip.add(Component.translatable("gui.ultraenchantment.index.max_level",
                    holder.value().getMaxLevel()).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal(node.enchantment().toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        this.hitboxes.add(new Hitbox(x, y - 1, x + w, y + 9, tooltip));
        return y + 13;
    }

    private int renderEdge(GuiGraphics graphics, TreeDefinition tree, TreeEdge edge,
                           int x, int y, int w) {
        TreeNode target = tree.node(edge.to()).orElse(null);
        // 总览展示阶级基础经验（书架折扣是台面实时状态，图鉴里不体现）。
        int baseXp = target == null ? edge.costLevels()
                : com.lyingice.ultraenchantment.starlight.logic.EvolutionService.evolutionBaseXp(edge, target);
        Component line = Component.literal("↓ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("gui.ultraenchantment.index.edge",
                        edge.requiredLevel(), Math.round(edge.chance() * 100.0F),
                        baseXp).withStyle(ChatFormatting.GRAY));

        TierDefinition targetTier = TreeIndex.tier(target == null
                ? ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "native")
                : target.tier());
        TierDefinition.MaterialCost material = targetTier.evolutionCost().orElse(null);
        if (material != null) {
            Item item = BuiltInRegistries.ITEM.get(material.item());
            Component itemName = item == null || item == Items.AIR
                    ? Component.literal(material.item().getPath())
                    : new ItemStack(item).getHoverName();
            line = line.copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("gui.ultraenchantment.index.material",
                            material.count(), itemName).withStyle(ChatFormatting.GRAY));
        }

        List<FormattedCharSequence> wrapped = this.font.split(line, w - 12);
        for (FormattedCharSequence part : wrapped) {
            graphics.drawString(this.font, part, x + 9, y, 0xFFFFFFFF, false);
            y += 9;
        }
        return y + 2;
    }

    // ── 路线拆解 ──────────────────────────────────────────────────────

    private List<List<TreeNode>> paths(TreeDefinition tree) {
        Set<ResourceLocation> hasParent = new HashSet<>();
        for (TreeEdge edge : tree.edges()) {
            hasParent.add(edge.to());
        }
        List<List<TreeNode>> out = new ArrayList<>();
        for (TreeNode node : tree.nodes()) {
            if (!hasParent.contains(node.id())) {
                collectPaths(tree, node, new ArrayList<>(), out);
            }
        }
        return out;
    }

    private void collectPaths(TreeDefinition tree, TreeNode node, List<TreeNode> path,
                              List<List<TreeNode>> out) {
        if (path.size() > 64) {
            return;
        }
        path.add(node);
        List<TreeEdge> next = tree.edgesFrom(node.id());
        if (next.isEmpty()) {
            out.add(List.copyOf(path));
        } else {
            for (TreeEdge edge : next) {
                TreeNode child = tree.node(edge.to()).orElse(null);
                if (child != null) {
                    collectPaths(tree, child, path, out);
                }
            }
        }
        path.remove(path.size() - 1);
    }

    private TreeNode rootNode(TreeDefinition tree) {
        Set<ResourceLocation> hasParent = new HashSet<>();
        for (TreeEdge edge : tree.edges()) {
            hasParent.add(edge.to());
        }
        for (TreeNode node : tree.nodes()) {
            if (!hasParent.contains(node.id())) {
                return node;
            }
        }
        return tree.nodes().isEmpty() ? null : tree.nodes().get(0);
    }

    // ── 搜索与提示 ────────────────────────────────────────────────────

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        this.filtered.clear();
        for (ResourceLocation treeId : this.allTrees) {
            TreeDefinition tree = TreeIndex.tree(treeId).orElse(null);
            if (tree != null && (needle.isEmpty() || searchText(tree).contains(needle))) {
                this.filtered.add(treeId);
            }
        }
        this.listScroll = 0;
        if (!this.filtered.contains(this.selected)) {
            this.selected = this.filtered.isEmpty() ? null : this.filtered.get(0);
            this.routeScroll = 0;
        }
    }

    private String searchText(TreeDefinition tree) {
        StringBuilder sb = new StringBuilder(Component.translatable(tree.display()).getString());
        for (TreeNode node : tree.nodes()) {
            sb.append(' ').append(nameOf(node).getString())
                    .append(' ').append(node.enchantment())
                    .append(' ').append(node.id());
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private Component nameOf(TreeNode node) {
        Holder<Enchantment> holder = holderOf(node);
        return holder == null ? Component.literal(node.enchantment().getPath())
                : holder.value().description();
    }

    private Holder<Enchantment> holderOf(TreeNode node) {
        HolderLookup.RegistryLookup<Enchantment> lookup = enchants();
        return lookup == null ? null : lookup.get(
                ResourceKey.create(Registries.ENCHANTMENT, node.enchantment())).orElse(null);
    }

    private HolderLookup.RegistryLookup<Enchantment> enchants() {
        return this.minecraft.level == null ? null
                : this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    private void renderFooter(GuiGraphics graphics) {
        graphics.drawString(this.font,
                Component.translatable("gui.ultraenchantment.index.shelf_note"),
                this.leftPos + 8, this.topPos + H - 28, COLOR_DIM, false);
        int y = this.topPos + H - 16;
        Component key = UEClientSetup.OPEN_INDEX.isUnbound()
                ? Component.translatable("gui.ultraenchantment.index.unbound")
                : UEClientSetup.OPEN_INDEX.getTranslatedKeyMessage();
        graphics.drawString(this.font,
                Component.translatable("gui.ultraenchantment.index.key_hint", key),
                this.leftPos + 8, y, COLOR_DIM, false);
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Hitbox hitbox : this.hitboxes) {
            if (mouseX >= hitbox.x1() && mouseX < hitbox.x2()
                    && mouseY >= hitbox.y1() && mouseY < hitbox.y2()) {
                graphics.renderComponentTooltip(this.font, hitbox.tooltip(), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    // ── 输入 ──────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        this.openTicks++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.openTicks > 2 && this.selected != null) {
            int x = this.leftPos + 4;
            int y = this.topPos + LIST_TOP - 6;
            int w = LEFT_W - 8;
            int bottom = this.topPos + H - FOOTER_H;
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < bottom) {
                int index = this.listScroll + (int) ((mouseY - y) / ROW_H);
                if (index >= 0 && index < this.filtered.size()) {
                    this.selected = this.filtered.get(index);
                    this.routeScroll = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listX = this.leftPos + 4;
        int listBottom = this.topPos + H - FOOTER_H;
        if (mouseX >= listX && mouseX < listX + LEFT_W - 8
                && mouseY >= this.topPos + LIST_TOP - 6 && mouseY < listBottom) {
            this.listScroll = Math.max(0, this.listScroll - (int) Math.signum(scrollY));
            return true;
        }
        int routeX = this.leftPos + LEFT_W + 6;
        if (mouseX >= routeX && mouseX < this.leftPos + W - 6
                && mouseY >= this.topPos + HEADER_H + 4 && mouseY < listBottom) {
            this.routeScroll = Math.max(0, this.routeScroll - (int) Math.signum(scrollY) * 12);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private record Hitbox(int x1, int y1, int x2, int y2, List<Component> tooltip) {}
}
