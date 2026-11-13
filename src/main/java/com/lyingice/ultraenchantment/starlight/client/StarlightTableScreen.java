package com.lyingice.ultraenchantment.starlight.client;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.starlight.content.EvolutionData;
import com.lyingice.ultraenchantment.starlight.content.TierDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeNode;
import com.lyingice.ultraenchantment.starlight.content.TreeDefinition;
import com.lyingice.ultraenchantment.starlight.content.TreeEdge;
import com.lyingice.ultraenchantment.starlight.logic.EvolutionService;
import com.lyingice.ultraenchantment.starlight.logic.TreeIndex;
import com.lyingice.ultraenchantment.starlight.menu.StarlightTableMenu;
import com.lyingice.ultraenchantment.starlight.network.EvolutionActionPayload;
import com.lyingice.ultraenchantment.starlight.registry.StarlightComponents;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>星图界面</b>——星光附魔台的 GUI。
 *
 * <h2>它画什么</h2>
 *
 * <p>把 {@link TreeDefinition} 当作一张星座图：
 * 节点是星，边是连线，物品当前所在的节点是「当前星」，
 * 从当前星出发的边是「闪烁的可选下一步」。
 *
 * <p>布局坐标来自数据包的 {@code pos}；没有坐标时退化为简单的网格兜底。
 * 界面只做两件事：<b>选中</b>与<b>发请求</b>，所有判定在服务端。
 */
public class StarlightTableScreen extends AbstractContainerScreen<StarlightTableMenu> {

    // ── 面板布局（GUI 相对坐标）──
    private static final int MAP_X = 8;
    private static final int MAP_Y = 18;
    private static final int MAP_W = 230;
    private static final int MAP_H = 128;
    private static final int PANEL_X = 244;
    private static final int PANEL_Y = 18;
    private static final int PANEL_W = 90;
    private static final int PANEL_H = 128;

    /** GUI 背景。槽位框由作者直接画在这张图上；代码不再额外绘制槽框。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Ultraenchantment.MODID, "textures/gui/starlight_table.png");

    private static final int COLOR_TEXT = 0xFFB8B8D0;
    private static final int COLOR_TITLE = 0xFFFFD86A;
    private static final int COLOR_DIM = 0xFF7A7A96;
    private static final int COLOR_OK = 0xFF6AD86A;
    private static final int COLOR_BAD = 0xFFD86A6A;
    private static final int COLOR_CYAN = 0xFF6AD8FF;
    private static final int COLOR_GOLD = 0xFFE8C86A;

    /** 当前树与选中节点。选中是客户端 UI 状态，不进服务端。 */
    private ResourceLocation currentTreeId;
    private ResourceLocation selectedNode;
    private ResourceLocation hoveredNode;
    private Map<ResourceLocation, ScreenPos> lastPositions = Map.of();

    /** 详情面板底部的按钮状态。 */
    private int buttonX;
    private int buttonY;
    private int buttonW;
    private int buttonH;
    private boolean buttonEnabled;
    private int buttonAction;
    private ResourceLocation buttonNode;

    public StarlightTableScreen(StarlightTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 340;
        this.imageHeight = 240;
        this.titleLabelX = 10;
        this.titleLabelY = 5;
        this.inventoryLabelX = 89;
        this.inventoryLabelY = 150;
    }

