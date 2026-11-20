package cn.blockforge.generated.circuitboard.client;

import cn.blockforge.generated.circuitboard.BoardData;
import java.util.Arrays;

/** 渲染电路板需要的数据（每帧从方块实体拷一份过来）：针脚、零件、电线。 */
public class BoardRenderState {
    /** 这个渲染状态对应 2x2 板子里的第几块（0..3）。 */
    public int part;
    /** 板面朝哪一边（{@link net.minecraft.core.Direction#ordinal()}）。 */
    public int facing;
    public final boolean[] pins = new boolean[BoardData.PIN_COUNT];
    public final boolean[] lit = new boolean[BoardData.PIN_COUNT];
    public final int[] cells = new int[BoardData.INT_COUNT];
    public final boolean[] cellLit = new boolean[BoardData.CELL_COUNT];
    /** 每根电线 8 个数：起点 xyz、终点 xyz、亮不亮（1/0）、终点是不是一个方块（1/0）。 */
    public float[] wires = new float[8 * 32];
    /** 每根电线有没有信号（送出去的、读进来的都算）。 */
    public boolean[] wireOn = new boolean[32];
    /** 界面里被点了「要删」的那一根：画成红色。 */
    public boolean[] wirePending = new boolean[32];
    public int wireCount;
    /** 每根飞线 6 个数：起点 xyz、终点 xyz。 */
    public float[] flies = new float[6 * 16];
    /** 每根飞线通不通电。 */
    public boolean[] flyOn = new boolean[16];
    public int flyCount;
    /** 这块板离眼睛多远：近处画满细节，远处少画几边（见 {@code CableGeometry.DETAIL_*}）。 */
    public int detail = CableGeometry.DETAIL_FULL;

    /** 电线根数不封顶，数组按需要长大；长大一次以后就一直够用。 */
    public void ensureWires(int count) {
        if (count <= wireOn.length) {
            return;
        }
        int capacity = Math.max(count, wireOn.length * 2);
        wires = Arrays.copyOf(wires, capacity * 8);
        wireOn = Arrays.copyOf(wireOn, capacity);
        wirePending = Arrays.copyOf(wirePending, capacity);
    }

    /** 飞线同理：不再限制条数。 */
    public void ensureFlies(int count) {
        if (count <= flyOn.length) {
            return;
        }
        int capacity = Math.max(count, flyOn.length * 2);
        flies = Arrays.copyOf(flies, capacity * 6);
        flyOn = Arrays.copyOf(flyOn, capacity);
    }
}
