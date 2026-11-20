package cn.blockforge.generated.circuitboard.client;

import cn.blockforge.generated.circuitboard.BoardData;
import cn.blockforge.generated.circuitboard.CableColors;
import cn.blockforge.generated.circuitboard.BoardPart;
import cn.blockforge.generated.circuitboard.CircuitBoardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 给电路板画针脚、零件、外圈铜边和电线。
 *
 * <p>板子是 2x2 个方块，而且可以平着放也可以竖着贴在墙上，所以这里所有几何都是先在「板子自己的坐标」
 * 里算好，再用 {@link BoardData#boxToWorld} 按板子的朝向转到世界里去。每一块只画自己那一份：
 * 靠外的两条边画铜边和针脚，自己的那一角画零件，电线由主方块统一画。</p>
 *
 * <p>界面里点了「要删」的那根线会画成红色，看得很清楚自己在删哪一根；平时每根线按
 * {@link CableColors} 上不同的颜色，一根针脚上接了好几根也分得清。</p>
 */
public class BoardRenderer implements BlockEntityRenderer<CircuitBoardBlockEntity> {
    /**
     * 每帧重复用的一份草稿：方块渲染和界面在同一个线程（渲染线程）上跑，
     * 所以同一个渲染器可以放心复用它，不用每帧新开一堆数组。
     */
    private final BoardRenderState state = new BoardRenderState();
    private static final int COLOR_PIN = 0xFFD9A227;
    private static final int COLOR_PIN_LIT = 0xFFFF3B30;
    private static final int COLOR_CAPACITOR = 0xFF1565C0;
    private static final int COLOR_DIODE = 0xFF00897B;
    private static final int COLOR_TRANSISTOR = 0xFF455A64;
    private static final int COLOR_RESISTOR = 0xFFBCAAA4;
    private static final int COLOR_SWITCH = 0xFF8D6E63;
    private static final int COLOR_SWITCH_ON = 0xFF9CCC65;
    private static final int COLOR_GATE = 0xFF5C6BC0;
    private static final int COLOR_DELAY = 0xFF7E57C2;
    private static final int COLOR_JUMPER = 0xFFCFD8DC;
    private static final int COLOR_NOT = 0xFFECEFF1;
    private static final int COLOR_TIMER = 0xFFF9A825;
    private static final int COLOR_NAND = 0xFF26A69A;
    private static final int COLOR_NOR = 0xFF7E57C2;
    private static final int COLOR_XOR = 0xFFFF7043;
    private static final int COLOR_XNOR = 0xFFFFB300;
    private static final int COLOR_PART_LIT = 0xFFFF5722;
    /** 界面里点了「要删」的那根线（只有这个状态是红色；平时每根线按 {@link CableColors} 上色）。 */
    private static final int COLOR_WIRE_PENDING = 0xFFFF1744;
    /** 板子外圈的那一圈铜边。 */
    private static final int COLOR_FRAME = 0xFFD9A227;

    public BoardRenderer(BlockEntityRendererProvider.Context context) {
    }

    // 下面这些小数组是画图用的临时草稿纸：每帧都会用到很多次，新开数组会让 GC 一直忙。
    // 1.21.1 是「取完数据当场就画」，画完就还回去，所以这些草稿可以放心复用。
    private final float[] scratchFrom = new float[3];
    private final float[] scratchTo = new float[3];

    @Override
    public void render(CircuitBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BoardRenderState state = this.state;
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // 离得近就画满细节；远处的线只有几个像素宽，少画几边、少分几段（满世界的线一起画时差别很大）
        double distance = cameraPos.distanceToSqr(board.getBlockPos().getX() + 0.5,
                board.getBlockPos().getY() + 0.5, board.getBlockPos().getZ() + 0.5);
        state.detail = distance <= 16.0 * 16.0 ? CableGeometry.DETAIL_FULL
                : distance <= 40.0 * 40.0 ? CableGeometry.DETAIL_MID : CableGeometry.DETAIL_LOW;
        BoardPart part = board.part();
        Direction facing = board.facing();
        state.part = part.index();
        state.facing = facing.ordinal();
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            state.pins[pin] = board.hasPin(pin);
            state.lit[pin] = board.isPinPowered(pin);
        }
        // 直接拷进渲染状态里现成的数组，不每帧新开
        board.copyCellsInto(state.cells);
        board.copyCellPoweredInto(state.cellLit);

        // 电线只有主方块画一次，免得画四遍
        state.wireCount = 0;
        state.flyCount = 0;
        if (part == BoardPart.NW) {
            BlockPos origin = board.getBlockPos();
            BlockPos masterPos = board.masterPos();
            state.ensureWires(board.wires().size());
            state.ensureFlies(board.flies().size());
            int index = 0;
            for (CircuitBoardBlockEntity.WireLink wire : board.wires()) {
                float[] from = BoardData.pinCentre(wire.pin(), scratchFrom);
                BoardData.localToWorld(facing, from[0], from[1], from[2], from);
                BlockPos delta = wire.target().subtract(origin);
                float toX;
                float toY;
                float toZ;
                if (wire.toBoard()) {
                    float[] target = BoardData.pinCentre(wire.targetPin() & 63, scratchTo);
                    CircuitBoardBlockEntity other = board.getLevel() != null
                            && board.getLevel().getBlockEntity(wire.target()) instanceof CircuitBoardBlockEntity o ? o : null;
                    BoardData.localToWorld(other != null ? other.facing() : facing,
                            target[0], target[1], target[2], target);
                    toX = delta.getX() + target[0];
                    toY = delta.getY() + target[1];
                    toZ = delta.getZ() + target[2];
                } else {
                    toX = delta.getX() + 0.5F;
                    toY = delta.getY() + 0.5F;
                    toZ = delta.getZ() + 0.5F;
                    // 接红石粉的时候贴着地面拉过去，看起来才像真的接上了
                    BlockState targetState = board.getLevel() != null
                            ? board.getLevel().getBlockState(wire.target()) : null;
                    if (targetState != null && targetState.is(Blocks.REDSTONE_WIRE)) {
                        toY = delta.getY() + 0.0625F;
                    }
                }
                int base = state.wireCount * 8;
                state.wires[base] = from[0];
                state.wires[base + 1] = from[1];
                state.wires[base + 2] = from[2];
                state.wires[base + 3] = toX;
                state.wires[base + 4] = toY;
                state.wires[base + 5] = toZ;
                state.wires[base + 6] = 0.0F;
                state.wires[base + 7] = wire.toBoard() ? 0.0F : 1.0F;
                state.wireOn[state.wireCount] = board.isWireActive(index);
                state.wirePending[state.wireCount] = WireHighlight.isBoardWirePending(masterPos, index, wire);
                state.wireCount++;
                index++;
            }

            // 飞线也是主方块画一次：从板子这头拱到那头
            int flyIndex = 0;
            for (CircuitBoardBlockEntity.FlyLink fly : board.flies()) {
                float[] from = flyPoint(fly.a(), scratchFrom);
                BoardData.localToWorld(facing, from[0], from[1], from[2], from);
                float[] to = flyPoint(fly.b(), scratchTo);
                BoardData.localToWorld(facing, to[0], to[1], to[2], to);
                int base = state.flyCount * 6;
                state.flies[base] = from[0];
                state.flies[base + 1] = from[1];
                state.flies[base + 2] = from[2];
                state.flies[base + 3] = to[0];
                state.flies[base + 4] = to[1];
                state.flies[base + 5] = to[2];
                state.flyOn[state.flyCount] = board.isFlyPowered(flyIndex);
                state.flyCount++;
                flyIndex++;
            }
        }
        // 五组几何（针脚 / 铜边 / 零件 / 电线 / 飞线）共用同一个渲染类型，一次拿缓冲全部写进去
        draw(state, pose, buffer.getBuffer(CableGeometry.renderType()));
    }

    /** 飞线端点：格子中心、贴着板面稍微高一点（板子自己的坐标）。 */
    private static float[] flyPoint(int cell, float[] out) {
        out[0] = BoardData.cellCentreX(cell);
        out[1] = BoardData.PLATE_TOP + 0.04F;
        out[2] = BoardData.cellCentreZ(cell);
        return out;
    }

    /** 真正把几何写进顶点缓冲。 */
    private static void draw(BoardRenderState state, PoseStack pose, VertexConsumer vertices) {
        BoardPart part = BoardPart.of(state.part);
        Direction facing = Direction.values()[Math.max(0, Math.min(5, state.facing))];
        // 这一块在板子里的位置：所有几何先在板子坐标里算，最后减掉它再按朝向转
        float ox = part.dx();
        float oz = part.dz();

        boolean anyPin = false;
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (state.pins[pin]) {
                anyPin = true;
                break;
            }
        }
        if (anyPin) {
            {
            PoseStack.Pose pose2 = pose.last();
                float[] local = new float[6];
                float[] world = new float[6];
                for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
                    if (!state.pins[pin]) {
                        continue;
                    }
                    int color = state.lit[pin] ? COLOR_PIN_LIT : COLOR_PIN;
                    BoardData.pinBox(pin, local);
                    shift(local, ox, oz);
                    BoardData.boxToWorld(facing, local, world);
                    CableGeometry.cuboid(vertices, pose2,
                            world[0], world[1], world[2], world[3], world[4], world[5], color);
                }
            }
        }

        // 板子自己那一圈铜边（原来在方块模型里，现在模型只管薄板，边框画在这里才跟得上朝向）
        {
            PoseStack.Pose pose2 = pose.last();
            float[] local = new float[6];
            float[] world = new float[6];
            float edge = 1.0F / 16.0F;
            float far = BoardData.BOARD_BLOCKS;
            float y0 = BoardData.PLATE_TOP;
            float y1 = y0 + edge;
            for (int side = 0; side < 4; side++) {
                if (!part.outer(side)) {
                    continue;
                }
                switch (side) {
                    case 0 -> {
                        local[0] = part.dx();
                        local[2] = 0.0F;
                        local[3] = part.dx() + 1;
                        local[5] = edge;
                    }
                    case 2 -> {
                        local[0] = part.dx();
                        local[2] = far - edge;
                        local[3] = part.dx() + 1;
                        local[5] = far;
                    }
                    case 1 -> {
                        local[0] = far - edge;
                        local[2] = part.dz();
                        local[3] = far;
                        local[5] = part.dz() + 1;
                    }
                    default -> {
                        local[0] = 0.0F;
                        local[2] = part.dz();
                        local[3] = edge;
                        local[5] = part.dz() + 1;
                    }
                }
                local[1] = y0;
                local[4] = y1;
                shift(local, ox, oz);
                BoardData.boxToWorld(facing, local, world);
                CableGeometry.cuboid(vertices, pose2,
                        world[0], world[1], world[2], world[3], world[4], world[5], COLOR_FRAME);
            }
        }

        boolean anyPart = false;
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            if (BoardData.isComponent(BoardData.type(BoardData.get(state.cells, cell)))) {
                anyPart = true;
                break;
            }
        }
        if (anyPart) {
            {
            PoseStack.Pose pose2 = pose.last();
                // 注意：这个画图的回调是晚一帧才真的跑的，所以草稿数组必须是这里自己新开的，
                // 不能跟别的板子共用（否则所有零件都会画成最后一块的样子）
                float[][] boxes = {new float[6], new float[6], new float[6]};
                float[] world = new float[6];
                for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
                    if (BoardData.colOf(cell) / 8 != part.dx() || BoardData.rowOf(cell) / 8 != part.dz()) {
                        continue;
                    }
                    int value = BoardData.get(state.cells, cell);
                    int type = BoardData.type(value);
                    if (!BoardData.isComponent(type)) {
                        continue;
                    }
                    int color = state.cellLit[cell] ? COLOR_PART_LIT : partColor(type, value);
                    int count = BoardData.componentBoxes(cell, value, boxes);
                    for (int i = 0; i < count; i++) {
                        float[] box = boxes[i];
                        shift(box, ox, oz);
                        BoardData.boxToWorld(facing, box, world);
                        CableGeometry.cuboid(vertices, pose2,
                                world[0], world[1], world[2], world[3], world[4], world[5], color);
                    }
                }
            }
        }

        if (state.wireCount > 0) {
            {
            PoseStack.Pose pose2 = pose.last();
                for (int i = 0; i < state.wireCount; i++) {
                    int base = i * 8;
                    float ax = state.wires[base];
                    float ay = state.wires[base + 1];
                    float az = state.wires[base + 2];
                    float bx = state.wires[base + 3];
                    float by = state.wires[base + 4];
                    float bz = state.wires[base + 5];
                    // 每根线一个颜色：同一根针脚上接了好几根线也分得清
                    int color = state.wirePending[i] ? COLOR_WIRE_PENDING
                            : state.wireOn[i] ? CableColors.lit(i) : CableColors.of(i);
                    float dx = bx - ax;
                    float dy = by - ay;
                    float dz = bz - az;
                    float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (length < 1.0E-4F) {
                        continue;
                    }
                    int detail = state.detail;
                    // 中间往下垂一点点，看起来像一根真的铜线
                    CableGeometry.hangingCable(vertices, pose2, ax, ay, az, bx, by, bz, color, detail);
                    // 两头的铜接头：板子这一头一定画；另一头离得远就不画了（那几个像素看不出来）
                    CableGeometry.connector(vertices, pose2, ax, ay, az, color,
                            detail >= CableGeometry.DETAIL_MID ? 6 : 4);
                    if (state.wires[base + 7] > 0.5F && detail == CableGeometry.DETAIL_FULL) {
                        CableGeometry.connector(vertices, pose2, bx, by, bz, color, 6);
                    }
                }
            }
        }

        if (state.flyCount > 0) {
            {
            PoseStack.Pose pose2 = pose.last();
                for (int i = 0; i < state.flyCount; i++) {
                    int base = i * 6;
                    float ax = state.flies[base];
                    float ay = state.flies[base + 1];
                    float az = state.flies[base + 2];
                    float bx = state.flies[base + 3];
                    float by = state.flies[base + 4];
                    float bz = state.flies[base + 5];
                    int color = state.flyOn[i] ? CableColors.lit(i) : CableColors.of(i);
                    int detail = state.detail;
                    CableGeometry.archedCable(vertices, pose2, ax, ay, az, bx, by, bz, color, detail);
                    // 飞线两头都钉在板子上，离得远就把两颗小铜珠一起省掉
                    if (detail >= CableGeometry.DETAIL_MID) {
                        CableGeometry.connector(vertices, pose2, ax, ay, az, color, 6);
                        CableGeometry.connector(vertices, pose2, bx, by, bz, color, 6);
                    }
                }
            }
        }
    }

    /** 把板子坐标的立方体挪到「以当前方块为原点」的位置。 */
    private static void shift(float[] box, float ox, float oz) {
        box[0] -= ox;
        box[3] -= ox;
        box[2] -= oz;
        box[5] -= oz;
    }

    private static int partColor(int type, int value) {
        return switch (type) {
            case BoardData.TYPE_CAPACITOR -> COLOR_CAPACITOR;
            case BoardData.TYPE_DIODE -> COLOR_DIODE;
            case BoardData.TYPE_TRANSISTOR -> COLOR_TRANSISTOR;
            case BoardData.TYPE_RESISTOR -> COLOR_RESISTOR;
            case BoardData.TYPE_SWITCH -> BoardData.switchOn(value) ? COLOR_SWITCH_ON : COLOR_SWITCH;
            case BoardData.TYPE_AND, BoardData.TYPE_OR -> COLOR_GATE;
            case BoardData.TYPE_DELAY -> COLOR_DELAY;
            case BoardData.TYPE_JUMPER -> COLOR_JUMPER;
            case BoardData.TYPE_NOT -> COLOR_NOT;
            case BoardData.TYPE_TIMER -> COLOR_TIMER;
            case BoardData.TYPE_LOGIC -> switch (BoardData.logicKind(value)) {
                case BoardData.LOGIC_NAND -> COLOR_NAND;
                case BoardData.LOGIC_NOR -> COLOR_NOR;
                case BoardData.LOGIC_XOR -> COLOR_XOR;
                default -> COLOR_XNOR;
            };
            default -> COLOR_PIN;
        };
    }
}
