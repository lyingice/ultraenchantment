package com.lyingice.ultraenchantment.content;

/**
 * 书的<b>外观档位</b>编码——把「科目 × 阶级」压进一个整数。
 *
 * <p>1.21.1 的物品模型只有一条 override 链，{@code custom_model_data} 是唯一可用的谓词，
 * 因此两个维度必须编码成一个数：{@code 科目偏移 + 阶级档位}（铭刻 0 / 进化 10 / 升级 20）。
 *
 * <h2>为什么放在 content（运行期）而不是 datagen</h2>
 *
 * <p>这段算术有两个消费者：<b>datagen</b> 写模型 JSON 时要用它，
 * <b>运行期</b>（{@code BookFactory} 造书，也就是铁砧产物）也要用它写物品组件。
 * 一旦把它定义在 datagen 类里，运行期引用就会把<b>客户端模型生成器</b>的整个类层级拖进来
 * ——{@code ItemModelProvider} 属于客户端 datagen API，在只跑服务端的发行版里未必存在。
 * 后果不是「功能没生效」，而是写入铁砧结果槽时抛 {@code NoClassDefFoundError}：
 * 包处理链里的错误 = 玩家直接掉线（见 AGENT.md P0-15）。
 *
 * <p>方向必须是「<b>datagen 依赖运行期</b>」，不能反过来。
 */
public final class BookView {
    private BookView() {}

    /** 科目偏移：铭刻 = 0、进化 = 10、升级 = 20。 */
    public static int subjectOffset(String subjectId) {
        return switch (subjectId) {
            case "inscription" -> 0;
            case "ascension" -> 10;
            case "upgrade" -> 20;
            default -> throw new IllegalArgumentException("未知科目: " + subjectId);
        };
    }

    /** 谓词值：{@code 科目偏移 + 阶级档位}。 */
    public static int predicateOf(BookSubject subject, UETier tier) {
        return predicateOf(subject.id(), tier);
    }

    /** 字符串版，供 datagen 按科目 id 调用。 */
    public static int predicateOf(String subjectId, UETier tier) {
        return subjectOffset(subjectId) + tier.modelData();
    }
}
