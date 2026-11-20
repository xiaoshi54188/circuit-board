package cn.blockforge.generated.circuitboard;

import net.minecraft.core.Direction;

/**
 * 电路板的面板数据：16x16 个格子，每格 1 字节（低 4 位是类型，高 4 位是元件朝向）。
 * 这里只放纯数学和几何，不带任何 Minecraft 状态，方便客户端界面和服务端逻辑共用。
 *
 * <p>整块板在世界上占 2x2 个方块，所以一格是 1/8 方块；下面的几何坐标都用「板子空间」：
 * x、z 从 0 到 {@link #BOARD_BLOCKS}，y 从 0 到 1。渲染某个方块时减去它在板里的偏移就行。</p>
 */
public final class BoardData {
    /** 面板一边的格子数。 */
    public static final int SIZE = 16;
    /** 面板每边占几个方块（一块板 2x2 方块）。 */
    public static final int BOARD_BLOCKS = 2;
    /** 一个方块对应几格。 */
    public static final int CELLS_PER_BLOCK = SIZE / BOARD_BLOCKS;
    /** 面板格子总数。 */
    public static final int CELL_COUNT = SIZE * SIZE;
    /** 一个 int 里塞 4 格（每格 1 字节）。 */
    public static final int CELLS_PER_INT = 4;
    /** cells 数组长度。 */
    public static final int INT_COUNT = CELL_COUNT / CELLS_PER_INT;
    /** 上一版存档的格式：每格 4 bit，一个 int 装 8 格（读老存档时用）。 */
    public static final int LEGACY_INT_COUNT = CELL_COUNT / 8;
    /** 针脚总数：4 边 x 每边 16 个。 */
    public static final int PIN_COUNT = 64;
    /** 一格在世界里的边长（方块为单位）。 */
    public static final float CELL_SIZE = 1.0F / CELLS_PER_BLOCK;

    public static final int TYPE_EMPTY = 0;
    public static final int TYPE_TRACE = 1;
    /** 电容：单向通过，断电后还能放一会儿电。 */
    public static final int TYPE_CAPACITOR = 2;
    /** 二极管：只让电流朝箭头那一面走，反方向完全不通。 */
    public static final int TYPE_DIODE = 3;
    /** 三极管：两侧任意一边有电时就导通。 */
    public static final int TYPE_TRANSISTOR = 4;
    /** 电阻：只有两个引脚，所以只能直着穿过，不能拐弯。 */
    public static final int TYPE_RESISTOR = 5;
    /** 开关：自己就是电源，右键切换开 / 关。dir 的最低位是开关状态。 */
    public static final int TYPE_SWITCH = 6;
    /** 与门：箭头左右两侧都有电，箭头那一面才出电。 */
    public static final int TYPE_AND = 7;
    /** 或门：箭头左右任意一侧有电，箭头那一面就出电。 */
    public static final int TYPE_OR = 8;
    /** 延迟器：电流进来后等几个 tick 才从箭头方向输出。 */
    public static final int TYPE_DELAY = 9;
    /** 跳线（跨线桥）：只沿自身轴线双向导通，横着的线不会连通。 */
    public static final int TYPE_JUMPER = 10;
    /** 非门：箭头那一面一直通电，直到它背后（箭头反面）也被通电为止。 */
    public static final int TYPE_NOT = 11;
    /** 计时器：收到信号（箭头背面那一格有电）才一闪一闪地打钟，箭头那边是出口，dir 的高两位是周期。 */
    public static final int TYPE_TIMER = 12;
    /**
     * 逻辑门：与非 / 或非 / 异或 / 同或。箭头那一面是输出口，左右两侧是输入口。
     * dir 的低两位是箭头方向，高两位是「哪一种门」（见下面的 LOGIC_ 常量）。
     */
    public static final int TYPE_LOGIC = 13;
    /** 最高用到的类型编号，之后新增零件只要往上加。 */
    public static final int TYPE_MAX = TYPE_LOGIC;

