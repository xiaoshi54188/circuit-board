package cn.blockforge.generated.circuitboard;

/**
 * 电线的颜色表：一个端子上挂了好几根线时，每根线用不同的颜色，一眼就能分清哪根是哪根。
 *
 * <p>这里只存「第几种颜色」，真正的颜色值由 {@link #of(int)} 换出来（ARGB）。之所以只存编号：
 * 一根跳线两头各有一个端子，两头都得画同一根线，所以两头存的必须是同一个编号，
 * 不然同一根线会一半是这个颜色、一半是那个颜色，看着像花屏。</p>
 *
 * <p>这一整套颜色<b>故意绕开红色</b>：红色是「点了要删的线」的标记色，跟它撞色就分不清了。</p>
 */
public final class CableColors {
    /** 一共有几种颜色（一个端子上最多能有这么多种不同颜色的线，再多就从头轮）。 */
    public static final int COUNT = 10;

    /** 第 0 种是铜色：只接一根线的时候，看着还是原来那根铜线。 */
    private static final int[] BASE = {
            0xB87333, // 0 铜（红褐，和「待删」的鲜红差很远）
            0x4FC3F7, // 1 天蓝
            0x66BB6A, // 2 绿
            0xFFD54F, // 3 黄
            0xB39DDB, // 4 紫
            0xECEFF1, // 5 白
            0x26C6DA, // 6 青
            0xAED581, // 7 浅绿
            0x5C6BC0, // 8 蓝
            0x8D6E63, // 9 咖啡
    };

    private CableColors() {
    }

    /** 把一个编号折到 0 ~ {@link #COUNT} 之间（存档里可能来自更老的版本，别越界）。 */
    public static int wrap(int index) {
        int i = index % COUNT;
        return i < 0 ? i + COUNT : i;
    }

    /** 这个编号的颜色（不通电时）。 */
    public static int of(int index) {
        return 0xFF000000 | BASE[wrap(index)];
    }

    /** 这个编号的颜色（通着电时）：同一种颜色往白色提亮，颜色还是认得出是哪一根，但明显在发光。 */
    public static int lit(int index) {
        return blendTowardsWhite(of(index), 0.45F);
    }

    /** 面板上那一小格用的深色：拿线的本色压暗当成底色，白字才看得清。 */
    public static int chip(int index) {
        int color = of(index);
        int r = (int) (((color >> 16) & 0xFF) * 0.34F);
        int g = (int) (((color >> 8) & 0xFF) * 0.34F);
        int b = (int) ((color & 0xFF) * 0.34F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int blendTowardsWhite(int color, float amount) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
