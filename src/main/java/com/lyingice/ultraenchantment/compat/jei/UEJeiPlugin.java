package com.lyingice.ultraenchantment.compat.jei;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.lyingice.ultraenchantment.content.BookSpecs;
import com.lyingice.ultraenchantment.content.BookSubject;
import com.lyingice.ultraenchantment.registry.UEComponents;
import com.lyingice.ultraenchantment.registry.UEItems;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * JEI 兼容插件。
 *
 * <h2>为什么必须注册 subtype（实测数据）</h2>
 *
 * <p>本模组的书与祛咒石都是<b>单例物品</b>：几百个变体共用同一个 {@code Item}，
 * 只靠<b>数据组件</b>区分（载荷 + {@code custom_model_data}）。
 * 而 JEI 判断「这是不是同一种材料」用的是 subtype——<b>默认完全不认组件</b>。
 *
 * <p>同一份代码、同一个存档，只切换「注册 / 不注册」，JEI 材料表里的条数：
 * <pre>
 *   不注册（JEI 默认）  : 进阶附魔书 <b>1</b> 条，祛咒石 <b>1</b> 条
 *   注册 subtype 之后   : 进阶附魔书 <b>384</b> 条（进化 93 / 载体 276 / 升级 15），祛咒石 <b>3</b> 条
 * </pre>
 * 原版附魔书能一本本列出来，是因为 JEI 在它的原版插件里内建注册了同类解释器；
 * 我们的物品没人替它注册，于是整张材料表里只剩<b>一本书</b>。
 *
 * <h2>键怎么取</h2>
 *
 * <p>键取自<b>语义载荷</b>（载荷组件经自己的 codec 序列化成 JSON），
 * 而不是整份组件：于是同一变体永远同键——不会被修复费、自定义名字、耐久这类
 * 与「这是哪一种书」无关的数据分裂成多条；不同变体必然不同键。
 *
 * <p>用的是 19.5x 起的<b>新接口</b> {@link ISubtypeInterpreter}
 * （{@code getSubtypeData} 返回任意可比较数据）；
 * 旧的 {@code IIngredientSubtypeInterpreter} 已被标记过时待删，不再使用。
 *
 * <p><b>本类只在装了 JEI 的客户端被加载</b>：JEI 依赖是 {@code compileOnly}，
 * 正式包体里没有它的类；{@code @JeiPlugin} 的扫描由 JEI 自己做，
 * 专用服务器上永远不会触碰本类（P0-16）。
 */