    /** 逻辑门的四种：与非、或非、异或、同或。 */
    public static final int LOGIC_NAND = 0;
    public static final int LOGIC_NOR = 1;
    public static final int LOGIC_XOR = 2;
    public static final int LOGIC_XNOR = 3;

    /** 板子本体厚度（以方块为单位）：0 ~ 3/16。 */
    public static final float PLATE_TOP = 3.0F / 16.0F;

    /** 0=北(-Z) 1=东(+X) 2=南(+Z) 3=西(-X)。这是「面板自己的」四个方向，不是世界方向。 */
    public static final Direction[] SIDES = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private BoardData() {
    }

    // ------------------------------------------------------------------ 板子的朝向

    /**
     * 板子可以平着放（朝上 / 朝下），也可以竖着贴在墙上（朝东南西北）。
     * {@code facing} 记的就是「板面朝哪一边」，也就是从板子正面往外指的方向。
     *
     * <p>板子自己的坐标系是：(x 往右、y 往正面外、z 往下)。下面两个方法把「面板上的右和下」
     * 翻译成世界方向，板子所有几何、针脚、渲染都靠它们换算。</p>
     */

    /** 板面朝 facing 时，「面板上的右边」在世界里是哪个方向。 */
    public static Direction rightOf(Direction facing) {
        return switch (facing) {
            case UP, DOWN, SOUTH -> Direction.EAST;
            case NORTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
        };
    }

