package com.lyingice.ultraenchantment.compat.jei;

import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.content.UETier;
import com.lyingice.ultraenchantment.logic.BookFactory;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 「附魔铁砧」JEI 类别——三类操作共用一条类别，靠配方上那行字区分：
 *
 * <pre>
 *   附魔进阶   [物品] + [进阶书]  →  [进阶后的物品]
 *   附魔合并   [书]   + [书]      →  [合并后的书]
 *   附魔升级   [物品] + [升级书]  →  [等级更高的物品]
 * </pre>
 *
 * <h2>轮播</h2>
 * <p>三个槽都吃列表（{@code addItemStacks}），JEI 会在同一格里轮播——「中间等级」就是靠它展示的：
 * 一条配方覆盖 2..上限 的全部升级书与对应结果，不膨胀条目数。
 *
 * <h2>背景与图标</h2>
 * <p>背景用 {@code createBlankDrawable}（白板 + 插槽背景），不需要美术资源；
 * 类别图标是<b>三种书轮播</b>（自己实现 {@link IDrawable}，按秒切换），
 * 一眼能看出这条类别同时管进阶 / 合并 / 升级。
 */
public class AnvilRecipeCategory implements IRecipeCategory<AnvilDisplay> {
    private static final int WIDTH = 104;
    private static final int HEIGHT = 44;
    private static final int SLOT_Y = 22;

    private final IDrawable background;
    private final IDrawable icon;

    public AnvilRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = new CyclingIcon();
    }

    /** 配方上那行字：附魔进阶 / 附魔合并 / 附魔升级。 */
    private static Component actionLabel(AnvilDisplay.Kind kind) {
        return Component.translatable("jei.ultraenchantment.action." + kind.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public RecipeType<AnvilDisplay> getRecipeType() {
        return UEJeiRecipeTypes.ANVIL;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ultraenchantment.category.anvil");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AnvilDisplay recipe, IFocusGroup focuses) {
        // 左槽挂说明：进阶要求（满级 / 指定等级）、合并规则等——鼠标停在左槽上就能读到。
        builder.addInputSlot(2, SLOT_Y)
                .setStandardSlotBackground()
                .addItemStacks(recipe.input())
                .addRichTooltipCallback((slotView, tooltip) -> recipe.notes().forEach(tooltip::add));
        builder.addInputSlot(26, SLOT_Y).setStandardSlotBackground().addItemStacks(recipe.book());
        builder.addOutputSlot(78, SLOT_Y).setOutputSlotBackground().addItemStacks(recipe.output());
    }

    /** 悬停整条配方也给出说明，方便没对准左槽的玩家。 */
    @Override
    public void getTooltip(ITooltipBuilder tooltip, AnvilDisplay recipe, IRecipeSlotsView slotsView,
                           double mouseX, double mouseY) {
        tooltip.addAll(recipe.notes());
    }

    @Override
    public void draw(AnvilDisplay recipe, IRecipeSlotsView slotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, actionLabel(recipe.kind()), 2, 2, 0x404040, false);
        graphics.drawString(font, "+", 20, SLOT_Y + 4, 0x808080, false);
        graphics.drawString(font, "→", 50, SLOT_Y + 4, 0x808080, false);
    }

    /** 三种图标轮播：进阶书 → 载体书 → 升级书，每秒换一张。 */
    private static final class CyclingIcon implements IDrawable {
        private final List<ItemStack> icons = List.of(
                BookFactory.create(BookSubject.ASCENSION, UETier.ADVANCED),
                BookFactory.create(BookSubject.INSCRIPTION, UETier.ADVANCED),
                BookFactory.create(BookSubject.UPGRADE, UETier.ADVANCED));

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
            long second = System.currentTimeMillis() / 1000L;
            graphics.renderItem(icons.get((int) (second % icons.size())), xOffset, yOffset);
        }
    }
}