    // ── 背景 ────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0,
                this.imageWidth, this.imageHeight, 340, 240);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题在青色标题带上，深色字才读得清
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY,
                0xFF062A2A, false);
        // 物品栏标签默认是 0x404040，在深色面板上等于隐形
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX,
                this.inventoryLabelY, 0xFFB8B8D0, false);
        graphics.drawString(this.font,
                Component.translatable("gui.ultraenchantment.starlight.offering"),
                8, 148, 0xFF062A2A, false);
        graphics.drawString(this.font,
                Component.translatable("gui.ultraenchantment.starlight.catalyst"),
                8, 200, 0xFF8A8AA0, false);
    }

    // ── 主渲染 ──────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        this.buttonEnabled = false;
        this.buttonNode = null;
        this.hoveredNode = null;

        ItemStack item = this.menu.itemStack();
        ResourceLocation treeId = TreeIndex.treeIdOf(item).orElse(null);
        TreeDefinition tree = treeId == null ? null : TreeIndex.tree(treeId).orElse(null);

        if (tree == null || item.isEmpty()) {
            this.currentTreeId = null;
            drawWrapped(graphics, Component.translatable("gui.ultraenchantment.starlight.empty"),
                    this.leftPos + MAP_X + 10, this.topPos + MAP_Y + 12,
                    MAP_W - 20, COLOR_DIM, 6);
        } else {
            this.currentTreeId = treeId;
            if (this.selectedNode != null && tree.node(this.selectedNode).isEmpty()) {
                this.selectedNode = null;
            }

            HolderLookup.RegistryLookup<Enchantment> enchants =
                    this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ResourceLocation currentId = EvolutionService.currentNode(treeId, tree, item).orElse(null);
            // 默认选中当前星：详情卡永远有内容，不再是一块空面板。
            if (this.selectedNode == null && currentId != null) {
                this.selectedNode = currentId;
            }
            TreeNode currentNode = currentId == null ? null : tree.node(currentId).orElse(null);
            int currentLevel = currentNode == null ? 0
                    : EvolutionService.levelOf(item, currentNode, enchants);

            renderStarMap(graphics, tree, item, currentId, currentLevel, mouseX, mouseY);
            renderDetailPanel(graphics, tree, treeId, item, currentId, currentNode, currentLevel,
                    enchants, mouseX, mouseY);
        }

        // ⚠️ 必须最后画：AbstractContainerScreen.render 不会自动画槽位 tooltip。
        // 少了这一句，鼠标悬停物品槽时什么都不会显示。
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    // ── 星图 ────────────────────────────────────────────────────────────

    private void renderStarMap(GuiGraphics graphics, TreeDefinition tree, ItemStack item,
                               ResourceLocation currentId, int currentLevel,
                               int mouseX, int mouseY) {
        Map<ResourceLocation, ScreenPos> positions = layout(tree);
        this.lastPositions = positions;

        Set<ResourceLocation> visited = new HashSet<>();
        EvolutionData data = StarlightComponents.evolutionOf(item);
        if (data != null && data.tree().equals(this.currentTreeId)) {
            visited.addAll(data.history());
            visited.add(data.node());
        } else if (currentId != null) {
            visited.add(currentId);
        }

        // ① 边
        for (TreeEdge edge : tree.edges()) {
            ScreenPos from = positions.get(edge.from());
            ScreenPos to = positions.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            boolean onPath = visited.contains(edge.from()) && visited.contains(edge.to());
            boolean fromCurrent = edge.from().equals(currentId);
            int color = onPath ? COLOR_GOLD : fromCurrent ? COLOR_CYAN : 0xFF34344A;
            int glow = (color & 0x00FFFFFF) | 0x26000000;
            drawLine(graphics, from.x(), from.y(), to.x(), to.y(), glow, 3);
            drawLine(graphics, from.x(), from.y(), to.x(), to.y(), color, 1);
        }

        // ② 节点
        long now = System.currentTimeMillis();
        for (TreeNode node : tree.nodes()) {
            ScreenPos pos = positions.get(node.id());
            if (pos == null) {
                continue;
            }
            TierDefinition tier = TreeIndex.tier(node.tier());
            int base = 0xFF000000 | (tier.color() & 0xFFFFFF);
            boolean current = node.id().equals(currentId);
            boolean isVisited = visited.contains(node.id());
            boolean adjacent = currentId != null && tree.edge(currentId, node.id()).isPresent();

            int radius = current ? 5 : adjacent ? 4 : isVisited ? 3 : 2;
            int alpha = 0xFF;
            if (adjacent && !current) {
                float twinkle = 0.5F + 0.5F * Mth.sin((now % 2400L) / 2400.0F * ((float) Math.PI * 2.0F)
                        + (node.id().hashCode() % 628) / 100.0F);
                alpha = 0x80 + (int) (0x7F * twinkle);
            } else if (!isVisited && !current) {
                alpha = 0x70;
            }
            int argb = (base & 0x00FFFFFF) | (alpha << 24);

            diamond(graphics, pos.x(), pos.y(), radius + 3, (base & 0x00FFFFFF) | 0x22000000);
            diamond(graphics, pos.x(), pos.y(), radius, argb);

            if (current) {
                diamondOutline(graphics, pos.x(), pos.y(), radius + 3, 0xFFFFE08A);
            }
            if (node.id().equals(this.selectedNode)) {
                diamondOutline(graphics, pos.x(), pos.y(), radius + 2, 0xFFFFFFFF);
            }
        }

        // ③ 悬停
        this.hoveredNode = nodeAt(mouseX, mouseY);
    }

    /** 把数据包的 pos 映射到屏幕坐标；缺 pos 的节点退化为网格。 */
    private Map<ResourceLocation, ScreenPos> layout(TreeDefinition tree) {
        Map<ResourceLocation, TreeNode.Pos> raw = new LinkedHashMap<>();
        int fallback = 0;
        for (TreeNode node : tree.nodes()) {
            TreeNode.Pos pos = node.pos().orElse(null);
            if (pos == null) {
                pos = new TreeNode.Pos((fallback % 3) - 1, fallback / 3);
            }
            raw.put(node.id(), pos);
            fallback++;
        }
        if (raw.isEmpty()) {
            return Map.of();
        }

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (TreeNode.Pos pos : raw.values()) {
            minX = Math.min(minX, pos.x());
            maxX = Math.max(maxX, pos.x());
            minY = Math.min(minY, pos.y());
            maxY = Math.max(maxY, pos.y());
        }
        float spanX = Math.max(1.0F, maxX - minX);
        float spanY = Math.max(1.0F, maxY - minY);
        final float scale = Math.min(
                Math.min((MAP_W - 28.0F) / spanX, (MAP_H - 28.0F) / spanY), 42.0F);

        float centerX = this.leftPos + MAP_X + MAP_W / 2.0F;
        float centerY = this.topPos + MAP_Y + MAP_H / 2.0F;
        float midX = (minX + maxX) / 2.0F;
        float midY = (minY + maxY) / 2.0F;

        Map<ResourceLocation, ScreenPos> out = new LinkedHashMap<>();
        raw.forEach((id, pos) -> {
            int x = Math.round(centerX + (pos.x() - midX) * scale);
            int y = Math.round(centerY - (pos.y() - midY) * scale);
            out.put(id, new ScreenPos(x, y));
        });
        return out;
    }

    // ── 详情面板 ────────────────────────────────────────────────────────

    private void renderDetailPanel(GuiGraphics graphics, TreeDefinition tree, ResourceLocation treeId,
                                   ItemStack item, ResourceLocation currentId, TreeNode currentNode,
                                   int currentLevel, HolderLookup.RegistryLookup<Enchantment> enchants,
                                   int mouseX, int mouseY) {
        // 悬停优先：鼠标扫过哪颗星就显示哪颗星的详情；没悬停时回落到已选中的星。
        ResourceLocation selected = this.hoveredNode != null ? this.hoveredNode : this.selectedNode;
        int x = this.leftPos + PANEL_X + 4;
        int y = this.topPos + PANEL_Y + 4;

        if (selected == null) {
            drawWrapped(graphics, Component.translatable("gui.ultraenchantment.starlight.select"),
                    x, y, PANEL_W - 8, COLOR_DIM, 6);
            return;
        }
        TreeNode node = tree.node(selected).orElse(null);
        if (node == null) {
            return;
        }
        Holder<Enchantment> holder = enchants.get(
                ResourceKey.create(Registries.ENCHANTMENT, node.enchantment())).orElse(null);
        Component name = holder == null
                ? Component.literal(node.enchantment().toString())
                : holder.value().description();
        TierDefinition tier = TreeIndex.tier(node.tier());
        int nodeLevel = holder == null ? 0
                : EnchantmentHelper.getEnchantmentsForCrafting(item).getLevel(holder);
        int nodeMax = holder == null ? 0 : holder.value().getMaxLevel();

        // 面板标题：星座名 + 青色分隔线。没有选中星时也不再是一块空面板。
        graphics.drawString(this.font, Component.translatable(tree.display()), x, y, COLOR_CYAN, false);
        graphics.fill(x, y + 11, x + PANEL_W - 8, y + 12, 0xFF3FBDBD);
        int lineY = y + 16;

        graphics.drawString(this.font, name, x, lineY, COLOR_TITLE, false);
        lineY += 11;
        Component tierName = Component.translatable(tier.display());
        graphics.drawString(this.font, tierName, x, lineY,
                0xFF000000 | (tier.color() & 0xFFFFFF), false);
        graphics.drawString(this.font, "Lv. " + nodeLevel + "/" + nodeMax,
                x + this.font.width(tierName) + 4, lineY, COLOR_TEXT, false);
        lineY += 11;

        if (selected.equals(currentId)) {
            graphics.drawString(this.font, Component.translatable("gui.ultraenchantment.starlight.current"),
                    x, lineY, COLOR_OK, false);
            lineY += 13;
            if (nodeLevel < nodeMax) {
                int cost = EvolutionService.upgradeCost(nodeLevel);
                boolean affordable = this.minecraft.player.experienceLevel >= cost;
                drawWrapped(graphics, Component.translatable("gui.ultraenchantment.starlight.upgrade_cost", cost),
                        x, lineY, PANEL_W - 8, affordable ? COLOR_OK : COLOR_BAD, 2);
                setButton(graphics, x, PANEL_W - 8, Component.translatable("gui.ultraenchantment.starlight.upgrade"),
                        affordable, EvolutionActionPayload.ACTION_UPGRADE, node.id());
            } else {
                graphics.drawString(this.font, Component.translatable("gui.ultraenchantment.starlight.max_level"),
                        x, lineY, COLOR_DIM, false);
            }
            return;
        }

        TreeEdge edge = currentId == null ? null : tree.edge(currentId, selected).orElse(null);
        if (edge == null) {
            drawWrapped(graphics, Component.translatable("gui.ultraenchantment.starlight.unreachable"),
                    x, lineY, PANEL_W - 8, COLOR_DIM, 4);
            return;
        }

        boolean levelOk = currentLevel >= edge.requiredLevel();
        boolean xpOk = this.minecraft.player.experienceLevel >= edge.costLevels();
        drawWrapped(graphics, Component.translatable("gui.ultraenchantment.starlight.requires",
                        edge.requiredLevel()), x, lineY, PANEL_W - 8, levelOk ? COLOR_OK : COLOR_BAD, 2);
        lineY += 20;
        drawWrapped(graphics, Component.translatable("gui.ultraenchantment.starlight.cost",
                        edge.costLevels()), x, lineY, PANEL_W - 8, xpOk ? COLOR_OK : COLOR_BAD, 2);
        lineY += 20;
        graphics.drawString(this.font, Component.translatable("gui.ultraenchantment.starlight.chance",
                        Math.round(edge.chance() * 100.0F)), x, lineY, COLOR_TEXT, false);
        lineY += 11;
        // 失败惩罚：当前节点附魔等级随机下降。分支名已在节点名里（如“超级锋利·血刃”），不再单列。
        graphics.drawString(this.font, Component.translatable("gui.ultraenchantment.starlight.failure_drop",
                        edge.failureDropMin(), edge.failureDropMax()), x, lineY, COLOR_BAD, false);

        boolean canAttempt = levelOk && xpOk;
        setButton(graphics, x, PANEL_W - 8, Component.translatable("gui.ultraenchantment.starlight.light"),
                canAttempt, EvolutionActionPayload.ACTION_EVOLVE, node.id());
    }

    private void setButton(GuiGraphics graphics, int x, int width, Component label, boolean enabled,
                           int action, ResourceLocation node) {
        this.buttonX = x;
        this.buttonY = this.topPos + PANEL_Y + PANEL_H - 22;
        this.buttonW = width;
        this.buttonH = 16;
        this.buttonEnabled = enabled;
        this.buttonAction = action;
        this.buttonNode = node;

        graphics.fill(x, this.buttonY, x + width, this.buttonY + this.buttonH,
                enabled ? 0xFF2E5A8A : 0xFF222232);
        graphics.fill(x + 1, this.buttonY + 1, x + width - 1, this.buttonY + this.buttonH - 1,
                enabled ? 0xFF1E3E6A : 0xFF181824);
        graphics.drawCenteredString(this.font, label, x + width / 2, this.buttonY + 4,
                enabled ? 0xFFFFFFFF : 0xFF6A6A80);
    }

    // ── 输入 ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.buttonEnabled && this.buttonNode != null
                && inside(mouseX, mouseY, this.buttonX, this.buttonY, this.buttonW, this.buttonH)) {
            PacketDistributor.sendToServer(new EvolutionActionPayload(
                    this.currentTreeId, this.buttonNode, this.buttonAction));
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY,
                this.leftPos + MAP_X, this.topPos + MAP_Y, MAP_W, MAP_H)) {
            ResourceLocation hit = nodeAt(mouseX, mouseY);
            if (hit != null) {
                this.selectedNode = hit;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ResourceLocation nodeAt(double mouseX, double mouseY) {
        ResourceLocation best = null;
        double bestDistance = 10.0 * 10.0;
        for (Map.Entry<ResourceLocation, ScreenPos> entry : this.lastPositions.entrySet()) {
            ScreenPos pos = entry.getValue();
            double dx = mouseX - pos.x();
            double dy = mouseY - pos.y();
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ── 绘制工具 ────────────────────────────────────────────────────────

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width,
                             int color, int maxLines) {
        List<FormattedCharSequence> lines = this.font.split((FormattedText) text, width);
        int count = Math.min(lines.size(), maxLines);
        for (int i = 0; i < count; i++) {
            graphics.drawString(this.font, lines.get(i), x, y + i * 10, color, false);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                 int color, int thickness) {
        int half = thickness / 2;
        for (int ox = -half; ox <= half; ox++) {
            for (int oy = -half; oy <= half; oy++) {
                bresenham(graphics, x0 + ox, y0 + oy, x1 + ox, y1 + oy, color);
            }
        }
    }

    private static void bresenham(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static void diamond(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = radius - Math.abs(dy);
            graphics.fill(cx - halfWidth, cy + dy, cx + halfWidth + 1, cy + dy + 1, color);
        }
    }

    private static void diamondOutline(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        drawLine(graphics, cx, cy - radius, cx + radius, cy, color, 1);
        drawLine(graphics, cx + radius, cy, cx, cy + radius, color, 1);
        drawLine(graphics, cx, cy + radius, cx - radius, cy, color, 1);
        drawLine(graphics, cx - radius, cy, cx, cy - radius, color, 1);
    }

    private record ScreenPos(int x, int y) {}
}
