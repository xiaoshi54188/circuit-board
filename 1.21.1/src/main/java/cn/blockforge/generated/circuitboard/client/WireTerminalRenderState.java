package cn.blockforge.generated.circuitboard.client;

import java.util.Arrays;

/**
 * 端子上挂着的每根线要画的数据：目标点（相对自己的坐标）、颜色、是不是「要删」的红色。
 */
public class WireTerminalRenderState {
    public int count;
    /** 每根线 3 个数：目标点 xyz。 */
    public float[] targets = new float[3 * 8];
    /** 每根线的颜色（ARGB，已经算好通不通电）。 */
    public int[] colors = new int[8];
    public boolean[] pending = new boolean[8];
    /** 每根线画多细（{@code CableGeometry.DETAIL_*}）：近处满细节，远处省着画。 */
    public byte[] details = new byte[8];

    /** 一个端子上能挂几根线不封顶，数组按需要长大。 */
    public void ensure(int capacity) {
        if (capacity <= colors.length) {
            return;
        }
        int grown = Math.max(capacity, colors.length * 2);
        targets = Arrays.copyOf(targets, grown * 3);
        colors = Arrays.copyOf(colors, grown);
        pending = Arrays.copyOf(pending, grown);
        details = Arrays.copyOf(details, grown);
    }
}
