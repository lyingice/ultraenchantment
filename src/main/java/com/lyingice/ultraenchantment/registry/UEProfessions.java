package com.lyingice.ultraenchantment.registry;

import com.lyingice.ultraenchantment.Ultraenchantment;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 村民职业「附魔进阶师」与它的工作站 POI。
 *
 * <h2>工作站为什么是附魔台</h2>
 *
 * <p>原版每个职业都占一个<b>独有</b>的站点方块（{@code PoiTypes} 第 109-121 行：
 * 图书管理员=讲台、牧师=酿造台、武器匠=砂轮……），而
 * <b>附魔台在原版没有任何 POI</b>——它是个纯玩家方块。
 *
 * <p>因此给附魔台注册一个属于本模组的 {@link PoiType} <b>不会与任何原版职业抢站点</b>，
 * 这是整个方案能成立的前提：若复用一个已有站点，村民的职业分配会变成随机二选一。
 *
 * <h2>⚠️ 必须用 DeferredRegister，不能用 Registry.register</h2>
 *
 * <p>实测：在 mod 构造函数里直接 {@code Registry.register(BuiltInRegistries.POINT_OF_INTEREST_TYPE, ...)}
 * 会抛
 * <pre>
 *   IllegalStateException: Registry is already frozen
 *     (trying to add key ResourceKey[minecraft:point_of_interest_type / ultraenchantment:advanced_enchanter])
 * </pre>
 * 到 mod 构造期，{@code BuiltInRegistries} 已经冻结，只能通过
 * {@link DeferredRegister}（它挂的是注册事件，在冻结前那一刻写入）。
 *
 * <h2>⚠️ requestedItems 必须留空</h2>
 *
 * <p>{@link VillagerProfession} 的 {@code requestedItems} 要求构造时就有具体的
 * {@code ImmutableSet<Item>} 值。若写 {@code UEItems.ADVANCED_ENCHANTED_BOOK.get()}，
 * 会因为 {@code DeferredHolder} 尚未绑定而抛
 * 「Trying to access unbound value: ultraenchantment:advanced_enchanted_book」——
 * 职业的 supplier 与物品的 supplier 在同一个注册事件里求值，顺序无保证。
 *
 * <p>空集合的代价仅是村民不会主动捡起我们的书（不影响交易本身，交易由
 * {@code VillagerTradeEvents} 挂载）。
 *
 * <h2>方块状态登记不用我们做</h2>
 *
 * <p>{@code PoiTypes.registerBlockStates} 里写着
 * {@code // Neo: we do this automatically for modded PoiTypes in NeoForgeRegistryCallbacks}，
 * {@code PoiTypeCallbacks.onAdd} 会自动把 {@code matchingStates} 灌进
 * {@code BLOCKSTATE_TO_POI_TYPE_MAP}。手工再灌一次会撞它的
 * 「一个方块状态只能属于一个 POI 类型」断言。
 */
public final class UEProfessions {
    private UEProfessions() {}

    /** 职业 id：{@code ultraenchantment:advanced_enchanter}。 */
    public static final ResourceLocation PROFESSION_ID =
            ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "advanced_enchanter");

    /** 工作站 POI 的注册键。 */
    public static final ResourceKey<PoiType> ADVANCED_ENCHANTER_POI_KEY = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(Ultraenchantment.MODID, "advanced_enchanter"));

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Ultraenchantment.MODID);

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, Ultraenchantment.MODID);

    /**
     * 工作站 POI：附魔台。
     *
     * <p>{@code maxTickets = 1}、{@code validRange = 1} 与原版职业站点一致。
     */
    public static final DeferredHolder<PoiType, PoiType> ADVANCED_ENCHANTER_POI =
            POI_TYPES.register("advanced_enchanter", () -> new PoiType(
                    ImmutableSet.copyOf(Blocks.ENCHANTING_TABLE.getStateDefinition().getPossibleStates()),
                    1, 1));

    /** 职业本体。工作音效复用图书管理员（同为「翻书」语义，本模组无自有音效资源）。 */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> ADVANCED_ENCHANTER =
            PROFESSIONS.register("advanced_enchanter", () -> new VillagerProfession(
                    "advanced_enchanter",
                    holder -> holder.is(ADVANCED_ENCHANTER_POI_KEY),   // heldJobSite
                    holder -> holder.is(ADVANCED_ENCHANTER_POI_KEY),   // acquirableJobSite
                    ImmutableSet.of(),                                  // requestedItems（见类文档）
                    ImmutableSet.of(),                                  // secondaryPoi
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    /** 挂到 mod bus。在 {@code Ultraenchantment} 构造期调用。 */
    public static void register(IEventBus modEventBus) {
        POI_TYPES.register(modEventBus);
        PROFESSIONS.register(modEventBus);
    }
}
