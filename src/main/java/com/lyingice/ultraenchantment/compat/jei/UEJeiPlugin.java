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
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
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
        } catch (RuntimeException | LinkageError t) {
            LOGGER.warn("[JEI] 统计失败（不影响功能）", t);
        }
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