@JeiPlugin
public class UEJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 最近一次注册的配方（运行时校验用；JEI 配方是客户端数据，不会同步）。 */
    private static List<AnvilDisplay> LAST_RECIPES = List.of();

    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "core");

    /** 书：按「科目 + 语义载荷」区分。 */
    private static final ISubtypeInterpreter<ItemStack> BOOK_SUBTYPE = new ISubtypeInterpreter<>() {
        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return bookKey(stack);
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return bookKey(stack);
        }
    };

    /** 祛咒石：按阶级区分。 */
    private static final ISubtypeInterpreter<ItemStack> STONE_SUBTYPE = new ISubtypeInterpreter<>() {
        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return stoneKey(stack);
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return stoneKey(stack);
        }
    };

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new AnvilRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<AnvilDisplay> recipes = UEJeiRecipes.build();
        registration.addRecipes(UEJeiRecipeTypes.ANVIL, recipes);

        LAST_RECIPES = recipes;

        int ascend = 0;
        int merge = 0;
        int upgrade = 0;
        for (AnvilDisplay recipe : recipes) {
            switch (recipe.kind()) {
                case ASCEND -> ascend++;
                case MERGE -> merge++;
                case UPGRADE -> upgrade++;
            }
        }
        LOGGER.info("[JEI] 附魔铁砧配方 {} 条（附魔进阶 {} / 附魔合并 {} / 附魔升级 {}）",
                recipes.size(), ascend, merge, upgrade);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 铁砧是三类操作的共同催化剂；书本身也放上去，方便「拿着书按 R」时能跳到这里。
        registration.addRecipeCatalysts(UEJeiRecipeTypes.ANVIL,
                Items.ANVIL, Items.CHIPPED_ANVIL, Items.DAMAGED_ANVIL,
                UEItems.ADVANCED_ENCHANTED_BOOK.get(), UEItems.CURATIVE_STONE.get());
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(UEItems.ADVANCED_ENCHANTED_BOOK.get(), BOOK_SUBTYPE);
        registration.registerSubtypeInterpreter(UEItems.CURATIVE_STONE.get(), STONE_SUBTYPE);
    }

    /**
     * 材料表就绪后记一笔账——「变体到底有没有被 JEI 认成不同材料」只有数字能证明。
     * 想复核就把 {@link #registerItemSubtypes} 里那两行注释掉再跑一次：
     * 条数会塌回 1（这正是本插件存在的理由）。
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        int books = 0;
        int ascension = 0;
        int carrier = 0;
        int upgrade = 0;
        int blank = 0;
        int stones = 0;
        try {
            for (ItemStack stack : runtime.getIngredientManager().getAllItemStacks()) {
                if (UEItems.isAdvancedBook(stack)) {
                    books++;
                    BookSubject subject = UEItems.subjectOf(stack).orElse(null);
                    if (subject == null) {
                        blank++;
                    } else {
                        switch (subject) {
                            case ASCENSION -> ascension++;
                            case INSCRIPTION -> carrier++;
                            case UPGRADE -> upgrade++;
                        }
                    }
                } else if (UEItems.isCurativeStone(stack)) {
                    stones++;
                }
            }
            LOGGER.info("[JEI] 材料表：进阶附魔书 {} 条（进化 {} / 载体 {} / 升级 {} / 空白 {}），祛咒石 {} 条",
                    books, ascension, carrier, upgrade, blank, stones);

            // 创造栏的硬性约定：**载体书一律单条目**（规格：创造栏的轴是「谱系 × 阶级 × 等级」）。
            // 多条目书只能由玩家在铁砧上合并产生，不该出现在这里。
            // 这条检查是可以失败的：谁再往创造栏投一本多条目书，它会立刻报出书名与条目。
            checkNoMultiEntryBooks(runtime);

            // 配方里的展示栈必须能编码：它们是客户端造的、JEI 作弊模式还能直接给到玩家手上，
            // 一旦有服务端 Holder 混进来（P0-17），玩家点一下就掉线。
            checkRecipeStacksEncodable();
        } catch (RuntimeException | LinkageError t) {
            LOGGER.warn("[JEI] 统计失败（不影响功能）", t);
        }
    }

    /**
     * 把每条配方里的每个展示栈都编码一遍。
     *
     * <p>为什么值得单独验：这些栈由客户端构造并交给 JEI 展示（作弊模式还能直接取走），
     * 编码用的是<b>客户端</b>注册表；只要混进一个服务端 Holder，玩家一点就掉线（P0-17）。
     *
     * <p>这条检查能失败：把 {@code UEJeiRecipes} 里的客户端 lookup 换回
     * {@code CommonHooks.resolveLookup}，在单机上它会立刻报出失败的栈。
     */
    private static void checkRecipeStacksEncodable() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            LOGGER.warn("[JEI] 没有客户端世界，跳过配方栈编码校验");
            return;
        }
        int stacks = 0;
        int failed = 0;
        for (AnvilDisplay recipe : LAST_RECIPES) {
            for (List<ItemStack> slot : List.of(recipe.input(), recipe.book(), recipe.output())) {
                for (ItemStack stack : slot) {
                    stacks++;
                    try {
                        RegistryFriendlyByteBuf buf =
                                new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                        ItemStack.STREAM_CODEC.encode(buf, stack);
                    } catch (Throwable t) {
                        failed++;
                        LOGGER.error("[JEI] ⚠️ 配方里的展示栈编不出去（{}）：{}",
                                stack.getItem(), t.toString());
                    }
                }
            }
        }
        LOGGER.info("[JEI] 配方展示栈编码校验：{} 个栈，失败 {} 个（约定 0）", stacks, failed);
    }

    /**
     * 创造栏的硬性约定：<b>载体书一律单条目</b>。
     *
     * <p>多条目书是「玩家在铁砧上合并两本」才该出现的东西（规格 §5.4），不该由创造栏发出来；
     * 而且它与单条目书共用材质，摆在列表里读起来就是「某条附魔的书莫名多了一条别的附魔」。
     *
     * <p>这条检查能失败：谁再把多条目样本投进创造栏，这里会报出阶级与条目（约定值 0）。
     */
    private static void checkNoMultiEntryBooks(IJeiRuntime runtime) {
        int multi = 0;
        for (ItemStack stack : runtime.getIngredientManager().getAllItemStacks()) {
            if (!UEItems.isAdvancedBook(stack)) {
                continue;
            }
            BookSpecs.Inscription spec = stack.get(UEComponents.INSCRIPTION_SPEC.get());
            if (spec == null || spec.entries().size() < 2) {
                continue;
            }
            multi++;
            LOGGER.error("[JEI] ⚠️ 创造栏出现了多条目载体书：「{}」阶 {} —— 创造栏只放单条目书",
                    spec.tier().id(), entriesOf(spec));
        }
        LOGGER.info("[JEI] 创造栏多条目载体书 {} 本（约定值 0）", multi);
    }

    private static String entriesOf(BookSpecs.Inscription spec) {
        StringBuilder sb = new StringBuilder();
        for (BookSpecs.Inscription.Entry entry : spec.entries()) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(entry.enchantment().getPath()).append("(").append(entry.level()).append(")");
        }
        return sb.toString();
    }

    // ── subtype 键 ──────────────────────────────────────────────────────

    private static String bookKey(ItemStack stack) {
        BookSubject subject = UEItems.subjectOf(stack).orElse(null);
        if (subject == null) {
            return "blank";   // 无载荷的空白书：全归一类
        }
        return switch (subject) {
            case ASCENSION -> "ascension|" + json(BookSpecs.Ascension.CODEC,
                    stack.get(UEComponents.ASCENSION_SPEC.get()));
            case INSCRIPTION -> "carrier|" + json(BookSpecs.Inscription.CODEC,
                    stack.get(UEComponents.INSCRIPTION_SPEC.get()));
            case UPGRADE -> "upgrade|" + json(BookSpecs.Upgrade.CODEC,
                    stack.get(UEComponents.UPGRADE_SPEC.get()));
        };
    }

    private static String stoneKey(ItemStack stack) {
        var tier = stack.get(UEComponents.CURATIVE_TIER.get());
        return tier == null ? "curative|none" : "curative|" + tier.id();
    }

    /** 用数据组件自己的 codec 序列化成 JSON 当键：稳定、可读、不可能漏字段。 */
    private static <T> String json(Codec<T> codec, T value) {
        if (value == null) {
            return "none";
        }
        return codec.encodeStart(JsonOps.INSTANCE, value).result()
                .map(Object::toString)
                .orElse("encode-error");
    }
}
