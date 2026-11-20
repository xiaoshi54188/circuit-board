package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * 一块电路板现在是 2x2 个方块拼起来的，这个枚举说明「当前这个方块在板子里的位置」。
 *
 * <p>NW 是主控方块（数据存在它身上），另外三块只是把数据拿出来显示。每块都有两条边露在外面，
 * 另外两条边和同板的方块贴在一起。</p>
 */
public enum BoardPart implements StringRepresentable {
    NW("nw", 0, 0),
    NE("ne", 1, 0),
    SW("sw", 0, 1),
    SE("se", 1, 1);

    private final String name;
    private final int dx;
    private final int dz;

    BoardPart(String name, int dx, int dz) {
        this.name = name;
        this.dx = dx;
        this.dz = dz;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    /** 0..3，和 {@link BoardData#pinPart(int)} 对应。 */
    public int index() {
        return dx + dz * 2;
    }

    public static BoardPart of(int index) {
        return values()[index & 3];
    }

    public BlockPos masterPos(BlockPos pos, Direction facing) {
        Direction right = BoardData.rightOf(facing);
        Direction down = BoardData.downOf(facing);
        return pos.offset(
                -right.getStepX() * dx - down.getStepX() * dz,
                -right.getStepY() * dx - down.getStepY() * dz,
                -right.getStepZ() * dx - down.getStepZ() * dz);
    }

    /** 主方块位置 + 这一块在板子里的位置（板子可以竖着放，所以三个轴都要算）。 */
    public BlockPos partPos(BlockPos master, Direction facing) {
        Direction right = BoardData.rightOf(facing);
        Direction down = BoardData.downOf(facing);
        return master.offset(
                right.getStepX() * dx + down.getStepX() * dz,
                right.getStepY() * dx + down.getStepY() * dz,
                right.getStepZ() * dx + down.getStepZ() * dz);
    }

    /** 这个方块的这个方向是不是朝外的（朝内的那面贴着同板的另一半）。 */
    public boolean outer(int side) {
        return switch (side & 3) {
            case 0 -> dz == 0;
            case 1 -> dx == 1;
            case 2 -> dz == 1;
            default -> dx == 0;
        };
    }

    /** 这根针脚是不是长在这个方块上。 */
    public boolean owns(int pin) {
        int side = BoardData.pinSide(pin);
        int along = BoardData.pinAlong(pin);
        return switch (side) {
            case 0 -> dz == 0 && along / 8 == dx;
            case 1 -> dx == 1 && along / 8 == dz;
            case 2 -> dz == 1 && along / 8 == dx;
            default -> dx == 0 && along / 8 == dz;
        };
    }
}