    /** 板面朝 facing 时，「面板上的下边」在世界里是哪个方向（竖着的板下边朝下）。 */
    public static Direction downOf(Direction facing) {
        return switch (facing) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.DOWN;
        };
    }

    /**
     * 板子局部坐标的原点：局部 (0,0,0) 落在方块里的哪一个角。
     *
     * <p>板面朝 facing，「往右」是 right，「往下」是 down。三条轴里只要有一条是往负方向走的
     * （比如竖着贴墙时「往下」就是 -Y，朝北的板子「往右」是 -X），起点就得先挪到方块的另一头，
     * 转出来的几何才会正好落在这一格里。三条轴互相垂直，每个分量只有一条轴有值，直接相加就行。</p>
     *
     * <p>这里原来是一张写死的表，竖着放的时候漏了「往下」那一轴的 1，结果零件、针脚、电线
     * 全都画到旁边 / 下面那一格去了，看着就像渲染错位。</p>
     */
    private static float origin(Direction right, Direction facing, Direction down, int axis) {
        float origin = 0.0F;
        if (step(right, axis) < 0) {
            origin += 1.0F;
        }
        if (step(facing, axis) < 0) {
            origin += 1.0F;
        }
        if (step(down, axis) < 0) {
            origin += 1.0F;
        }
        return origin;
    }

    /** 方向在某个轴上的步长（axis：0=x，1=y，2=z）。 */
    private static int step(Direction direction, int axis) {
        return switch (axis) {
            case 0 -> direction.getStepX();
            case 1 -> direction.getStepY();
            default -> direction.getStepZ();
        };
    }

    /** 面板的第 side 条边（{@link #SIDES} 的下标）在世界里是哪个方向。 */
    public static Direction localToWorld(Direction facing, int side) {
        Direction right = rightOf(facing);
        Direction down = downOf(facing);
        return switch (side & 3) {
            case 0 -> down.getOpposite();
            case 1 -> right;
            case 2 -> down;
            default -> right.getOpposite();
        };
    }

    /** 世界方向是面板的哪一条边（0..3）；不是面板的四条边之一就返回 -1（板子正反面）。 */
    public static int sideOfWorld(Direction facing, Direction world) {
        if (world == null) {
            return -1;
        }
        Direction right = rightOf(facing);
        Direction down = downOf(facing);
        if (world == down.getOpposite()) {
            return 0;
        }
        if (world == right) {
            return 1;
        }
        if (world == down) {
            return 2;
        }
        if (world == right.getOpposite()) {
            return 3;
        }
        return -1;
    }

    /**
     * 板子自己的坐标（相对板子西北角，单位是方块）换成世界方向的偏移。
     * 只做「转一下」不做平移，所以结果还是相对板子原点的。
     */
    public static float[] localToWorld(Direction facing, float lx, float ly, float lz, float[] out) {
        Direction right = rightOf(facing);
        Direction down = downOf(facing);
        out[0] = origin(right, facing, down, 0)
                + right.getStepX() * lx + facing.getStepX() * ly + down.getStepX() * lz;
        out[1] = origin(right, facing, down, 1)
                + right.getStepY() * lx + facing.getStepY() * ly + down.getStepY() * lz;
        out[2] = origin(right, facing, down, 2)
                + right.getStepZ() * lx + facing.getStepZ() * ly + down.getStepZ() * lz;
        return out;
    }

    /** 同上，但一次把一整个轴对齐的立方体转过去（转完还是轴对齐的，重新算一遍包围盒）。 */
    public static float[] boxToWorld(Direction facing, float[] box, float[] out) {
        Direction right = rightOf(facing);
        Direction down = downOf(facing);
        float ox = origin(right, facing, down, 0);
        float oy = origin(right, facing, down, 1);
        float oz = origin(right, facing, down, 2);
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            float lx = (corner & 1) == 0 ? box[0] : box[3];
            float ly = (corner & 2) == 0 ? box[1] : box[4];
            float lz = (corner & 4) == 0 ? box[2] : box[5];
            float wx = ox + right.getStepX() * lx + facing.getStepX() * ly
                    + down.getStepX() * lz;
            float wy = oy + right.getStepY() * lx + facing.getStepY() * ly
                    + down.getStepY() * lz;
            float wz = oz + right.getStepZ() * lx + facing.getStepZ() * ly
                    + down.getStepZ() * lz;
            minX = Math.min(minX, wx);
            minY = Math.min(minY, wy);
            minZ = Math.min(minZ, wz);
            maxX = Math.max(maxX, wx);
            maxY = Math.max(maxY, wy);
            maxZ = Math.max(maxZ, wz);
        }
        out[0] = minX;
        out[1] = minY;
        out[2] = minZ;
        out[3] = maxX;
        out[4] = maxY;
        out[5] = maxZ;
        return out;
    }

    public static int[] emptyCells() {
        return new int[INT_COUNT];
    }

    public static int get(int[] cells, int index) {
        return (cells[index >> 2] >>> ((index & 3) * 8)) & 0xFF;
    }

    public static void set(int[] cells, int index, int value) {
        int shift = (index & 3) * 8;
        cells[index >> 2] = (cells[index >> 2] & ~(0xFF << shift)) | ((value & 0xFF) << shift);
    }

    public static int type(int value) {
        return value & 0xF;
    }

    public static int dir(int value) {
        return (value >> 4) & 0xF;
    }

    public static int make(int type, int dir) {
        return (type & 0xF) | ((dir & 0xF) << 4);
    }

    /** 这一格放的是不是零件（电容 / 二极管 / 三极管 ... 跳线）。 */
    public static boolean isComponent(int type) {
        return type >= TYPE_CAPACITOR && type <= TYPE_MAX;
    }

    /** 这个零件用不用「朝向」这个参数。 */
    public static boolean hasFacing(int type) {
        return switch (type) {
            case TYPE_TRACE, TYPE_SWITCH, TYPE_EMPTY -> false;
            default -> isComponent(type);
        };
    }

    /** 开关的 dir 最低位就是开 / 关。 */
    public static boolean switchOn(int value) {
        return (dir(value) & 1) != 0;
    }

    public static int switchValue(boolean on) {
        return make(TYPE_SWITCH, on ? 1 : 0);
    }

    /**
     * 元件真正的朝向（箭头指哪边）。
     *
     * <p>计时器的 dir 高两位用来存「周期档位」、逻辑门的高两位用来存「哪一种门」，
     * 所以这两种的朝向只取低两位，其它零件没有额外信息，直接就是 dir。</p>
     */
    public static int facing(int value) {
        int type = type(value);
        return type == TYPE_TIMER || type == TYPE_LOGIC ? (dir(value) & 3) : dir(value);
    }

    /** 计时器的周期档位（0 最快，3 最慢）。 */
    public static int timerSpeed(int value) {
        return (dir(value) >> 2) & 3;
    }

    /** 拼一个计时器：facing 是出电方向，speed 是周期档位。 */
    public static int timerValue(int facing, int speed) {
        return make(TYPE_TIMER, (facing & 3) | ((speed & 3) << 2));
    }

    /** 这一格是哪种逻辑门（{@link #LOGIC_NAND} 等）。 */
    public static int logicKind(int value) {
        return (dir(value) >> 2) & 3;
    }

    /** 拼一个逻辑门：kind 是哪种门，facing 是输出口朝哪边。 */
    public static int logicValue(int kind, int facing) {
        return make(TYPE_LOGIC, (facing & 3) | ((kind & 3) << 2));
    }

    /** 把元件原地转一个方向（计时器只转朝向，周期保持不变）。 */
    public static int rotate(int value) {
        int type = type(value);
        if (!hasFacing(type)) {
            return make(type, dir(value));
        }
        return make(type, ((facing(value) + 1) & 3) | (dir(value) & 0xC));
    }

    public static int cellIndex(int col, int row) {
        return row * SIZE + col;
    }

    public static int colOf(int index) {
        return index % SIZE;
    }

    public static int rowOf(int index) {
        return index / SIZE;
    }

    /** 这个格子是不是贴着板子的边（贴边的格子才能长出针脚）。 */
    public static boolean isEdgeCell(int index) {
        int col = colOf(index);
        int row = rowOf(index);
        return col == 0 || col == SIZE - 1 || row == 0 || row == SIZE - 1;
    }

    /** 返回指定方向相邻格子的下标，越界返回 -1。 */
    public static int neighbourCell(int index, int dir) {
        int col = colOf(index);
        int row = rowOf(index);
        switch (dir) {
            case 0 -> row--;
            case 1 -> col++;
            case 2 -> row++;
            default -> col--;
        }
        if (col < 0 || col >= SIZE || row < 0 || row >= SIZE) {
            return -1;
        }
        return cellIndex(col, row);
    }

    /** 这一格的电流能不能沿着 dir 方向离开（带控制端的零件在方块实体里另外判断）。 */
    public static boolean passesToward(int value, int dir) {
        return switch (type(value)) {
            case TYPE_TRACE -> true;
            case TYPE_SWITCH -> switchOn(value);
            case TYPE_RESISTOR, TYPE_JUMPER -> dir(value) == dir || ((dir(value) + 2) & 3) == dir;
            // 非门 / 与门 / 或门 / 逻辑门 / 计时器不「透电」：只由它自己朝箭头那边出电
            case TYPE_NOT, TYPE_AND, TYPE_OR, TYPE_LOGIC, TYPE_TIMER -> false;
            default -> dir(value) == dir;
        };
    }

    /** 把旧版（每格 4 bit）的格子数据转成新格式，老存档不至于白掉。 */
    public static int[] fromLegacy(int[] legacy) {
        int[] cells = emptyCells();
        for (int i = 0; i < CELL_COUNT; i++) {
            int value = (legacy[i >> 3] >>> ((i & 7) * 4)) & 0xF;
            set(cells, i, make(value & 3, (value >> 2) & 3));
        }
        return cells;
    }

    public static int pin(int side, int along) {
        return (side & 3) * SIZE + (along & 15);
    }

    public static int pinSide(int pin) {
        return pin / SIZE;
    }

    public static int pinAlong(int pin) {
        return pin % SIZE;
    }

    /** 针脚在 2x2 板子里属于哪个方块（0..3，对应 {@link BoardPart#index()}）。 */
    public static int pinPart(int pin) {
        int side = pinSide(pin);
        int along = pinAlong(pin);
        int half = along / CELLS_PER_BLOCK;
        return switch (side) {
            case 0 -> half;
            case 1 -> 1 + half * 2;
            case 2 -> half + 2;
            default -> half * 2;
        };
    }

    /** 针脚对应的面板格子（贴着边的那一格）。 */
    public static int pinCell(int pin) {
        int side = pinSide(pin);
        int along = pinAlong(pin);
        return switch (side) {
            case 0 -> cellIndex(along, 0);
            case 1 -> cellIndex(SIZE - 1, along);
            case 2 -> cellIndex(along, SIZE - 1);
            default -> cellIndex(0, along);
        };
    }

    /** 这个格子上的针脚：边上的格子才有一根针脚，否则 -1。中间的格子返回 -1。 */
    public static int pinAtCell(int cell) {
        int col = colOf(cell);
        int row = rowOf(cell);
        if (row == 0) {
            return pin(0, col);
        }
        if (col == SIZE - 1) {
            return pin(1, row);
        }
        if (row == SIZE - 1) {
            return pin(2, col);
        }
        if (col == 0) {
            return pin(3, row);
        }
        return -1;
    }

    public static int oppositeSide(int side) {
        return (side + 2) & 3;
    }

    /** 面板格子所在行/列对应的世界朝向（给界面和渲染用）。 */
    public static int sideOfDirection(Direction direction) {
        for (int i = 0; i < SIDES.length; i++) {
            if (SIDES[i] == direction) {
                return i;
            }
        }
        return -1;
    }

    /** 格子在板子空间里的中心点。 */
    public static float cellCentreX(int cell) {
        return (colOf(cell) + 0.5F) * CELL_SIZE;
    }

    public static float cellCentreZ(int cell) {
        return (rowOf(cell) + 0.5F) * CELL_SIZE;
    }

    /**
     * 针脚在板子空间里的立方体：{x0,y0,z0,x1,y1,z1}。
     * 针脚比面板高一点，并且往外伸出 1.5/16，看起来像从板边戳出来的小金属针。
     */
    public static float[] pinBox(int pin) {
        return pinBox(pin, new float[6]);
    }

    /** 同上，但写进现成的数组里（每帧都要画，省得一直新开数组）。 */
    public static float[] pinBox(int pin, float[] out) {
        int side = pinSide(pin);
        int along = pinAlong(pin);
        float y0 = PLATE_TOP;
        float y1 = 6.0F / 16.0F;
        float a0 = along * CELL_SIZE + 0.35F / 16.0F;
        float a1 = (along + 1) * CELL_SIZE - 0.35F / 16.0F;
        float stick = 2.25F / 16.0F;
        float in = 0.6F / 16.0F;
        float far = BOARD_BLOCKS;
        switch (side) {
            case 0 -> {
                out[0] = a0;
                out[1] = y0;
                out[2] = -stick;
                out[3] = a1;
                out[4] = y1;
                out[5] = in;
            }
            case 1 -> {
                out[0] = far - in;
                out[1] = y0;
                out[2] = a0;
                out[3] = far + stick;
                out[4] = y1;
                out[5] = a1;
            }
            case 2 -> {
                out[0] = a0;
                out[1] = y0;
                out[2] = far - in;
                out[3] = a1;
                out[4] = y1;
                out[5] = far + stick;
            }
            default -> {
                out[0] = -stick;
                out[1] = y0;
                out[2] = a0;
                out[3] = in;
                out[4] = y1;
                out[5] = a1;
            }
        }
        return out;
    }

    /** 算针脚中心点用的临时数组（只在客户端渲染那一条线程上用）。 */
    private static final float[] PIN_CENTRE_TEMP = new float[6];

    /** 针脚中心点（板子空间，用来算电线从哪长出来）。 */
    public static float[] pinCentre(int pin) {
        return pinCentre(pin, new float[3]);
    }

    /** 同上，但写进现成的数组里。 */
    public static float[] pinCentre(int pin, float[] out) {
        float[] box = pinBox(pin, PIN_CENTRE_TEMP);
        out[0] = (box[0] + box[3]) * 0.5F;
        out[1] = (box[1] + box[4]) * 0.5F;
        out[2] = (box[2] + box[5]) * 0.5F;
        return out;
    }

    /** 零件最高点（渲染用，方块为单位）。 */
    private static float partHeight(int type) {
        return switch (type) {
            case TYPE_CAPACITOR -> 6.2F / 16.0F;
            case TYPE_DIODE -> 4.9F / 16.0F;
            case TYPE_TRANSISTOR -> 6.0F / 16.0F;
            case TYPE_RESISTOR -> 4.7F / 16.0F;
            case TYPE_SWITCH -> 4.6F / 16.0F;
            case TYPE_AND, TYPE_OR -> 4.9F / 16.0F;
            case TYPE_DELAY -> 5.2F / 16.0F;
            case TYPE_JUMPER -> 4.3F / 16.0F;
            case TYPE_NOT -> 5.6F / 16.0F;
            case TYPE_TIMER -> 6.4F / 16.0F;
            case TYPE_LOGIC -> 5.4F / 16.0F;
            default -> 4.4F / 16.0F;
        };
    }

    /**
     * 元件在板子空间里的立体小方块，最多返回 3 块（比如开关下面一块、上面一根小杆）。
     * 返回值是块数，坐标写进 out[i] = {x0,y0,z0,x1,y1,z1}。
     */
    public static int componentBoxes(int cell, int value, float[][] out) {
        int type = type(value);
        int dir = facing(value);
        float cx = cellCentreX(cell);
        float cz = cellCentreZ(cell);
        float half = CELL_SIZE * 0.5F;
        float y0 = PLATE_TOP;
        float top = partHeight(type);
        float inset = 0.7F / 16.0F;
        boolean alongZ = dir == 0 || dir == 2;

        switch (type) {
            case TYPE_RESISTOR, TYPE_JUMPER -> {
                float across = half * 0.42F;
                float length = half - inset;
                if (alongZ) {
                    out[0] = box(cx - across, y0, cz - length, cx + across, top, cz + length);
                } else {
                    out[0] = box(cx - length, y0, cz - across, cx + length, top, cz + across);
                }
                if (type == TYPE_JUMPER) {
                    // 跨线桥：两侧各加一个小墩子，看起来像架起来的
                    float leg = half * 0.75F;
                    float legW = half * 0.22F;
                    if (alongZ) {
                        out[1] = box(cx - legW, y0, cz - leg, cx + legW, top + 0.5F / 16.0F, cz - leg + legW * 2);
                        out[2] = box(cx - legW, y0, cz + leg - legW * 2, cx + legW, top + 0.5F / 16.0F, cz + leg);
                    } else {
                        out[1] = box(cx - leg, y0, cz - legW, cx - leg + legW * 2, top + 0.5F / 16.0F, cz + legW);
                        out[2] = box(cx + leg - legW * 2, y0, cz - legW, cx + leg, top + 0.5F / 16.0F, cz + legW);
                    }
                    return 3;
                }
                return 1;
            }
            case TYPE_SWITCH -> {
                float body = half * 0.62F;
                out[0] = box(cx - body, y0, cz - body, cx + body, top, cz + body);
                // 上面一根小拨杆，开着的时候偏向一边
                float lever = half * 0.2F;
                float shift = switchOn(value) ? half * 0.3F : -half * 0.3F;
                out[1] = box(cx - lever + shift, top - 0.2F / 16.0F, cz - lever,
                        cx + lever + shift, top + 1.2F / 16.0F, cz + lever);
                return 2;
            }
            case TYPE_AND, TYPE_OR, TYPE_DELAY -> {
                float body = half * 0.78F;
                out[0] = box(cx - body, y0, cz - body, cx + body, top, cz + body);
                // 上面一道小缺口，标出电流往哪边走
                float mark = half * 0.3F;
                float off = half * 0.42F;
                if (alongZ) {
                    float z = cz + (dir == 0 ? -off : off);
                    out[1] = box(cx - mark, top, z - mark * 0.5F, cx + mark, top + 0.6F / 16.0F, z + mark * 0.5F);
                } else {
                    float x = cx + (dir == 3 ? -off : off);
                    out[1] = box(x - mark * 0.5F, top, cz - mark, x + mark * 0.5F, top + 0.6F / 16.0F, cz + mark);
                }
                return 2;
            }
            case TYPE_CAPACITOR -> {
                float body = half * 0.62F;
                float across = half * 0.3F;
                if (alongZ) {
                    out[0] = box(cx - across, y0, cz - body, cx + across, top, cz + body);
                } else {
                    out[0] = box(cx - body, y0, cz - across, cx + body, top, cz + across);
                }
                return 1;
            }
            case TYPE_DIODE -> {
                float body = half * 0.55F;
                float across = half * 0.55F;
                if (alongZ) {
                    out[0] = box(cx - across, y0, cz - body, cx + across, top, cz + body);
                } else {
                    out[0] = box(cx - body, y0, cz - across, cx + body, top, cz + across);
                }
                return 1;
            }
            case TYPE_TRANSISTOR -> {
                float body = half * 0.72F;
                out[0] = box(cx - body, y0, cz - body, cx + body, top, cz + body);
                return 1;
            }
            case TYPE_NOT -> {
                // 非门：一块小芯片，箭头那一侧凸出一个小圆点（反相圈）
                float body = half * 0.66F;
                out[0] = box(cx - body, y0, cz - body, cx + body, top, cz + body);
                float dot = half * 0.2F;
                float off = half * 0.8F;
                if (alongZ) {
                    float z = cz + (dir == 0 ? -off : off);
                    out[1] = box(cx - dot, y0, z - dot, cx + dot, top - 0.6F / 16.0F, z + dot);
                } else {
                    float x = cx + (dir == 3 ? -off : off);
                    out[1] = box(x - dot, y0, cz - dot, x + dot, top - 0.6F / 16.0F, cz + dot);
                }
                return 2;
            }
            case TYPE_LOGIC -> {
                // 逻辑门：一块小芯片，箭头那侧凸出一个小圆点（输出），中间一道小凸筋区分是哪一种门
                float body = half * 0.68F;
                out[0] = box(cx - body, y0, cz - body, cx + body, top, cz + body);
                float dot = half * 0.2F;
                float off = half * 0.82F;
                if (alongZ) {
                    float z = cz + (dir == 0 ? -off : off);
                    out[1] = box(cx - dot, y0, z - dot, cx + dot, top - 0.5F / 16.0F, z + dot);
                } else {
                    float x = cx + (dir == 3 ? -off : off);
                    out[1] = box(x - dot, y0, cz - dot, x + dot, top - 0.5F / 16.0F, cz + dot);
                }
                float ridge = half * 0.5F;
                float wide = half * 0.22F;
                out[2] = alongZ
                        ? box(cx - wide, top, cz - ridge, cx + wide, top + 0.5F / 16.0F, cz + ridge)
                        : box(cx - ridge, top, cz - wide, cx + ridge, top + 0.5F / 16.0F, cz + wide);
                return 3;
            }
            case TYPE_TIMER -> {
                // 计时器：小方块底座 + 上面一个圆表盘 + 一道指向出口的小指针
                float body = half * 0.7F;
                out[0] = box(cx - body, y0, cz - body, cx + body, top - 1.4F / 16.0F, cz + body);
                float dial = half * 0.34F;
                out[1] = box(cx - dial, top - 1.4F / 16.0F, cz - dial,
                        cx + dial, top, cz + dial);
                float needle = half * 0.12F;
                float reach = half * 0.85F;
                if (alongZ) {
                    float z = cz + (dir == 0 ? -reach : reach);
                    out[2] = box(cx - needle, top - 0.3F / 16.0F, Math.min(cz, z),
                            cx + needle, top + 0.4F / 16.0F, Math.max(cz, z));
                } else {
                    float x = cx + (dir == 3 ? -reach : reach);
                    out[2] = box(Math.min(cx, x), top - 0.3F / 16.0F, cz - needle,
                            Math.max(cx, x), top + 0.4F / 16.0F, cz + needle);
                }
                return 3;
            }
            default -> {
                float body = half * 0.6F;
                out[0] = box(cx - body, y0, cz - body, cx + body, top, cz + body);
                return 1;
            }
        }
    }

    private static float[] box(float x0, float y0, float z0, float x1, float y1, float z1) {
        return new float[]{x0, y0, z0, x1, y1, z1};
    }
}
