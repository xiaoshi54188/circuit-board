package cn.blockforge.generated.circuitboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 电路板方块实体（整块 2x2 板子只有 NW 那一块真正算数，另外三块只负责显示）。
 *
 * <p>每一 tick 把「贴着边的方块红石信号、拼在一起的另一块板、电线连到的东西」送进面板里，
 * 按画好的路线流一遍，算出哪些针脚要对外输出红石信号。</p>
 *
 * <p>针脚是「又进又出」的：同一个针脚既可以当输入，也可以当输出。这里的判断办法很简单、也不会自锁：</p>
 * <ul>
 *     <li><b>外面有电的针脚只当输入</b>，绝不往回送电（所以拉杆关掉以后不会把电一直顶在那里）；</li>
 *     <li><b>外面没电、但线路通了的针脚就是输出</b>；</li>
 *     <li>板子<b>自己正在送电的那些方块</b>，这一 tick 读回来的电一律不算外部输入——不然板子会把
 *         自己的输出当成输入，于是「输了电就关不掉」或者「一直闪」。</li>
 * </ul>
 *
 * <p>零件规则：</p>
 * <ul>
 *     <li>线路：四个方向都能走；</li>
 *     <li>电容：只朝箭头那一面通电，断电后还会放一小会儿电（保持 {@link #CAPACITOR_HOLD_TICKS} tick）；</li>
 *     <li>二极管：只朝箭头那一面通电，反方向完全不通；</li>
 *     <li>非门：箭头那一面一直有电，箭头反面的那一格一通电它就断电（像红石火把反相）；</li>
 *     <li>三极管：两侧任意一边有电时才导通，用一路电控制另一路；</li>
 *     <li>电阻：只有两个引脚，只能直着穿过，不能拐弯；</li>
 *     <li>开关：自己就是电源，右键切换开 / 关；</li>
 *     <li>与门：两侧都有电才通；或门：侧边任意一边有电就通；</li>
 *     <li>延迟器：电流进来后等几 tick 才从箭头方向输出；</li>
 *     <li>计时器：<b>收到信号才开始打钟</b>——箭头背面那一格有电（针脚送进来的），它才一闪一闪地出电；
 *         信号断电就停；出口在箭头那边，周期可以选；</li>
 *     <li>跨线桥：只沿自身轴线双向导通，横着的线不会短路；</li>
 *     <li>飞线：不是插在格子里的零件，而是拿「飞线」在面板上点两格，两格之间直接拉一根铜线，
 *         不用走中间那些格子，两头都能当电源（双向）。跟拿在手里的跳线是一个用法。</li>
 * </ul>
 */
public class CircuitBoardBlockEntity extends BlockEntity {
    /** 飞线动作：拉一根新的。 */
    public static final int FLY_ADD = 0;
    /** 飞线动作：拆掉连着这一格的那根。 */
    public static final int FLY_REMOVE = 1;
    /** 电容充满一次能撑多少 tick（1 秒 = 20 tick）。 */
    public static final int CAPACITOR_HOLD_TICKS = 20;
    /** 延迟器要等几 tick 才导通。 */
    public static final int DELAY_TICKS = 4;
    /** 计时器的四档周期：亮多久就灭多久（tick）。 */
    public static final int[] TIMER_HALF_TICKS = {5, 10, 20, 40};
    /** 电线最长能接多远（方块）。 */
    public static final int MAX_WIRE_DISTANCE = 32;
    /** 顺着连在一起的红石粉检查「是不是板子自己在供电」时，最多走多少格。 */
    private static final int MAX_DUST_WALK = 64;
    /** 带控制端的零件让电流多绕几圈的收敛上限（每多转一圈就再算一遍）。 */
    private static final int MAX_PROPAGATE_ROUNDS = 16;
    /** 另一半迟迟不出现，等这么多 tick 就自己消失（防止爆炸炸剩半块板）。 */
    private static final int ORPHAN_GRACE_TICKS = 100;

    /**
     * 一根电线：从本板的 pin 出发，连到 target。
     *
     * <p>kind 说明 target 是什么：</p>
     * <ul>
     *     <li>{@link #KIND_READ}：一个普通方块，只读它的红石信号（旁边没空位装端子时用）；</li>
     *     <li>{@link #KIND_BOARD}：另一块电路板，用 targetPin 指针脚，两边互相传信号；</li>
     *     <li>{@link #KIND_TERMINAL}：接线端子，板子从这里把电真正送进旁边的方块（红石灯、门、红石粉都行）。</li>
     * </ul>
     */
    public record WireLink(int pin, BlockPos target, int targetPin, int kind) {
        public boolean toBoard() {
            return kind == KIND_BOARD;
        }

        public boolean toTerminal() {
            return kind == KIND_TERMINAL;
        }
    }

    /**
     * 一条飞线：板内第 a 格和第 b 格之间拉了一根铜线。
     *
     * <p>飞线不走中间那些格子，所以它能把板子两头远远地接起来，也不会和路过的线路打架。
     * 两头都能当电源：哪一头有电，另一头就跟着有电。</p>
     */
    public record FlyLink(int a, int b) {
        public boolean touches(int cell) {
            return a == cell || b == cell;
        }

        public int other(int cell) {
            return a == cell ? b : a;
        }
    }

    /** 只读一个方块的红石信号。 */
    public static final int KIND_READ = 0;
    /** 接到另一块板的针脚上。 */
    public static final int KIND_BOARD = 1;
    /** 接一个接线端子，由端子把电送进旁边的方块。 */
    public static final int KIND_TERMINAL = 2;

    private final BoardPart part;
    /** 板面朝哪一边（平着放是 UP / DOWN，竖着贴在墙上就是那面墙的反方向）。 */
    private final Direction facing;
    private final int[] cells = BoardData.emptyCells();
    /** 这一根针脚现在是不是在往外送电（只有「板子自己驱动」的针脚才算）。 */
    private final boolean[] pinOutput = new boolean[BoardData.PIN_COUNT];
    /** 这一根针脚现在是不是从外面收到电。 */
    private final boolean[] pinInput = new boolean[BoardData.PIN_COUNT];
    /** 整块板每一面有没有在输出的针脚（给 getSignal 用）。 */
    private final boolean[] boardSidePowered = new boolean[4];
    private final List<WireLink> wires = new ArrayList<>();
    /** 板子里拉着的飞线：两格之间一根铜线，双向直连。 */
    private final List<FlyLink> flies = new ArrayList<>();
    /** 电容剩下的电（tick）。 */
    private final int[] charge = new int[BoardData.CELL_COUNT];
    /** 延迟器的计时。 */
    private final int[] delayTimer = new int[BoardData.CELL_COUNT];
    /** 计时器这一 tick 是不是在放行（亮的那一半）。 */
    private final boolean[] timerOn = new boolean[BoardData.CELL_COUNT];
    /** 计时器走到周期的第几 tick。 */
    private final int[] timerPhase = new int[BoardData.CELL_COUNT];
    /** 上一次算完的结果：每个格子通不通电（渲染元件发光要用）。 */
    private final boolean[] cellPowered = new boolean[BoardData.CELL_COUNT];
    /** 算导通时的草稿数组，两趟计算各用一对（每 tick 都要用，别反复新开）。 */
    private final boolean[] solveBufA = new boolean[BoardData.CELL_COUNT];
    private final boolean[] solveBufB = new boolean[BoardData.CELL_COUNT];
    private final boolean[] solveBufC = new boolean[BoardData.CELL_COUNT];
    private final boolean[] solveBufD = new boolean[BoardData.CELL_COUNT];
    /** 算导通时走的队列，也是反复用的。 */
    private final ArrayDeque<Integer> solveQueue = new ArrayDeque<>();
    /**
     * 算电时用的几个草稿数组，全都反复用、不每 tick 新开。
     * 带计时器的板子每 tick 都要完整算一遍，一 tick 新开七八个 boolean[] 就是在给垃圾回收打工。
     */
    private final boolean[] selfOnBuf = new boolean[BoardData.CELL_COUNT];
    private final boolean[] seedsBuf = new boolean[BoardData.CELL_COUNT];
    private final boolean[] outBuf = new boolean[BoardData.PIN_COUNT];
    private final boolean[] sideBuf = new boolean[4];
    private final boolean[] pinChangedBuf = new boolean[BoardData.PIN_COUNT];
    /** 这一 tick 读到的输入；和 {@link #lastInput} 轮流当草稿（这样才比得出来「有没有变」）。 */
    private boolean[] inputBuf = new boolean[BoardData.PIN_COUNT];
    /** 读输入时「这条红石粉是不是板子自己在供电」的小本子，每 tick 清一次反复用。 */
    private final Map<Long, Boolean> dustCache = new HashMap<>();
    /** 「板子正在往外送电的那些方块」的草稿表，够用就一直用这一张。 */
    private long[] drivenBuf = new long[BoardData.PIN_COUNT * 2 + 16];
    /** {@link #drivenBuf} 这一 tick 实际填了几格（表是复用的，尾巴上留着上一轮的旧值）。 */
    private int drivenCount;
    private int orphanTicks;

    /** 板号（1、2、3……），0 表示还没分配。 */
    private int number;
    /** 这块板已经被复制过几份。 */
    private int copyCount;

    /** 上一 tick 每个针脚读到的外部信号，用来判断「没变化就跳过计算」。 */
    private boolean[] lastInput;
    /** 面板数据的版本号，改一格就 +1。 */
    private int cellsVersion;
    private int solvedVersion = -1;

    public CircuitBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.BOARD_BLOCK_ENTITY.get(), pos, state);
        this.part = state.is(ModRegistry.CIRCUIT_BOARD.get()) ? state.getValue(CircuitBoardBlock.PART) : BoardPart.NW;
        this.facing = state.is(ModRegistry.CIRCUIT_BOARD.get())
                ? state.getValue(CircuitBoardBlock.FACING) : Direction.UP;
    }

    /** 板面朝哪一边（渲染和接线算方向都要用）。 */
    public Direction facing() {
        return facing;
    }

    // ------------------------------------------------------------------ 板子的四块

    public BoardPart part() {
        return part;
    }

    public boolean isMaster() {
        return part == BoardPart.NW;
    }

    public BlockPos masterPos() {
        return part.masterPos(worldPosition, facing);
    }

    /** 主方块那一块（自己就是主方块时返回自己）。 */
    public CircuitBoardBlockEntity master() {
        if (isMaster()) {
            return this;
        }
        if (level == null) {
            return null;
        }
        BlockPos pos = masterPos();
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof CircuitBoardBlockEntity other && other.isMaster()
                ? other : null;
    }

    private int[] dataCells() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.cells : cells;
    }

    private boolean[] dataPins() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.pinOutput : pinOutput;
    }

    private boolean[] dataCellPowered() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.cellPowered : cellPowered;
    }

    private boolean[] dataSides() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.boardSidePowered : boardSidePowered;
    }

    // ------------------------------------------------------------------ 板号 / 复制

    public int number() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.number : number;
    }

    public int copyCount() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.copyCount : copyCount;
    }

    /** 这块板的显示名：3号电路板 / 3号电路板复制品2。 */
    public Component displayName() {
        return Component.translatable("gui.circuit_board.board_name", number());
    }

    /** 拿掉主方块上存的面板数据（复制 / 掉落用）。 */
    public int[] copyCells() {
        return dataCells().clone();
    }

    /** 渲染用：把面板数据直接拷进现成的数组里，省得每帧都新开一个。 */
    public void copyCellsInto(int[] out) {
        System.arraycopy(dataCells(), 0, out, 0, BoardData.INT_COUNT);
    }

    /** 渲染用：同上，这个格子通没通电。 */
    public void copyCellPoweredInto(boolean[] out) {
        System.arraycopy(dataCellPowered(), 0, out, 0, BoardData.CELL_COUNT);
    }

    /** 面板上的飞线（复制 / 掉落 / 渲染都用它，格式是每两个 int 一条：a、b）。 */
    public int[] copyFlies() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.encodeFlies() : encodeFlies();
    }

    /** 把另一块板的面板、飞线、板号、复制次数都搬过来（放下复制品时用）。 */
    public void applyDesign(int boardNumber, int copies, int[] sourceCells, int[] sourceFlies) {
        CircuitBoardBlockEntity m = master();
        if (m == null) {
            return;
        }
        if (sourceCells != null && sourceCells.length == BoardData.INT_COUNT) {
            System.arraycopy(sourceCells, 0, m.cells, 0, BoardData.INT_COUNT);
        }
        m.decodeFlies(sourceFlies);
        m.number = boardNumber;
        m.copyCount = Math.max(0, copies);
        m.cellsVersion++;
        m.setChanged();
        m.markClientDirty();
    }

    /** 记下一次复制：返回这一份复制品的编号（1、2、3……）。 */
    public int nextCopyNumber() {
        CircuitBoardBlockEntity m = master();
        if (m == null) {
            return 1;
        }
        m.copyCount++;
        m.setChanged();
        m.markClientDirty();
        return m.copyCount;
    }

    /** 老存档 / 指令放下的板子还没有号，这里补一个。 */
    public void ensureNumber() {
        CircuitBoardBlockEntity m = master();
        if (m != null && m.number <= 0 && level != null) {
            m.number = BoardNumbers.take(level);
            m.setChanged();
            m.markClientDirty();
        }
    }

    public boolean[] copyPins() {
        return dataPins().clone();
    }

    public boolean[] copyCellPowered() {
        return dataCellPowered().clone();
    }

    public void readCells(int[] source) {
        if (source != null && source.length == BoardData.INT_COUNT) {
            CircuitBoardBlockEntity m = master();
            if (m != null) {
                System.arraycopy(source, 0, m.cells, 0, BoardData.INT_COUNT);
                m.cellsVersion++;
                m.setChanged();
            }
        }
    }

    public List<WireLink> wires() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.wires : wires;
    }

    /** 板子上的飞线（渲染和界面画线用）。 */
    public List<FlyLink> flies() {
        CircuitBoardBlockEntity m = master();
        return m != null ? m.flies : flies;
    }

    /** 这条飞线现在通不通电（两头任意一头有电就算，铜线整根都会亮）。 */
    public boolean isFlyPowered(int index) {
        List<FlyLink> list = flies();
        if (index < 0 || index >= list.size()) {
            return false;
        }
        FlyLink fly = list.get(index);
        return isCellPowered(fly.a()) || isCellPowered(fly.b());
    }

    /** 这个格子通不通电（渲染元件发光用）。 */
    public boolean isCellPowered(int cell) {
        return dataCellPowered()[cell];
    }

    /** 这个针脚长没长出来（格子不是空的，而且这根针脚归本方块管）。 */
    public boolean hasPin(int pin) {
        return part.owns(pin) && hasPinAll(pin);
    }

    /** 这根针脚长没长出来（不管它属于板子的哪一块）。 */
    public boolean hasPinAll(int pin) {
        return BoardData.type(BoardData.get(dataCells(), BoardData.pinCell(pin))) != BoardData.TYPE_EMPTY;
    }

    /** 这根针脚现在是不是在往外送电。 */
    public boolean isPinPowered(int pin) {
        return dataPins()[pin & 63];
    }

    /** 这根针脚现在是不是正在从外面收电。 */
    public boolean isPinInput(int pin) {
        CircuitBoardBlockEntity m = master();
        return (m != null ? m.pinInput : pinInput)[pin & 63];
    }

    /** 这个方块某一面要不要对外输出红石信号。 */
    public int outputSignal(int side) {
        if (!part.outer(side)) {
            return 0;
        }
        CircuitBoardBlockEntity m = master();
        if (m == null || !m.boardSidePowered[side]) {
            return 0;
        }
        for (int along = 0; along < BoardData.SIZE; along++) {
            int pin = BoardData.pin(side, along);
            if (part.owns(pin) && m.pinOutput[pin]) {
                return 15;
            }
        }
        return 0;
    }

    private boolean isBoard(BlockPos target) {
        return level != null && level.getBlockState(target).is(ModRegistry.CIRCUIT_BOARD.get());
    }

    private CircuitBoardBlockEntity boardAt(BlockPos target) {
        return level != null && level.getBlockEntity(target) instanceof CircuitBoardBlockEntity board
                ? board : null;
    }

    // ------------------------------------------------------------------ 数据读写

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // 数据只存主方块那一块，另外三块是空的
        if (!isMaster()) {
            return;
        }
        output.putIntArray("cells", cells);
        output.putIntArray("wires", encodeWires());
        output.putIntArray("flies", encodeFlies());
        output.putIntArray("charge", encodeCharge());
        output.putInt("number", number);
        output.putInt("copies", copyCount);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int[] loadedCells = input.getIntArray("cells").orElse(null);
        if (loadedCells != null && loadedCells.length == BoardData.INT_COUNT) {
            System.arraycopy(loadedCells, 0, cells, 0, BoardData.INT_COUNT);
        } else if (loadedCells != null && loadedCells.length == BoardData.LEGACY_INT_COUNT) {
            System.arraycopy(BoardData.fromLegacy(loadedCells), 0, cells, 0, BoardData.INT_COUNT);
        }
        decodeWires(input.getIntArray("wires").orElse(null));
        decodeFlies(input.getIntArray("flies").orElse(null));
        decodeCharge(input.getIntArray("charge").orElse(null));
        number = input.getIntOr("number", 0);
        copyCount = input.getIntOr("copies", 0);
        cellsVersion++;
        // 这几项是从服务端同步过来的，客户端渲染要用
        decodePins(input.getIntArray("pins").orElse(null));
        decodePinInput(input.getIntArray("ins").orElse(null));
        decodeCellsOn(input.getIntArray("cells_on").orElse(null));
        int sides = input.getIntOr("sides", 0);
        for (int side = 0; side < 4; side++) {
            boardSidePowered[side] = (sides & (1 << side)) != 0;
        }
    }

    /** 同步给客户端的数据：整块板的面板、针脚、通电状态、电线。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CircuitBoardBlockEntity m = master();
        tag.putIntArray("cells", m != null ? m.cells : cells);
        tag.putIntArray("pins", encodePins(m != null ? m.pinOutput : pinOutput));
        tag.putIntArray("ins", encodePins(m != null ? m.pinInput : pinInput));
        tag.putIntArray("cells_on", encodeCellsOn(m != null ? m.cellPowered : cellPowered));
        tag.putIntArray("wires", m != null ? m.encodeWires() : encodeWires());
        tag.putIntArray("flies", m != null ? m.encodeFlies() : encodeFlies());
        tag.putInt("number", m != null ? m.number : number);
        tag.putInt("copies", m != null ? m.copyCount : copyCount);
        int sides = 0;
        boolean[] sideData = m != null ? m.boardSidePowered : boardSidePowered;
        for (int side = 0; side < 4; side++) {
            if (sideData[side]) {
                sides |= 1 << side;
            }
        }
        tag.putInt("sides", sides);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static int[] encodePins(boolean[] pins) {
        int[] data = new int[2];
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (pins[pin]) {
                data[pin >> 5] |= 1 << (pin & 31);
            }
        }
        return data;
    }

    private void decodePins(int[] data) {
        if (data == null || data.length != 2) {
            return;
        }
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            pinOutput[pin] = (data[pin >> 5] & (1 << (pin & 31))) != 0;
        }
    }

    private void decodePinInput(int[] data) {
        if (data == null || data.length != 2) {
            return;
        }
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            pinInput[pin] = (data[pin >> 5] & (1 << (pin & 31))) != 0;
        }
    }

    /** 每个格子通不通电，压成 8 个 int（1 bit / 格）。 */
    private static int[] encodeCellsOn(boolean[] lit) {
        int[] data = new int[BoardData.CELL_COUNT / 32];
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            if (lit[cell]) {
                data[cell >> 5] |= 1 << (cell & 31);
            }
        }
        return data;
    }

    private void decodeCellsOn(int[] data) {
        if (data == null || data.length != BoardData.CELL_COUNT / 32) {
            return;
        }
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            cellPowered[cell] = (data[cell >> 5] & (1 << (cell & 31))) != 0;
        }
    }

    private int[] encodeWires() {
        int[] data = new int[wires.size() * 6];
        for (int i = 0; i < wires.size(); i++) {
            WireLink wire = wires.get(i);
            int base = i * 6;
            data[base] = wire.pin();
            data[base + 1] = wire.kind();
            data[base + 2] = wire.target().getX();
            data[base + 3] = wire.target().getY();
            data[base + 4] = wire.target().getZ();
            data[base + 5] = wire.targetPin();
        }
        return data;
    }

    private void decodeWires(int[] data) {
        wires.clear();
        if (data == null) {
            return;
        }
        for (int i = 0; i + 5 < data.length; i += 6) {
            wires.add(new WireLink(data[i],
                    new BlockPos(data[i + 2], data[i + 3], data[i + 4]),
                    data[i + 5], data[i + 1]));
        }
    }

    /** 飞线压成 int 数组：每两个 int 一条（a、b 两个格子下标）。 */
    private int[] encodeFlies() {
        int[] data = new int[flies.size() * 2];
        for (int i = 0; i < flies.size(); i++) {
            FlyLink fly = flies.get(i);
            data[i * 2] = fly.a();
            data[i * 2 + 1] = fly.b();
        }
        return data;
    }

    private void decodeFlies(int[] data) {
        flies.clear();
        if (data == null) {
            return;
        }
        for (int i = 0; i + 1 < data.length; i += 2) {
            int a = data[i];
            int b = data[i + 1];
            if (a < 0 || a >= BoardData.CELL_COUNT || b < 0 || b >= BoardData.CELL_COUNT || a == b) {
                continue;
            }
            flies.add(new FlyLink(a, b));
        }
    }

    /**
     * 第 index 根电线上有没有信号（渲染用）。
     *
     * <p>不再单独存一份「每根线亮不亮」的数组：亮不亮其实只看它那根针脚是在往外送电还是在收电，
     * 这两个状态本来就同步给客户端了，所以线多到几百根也不会多占地方。</p>
     */
    public boolean isWireActive(int index) {
        CircuitBoardBlockEntity m = master();
        if (m == null) {
            m = this;
        }
        if (index < 0 || index >= m.wires.size()) {
            return false;
        }
        int pin = m.wires.get(index).pin() & 63;
        return m.pinOutput[pin] || m.pinInput[pin];
    }

    /** 电容的电量按「格子下标 + 剩下 tick」成对存，没电的格子不占地方。 */
    private int[] encodeCharge() {
        int count = 0;
        for (int value : charge) {
            if (value > 0) {
                count++;
            }
        }
        int[] data = new int[count * 2];
        int at = 0;
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            if (charge[cell] > 0) {
                data[at++] = cell;
                data[at++] = charge[cell];
            }
        }
        return data;
    }

    private void decodeCharge(int[] data) {
        Arrays.fill(charge, 0);
        if (data == null) {
            return;
        }
        for (int i = 0; i + 1 < data.length; i += 2) {
            int cell = data[i];
            if (cell >= 0 && cell < BoardData.CELL_COUNT) {
                charge[cell] = Math.max(0, data[i + 1]);
            }
        }
    }

    // ------------------------------------------------------------------ 对外操作

    /** 打开界面时把面板数据发到客户端。 */
    public void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BoardSyncPayload(worldPosition, copyCells()));
    }

    /** 某一格元件对应的物品；线路 / 空返回 null。逻辑门按「哪一种门」分开算。 */
    private static Item itemForValue(int value) {
        int type = BoardData.type(value);
        if (type == BoardData.TYPE_LOGIC) {
            return switch (BoardData.logicKind(value)) {
                case BoardData.LOGIC_NAND -> ModRegistry.NAND_GATE.get();
                case BoardData.LOGIC_NOR -> ModRegistry.NOR_GATE.get();
                case BoardData.LOGIC_XOR -> ModRegistry.XOR_GATE.get();
                default -> ModRegistry.XNOR_GATE.get();
            };
        }
        return switch (type) {
            case BoardData.TYPE_CAPACITOR -> ModRegistry.CAPACITOR.get();
            case BoardData.TYPE_DIODE -> ModRegistry.DIODE.get();
            case BoardData.TYPE_TRANSISTOR -> ModRegistry.TRANSISTOR.get();
            case BoardData.TYPE_RESISTOR -> ModRegistry.RESISTOR.get();
            case BoardData.TYPE_SWITCH -> ModRegistry.SWITCH.get();
            case BoardData.TYPE_AND -> ModRegistry.AND_GATE.get();
            case BoardData.TYPE_OR -> ModRegistry.OR_GATE.get();
            case BoardData.TYPE_DELAY -> ModRegistry.DELAY.get();
            case BoardData.TYPE_JUMPER -> ModRegistry.JUMPER.get();
            case BoardData.TYPE_NOT -> ModRegistry.NOT_GATE.get();
            case BoardData.TYPE_TIMER -> ModRegistry.TIMER.get();
            default -> null;
        };
    }

    /** 客户端在界面里点了一下格子，服务端在这里校验并写进数据。 */
    public void applyEdit(ServerPlayer player, int index, int value) {
        if (index < 0 || index >= BoardData.CELL_COUNT) {
            return;
        }
        CircuitBoardBlockEntity m = master();
        if (m == null) {
            return;
        }
        if (player.distanceToSqr(worldPosition.getX() + 1.0D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 1.0D) > 64.0D) {
            return;
        }
        int old = BoardData.get(m.cells, index);
        if (old == value) {
            return;
        }
        // 换成另一种元件时要扣对应的零件（纯转向 / 开关开合 / 换周期不扣）
        Item needed = itemForValue(value);
        if (needed != null && needed != itemForValue(old)) {
            if (!player.isCreative() && !consumeItem(player, needed)) {
                player.sendSystemMessage(Component.translatable("msg.circuit_board.need_part",
                        new ItemStack(needed).getHoverName()), true);
                syncTo(player);
                return;
            }
        }
        BoardData.set(m.cells, index, value);
        m.charge[index] = 0;
        m.delayTimer[index] = 0;
        m.cellsVersion++;
        m.setChanged();
        m.markClientDirty();
    }

    /**
     * 客户端在飞线模式里点了两格：action 是 {@link #FLY_ADD} 时把这两格拉成一根线，
     * 是 {@link #FLY_REMOVE} 时把连着 a 那一格的线拆掉。
     *
     * <p>飞线跟拿在手里的跳线一个用法：两头都能当电源，哪一头有电另一头就跟着有电。</p>
     */
    public void applyFly(ServerPlayer player, int a, int b, int action) {
        CircuitBoardBlockEntity m = master();
        if (m == null || level == null) {
            return;
        }
        if (player.distanceToSqr(m.worldPosition.getX() + 1.0D, m.worldPosition.getY() + 0.5D,
                m.worldPosition.getZ() + 1.0D) > 64.0D) {
            return;
        }
        if (action == FLY_REMOVE) {
            int removed = m.removeFlyAt(a);
            player.sendSystemMessage(Component.translatable(removed > 0
                    ? "msg.circuit_board.fly_removed" : "msg.circuit_board.fly_none"), true);
            return;
        }
        if (a < 0 || b < 0 || a >= BoardData.CELL_COUNT || b >= BoardData.CELL_COUNT) {
            return;
        }
        if (a == b) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.fly_same"), true);
            return;
        }
        if (!m.canFlyEnd(a) || !m.canFlyEnd(b)) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.fly_empty"), true);
            return;
        }
        if (m.flyBetween(a, b) != null) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.fly_exists"), true);
            return;
        }
        if (!player.isCreative() && !consumeItem(player, ModRegistry.FLY_WIRE.get())) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.need_part",
                    new ItemStack(ModRegistry.FLY_WIRE.get()).getHoverName()), true);
            return;
        }
        m.flies.add(new FlyLink(Math.min(a, b), Math.max(a, b)));
        m.cellsVersion++;
        m.setChanged();
        m.markClientDirty();
        player.sendSystemMessage(Component.translatable("msg.circuit_board.fly_ok"), true);
    }

    /** 这一格能不能当飞线的一头：得先有线路或者零件，空格子没有电可接。 */
    private boolean canFlyEnd(int cell) {
        return BoardData.type(BoardData.get(cells, cell)) != BoardData.TYPE_EMPTY;
    }

    /** 这两格之间已经有一根飞线了吗。 */
    private FlyLink flyBetween(int a, int b) {
        for (FlyLink fly : flies) {
            if ((fly.a() == a && fly.b() == b) || (fly.a() == b && fly.b() == a)) {
                return fly;
            }
        }
        return null;
    }

    /** 拆掉连着这一格的所有飞线，返回拆了几条。 */
    public int removeFlyAt(int cell) {
        CircuitBoardBlockEntity m = master();
        if (m == null) {
            return 0;
        }
        int before = m.flies.size();
        m.flies.removeIf(fly -> fly.touches(cell));
        int removed = before - m.flies.size();
        if (removed > 0) {
            m.cellsVersion++;
            m.setChanged();
            m.markClientDirty();
        }
        return removed;
    }

    /** 端点格子被擦成空格了，那条飞线就自己收掉。 */
    private void validateFlies() {
        if (flies.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int i = flies.size() - 1; i >= 0; i--) {
            FlyLink fly = flies.get(i);
            if (fly.a() == fly.b() || !canFlyEnd(fly.a()) || !canFlyEnd(fly.b())) {
                flies.remove(i);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            markClientDirty();
        }
    }

    private static boolean consumeItem(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    /**
     * 给某根针脚加一根电线。
     *
     * <p>一根针脚能接多少根线是<b>不封顶</b>的（就像一孔接线柱），所以这里不再先把同针脚的旧线拆掉、
     * 更不会因为「线太多」把最早的那根悄悄扔掉：只有「同一根针脚、同一个目标」才算重复。</p>
     *
     * <p>同一个端子上也能挂好几根针脚：电线可以从别的针脚也接到那个端子上，两根针脚就一起给这一串线送电。</p>
     */
    public void addWire(int pin, BlockPos target, int targetPin, int kind) {
        CircuitBoardBlockEntity m = master();
        if (m == null || pin < 0 || pin >= BoardData.PIN_COUNT) {
            return;
        }
        BlockPos pos = target.immutable();
        for (WireLink wire : m.wires) {
            if (wire.pin() == pin && wire.kind() == kind && wire.targetPin() == targetPin
                    && wire.target().equals(pos)) {
                return;
            }
        }
        m.wires.add(new WireLink(pin, pos, targetPin, kind));
        m.setChanged();
        m.markClientDirty();
        if (kind == KIND_TERMINAL && level != null && !level.isClientSide()) {
            // 刚装上的端子要让旁边的红石灯 / 门重新看一眼有没有电
            level.updateNeighborsAt(pos, ModRegistry.WIRE_TERMINAL.get());
            if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) {
                terminal.requestRefresh();
            }
        }
    }

    /** 一根电线在整块板电线列表里的位置（也就是界面上显示的编号减 1）。 */
    public record WireEntry(int index, WireLink wire) {
    }

    /**
     * 这根针脚上接着的所有电线，带上它们在整块板里的编号。
     * 界面上「#3」这种数字用的是 {@code index + 1}，删线也按这个编号删，和渲染高亮是同一套。
     */
    public List<WireEntry> wireEntriesOf(int pin) {
        List<WireEntry> out = new ArrayList<>(2);
        List<WireLink> all = wires();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).pin() == pin) {
                out.add(new WireEntry(i, all.get(i)));
            }
        }
        return out;
    }

    /** 这根针脚上接着的所有电线（按编号顺序：第 1 根是列表里的第 1 个）。 */
    public List<WireLink> wiresOf(int pin) {
        List<WireLink> out = new ArrayList<>(2);
        for (WireLink wire : wires()) {
            if (wire.pin() == pin) {
                out.add(wire);
            }
        }
        return out;
    }

    /** 这根针脚上接了几根电线。 */
    public int wireCount(int pin) {
        int count = 0;
        for (WireLink wire : wires()) {
            if (wire.pin() == pin) {
                count++;
            }
        }
        return count;
    }

    /** 拆掉第 index 根电线（编号从 0 开始，界面上显示的是 index + 1）。 */
    public boolean removeWireAt(int index) {
        CircuitBoardBlockEntity m = master();
        if (m == null || index < 0 || index >= m.wires.size()) {
            return false;
        }
        m.removeWireInternal(index);
        return true;
    }

    /**
     * 拆掉「这根针脚连到 target」的那根电线（端子面板里从针脚这一头拆线用）。
     * 找不到这样的线就返回 false。
     */
    public boolean removeWireTo(int pin, BlockPos target) {
        CircuitBoardBlockEntity m = master();
        if (m == null) {
            return false;
        }
        for (int i = 0; i < m.wires.size(); i++) {
            WireLink wire = m.wires.get(i);
            if (wire.pin() == pin && wire.target().equals(target)) {
                return m.removeWireAt(i);
            }
        }
        return false;
    }

    public WireLink wireAt(int pin) {
        for (WireLink wire : wires()) {
            if (wire.pin() == pin) {
                return wire;
            }
        }
        return null;
    }

    /**
     * 拆线的时候顺手把这个线头上的端子也收掉。
     *
     * <p>不过端子现在可以有好几个「用户」：可能还挂着跳线、也可能还有另一根针脚接着它。
     * 所以先只把「这根针脚」的接法摘掉，端子上确实一根线都不剩了，才把那个小铜疙瘩挖掉。</p>
     */
    private void dropTerminal(WireLink wire) {
        if (!wire.toTerminal() || level == null || level.isClientSide() || !level.isLoaded(wire.target())) {
            return;
        }
        if (!(level.getBlockEntity(wire.target()) instanceof WireTerminalBlockEntity terminal)) {
            return;
        }
        terminal.removeDrive(masterPos(), wire.pin());
        if (terminal.cableCount() == 0 && terminal.drives().isEmpty()) {
            level.setBlock(wire.target(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** 去掉这个针脚上的第一根电线（另一块板上对应的那根也一起去掉）；返回有没有去掉东西。 */
    public boolean removeWire(int pin) {
        CircuitBoardBlockEntity m = master();
        if (m == null) {
            return false;
        }
        for (int i = 0; i < m.wires.size(); i++) {
            if (m.wires.get(i).pin() == pin) {
                return m.removeWireAt(i);
            }
        }
        return false;
    }

    /** 按住列表下标拆一根线：线头的端子和另一块板上成对的那根一起收掉。 */
    private boolean removeWireInternal(int index) {
        CircuitBoardBlockEntity m = master();
        if (m == null || index < 0 || index >= m.wires.size()) {
            return false;
        }
        WireLink removed = m.wires.remove(index);
        m.dropTerminal(removed);
        if (removed.toBoard()) {
            CircuitBoardBlockEntity other = m.boardAt(removed.target());
            if (other != null) {
                CircuitBoardBlockEntity om = other.master();
                if (om != null) {
                    om.wires.removeIf(w -> w.pin() == removed.targetPin()
                            && w.toBoard() && w.target().equals(m.masterPos()));
                    om.setChanged();
                    om.markClientDirty();
                }
            }
        }
        m.setChanged();
        m.markClientDirty();
        return true;
    }

    /** 板子被拆掉 / 被炸掉的时候，把连在它身上的接线端子一起收掉。 */
    public void removeTerminals() {
        if (level == null || level.isClientSide()) {
            return;
        }
        CircuitBoardBlockEntity m = master();
        if (m == null) {
            return;
        }
        for (WireLink wire : m.wires) {
            m.dropTerminal(wire);
        }
        m.wires.clear();
    }

    /** 每 tick 检查一遍电线：端子被拆了、对面的板没了，就把这根线也收掉。 */
    private void validateWires() {
        if (level == null || wires.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int i = wires.size() - 1; i >= 0; i--) {
            WireLink wire = wires.get(i);
            if (!level.isLoaded(wire.target())) {
                continue;
            }
            if (wire.toTerminal() && !level.getBlockState(wire.target()).is(ModRegistry.WIRE_TERMINAL.get())) {
                wires.remove(i);
                changed = true;
            } else if (wire.toBoard() && !isBoard(wire.target())) {
                wires.remove(i);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            markClientDirty();
        }
    }

    private void markClientDirty() {
        if (level != null && !level.isClientSide()) {
            // 四块都要发一遍，不然另外三块在客户端上拿不到面板数据，针脚和零件就画不出来
            BlockPos masterPos = part.masterPos(worldPosition, facing);
            for (BoardPart other : BoardPart.values()) {
                BlockPos pos = other.partPos(masterPos, facing);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.is(ModRegistry.CIRCUIT_BOARD.get())) {
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 每 tick 算电

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!isMaster()) {
            checkOrphan();
            return;
        }
        if (!siblingsIntact()) {
            removeBoard();
            return;
        }
        if (number <= 0) {
            // 老存档里还没有板号的板子，第一次 ticking 的时候补一个
            number = BoardNumbers.take(level);
            setChanged();
            markClientDirty();
        }
        solve();
    }

    /** 板子缺了一块（被别的方式拆掉、或者没放全）就把整块收掉。 */
    private boolean siblingsIntact() {
        BlockPos master = worldPosition;
        for (BoardPart other : BoardPart.values()) {
            BlockPos pos = other.partPos(master, facing);
            if (!level.isLoaded(pos)) {
                return true;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModRegistry.CIRCUIT_BOARD.get()) || state.getValue(CircuitBoardBlock.PART) != other) {
                return false;
            }
        }
        return true;
    }

    private void checkOrphan() {
        if (master() != null) {
            orphanTicks = 0;
            return;
        }
        BlockPos pos = masterPos();
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        boolean masterThere = level.getBlockState(pos).is(ModRegistry.CIRCUIT_BOARD.get());
        if (masterThere) {
            orphanTicks = 0;
            return;
        }
        orphanTicks++;
        if (orphanTicks > ORPHAN_GRACE_TICKS) {
            level.setBlock(worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void removeBoard() {
        BlockPos master = worldPosition;
        for (BoardPart other : BoardPart.values()) {
            BlockPos pos = other.partPos(master, facing);
            if (level.isLoaded(pos) && level.getBlockState(pos).is(ModRegistry.CIRCUIT_BOARD.get())) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private void solve() {
        validateWires();
        validateFlies();
        // 外面送进来的电（板子自己正在驱动的那些方块已经排除掉了）
        boolean[] input = collectInputs();
        int version = cellsVersion;
        // 计时器先往前走一格：它只有在收到信号（箭头背面那一格有电）的时候才打钟
        boolean timerRunning = updateTimers(input);
        // 两块 buffer 轮着当草稿：这一 tick 的输入留在 lastInput 里，下一 tick 填另一块再比
        boolean[] reusable = lastInput;
        lastInput = input;
        inputBuf = reusable == null ? new boolean[BoardData.PIN_COUNT] : reusable;
        if (!timerRunning && !hasTimedParts()
                && reusable != null && solvedVersion == version && Arrays.equals(input, reusable)) {
            // 外面一点没变、板子上也没有会自己变化的零件：结果肯定和上一 tick 一样
            return;
        }
        solvedVersion = version;

        // 延迟器靠上一 tick 的结果计时，所以先更新计时再看能不能导通
        updateDelay();
        // 非门 / 逻辑门自己就是小电源，输出什么由上一 tick 的邻居状态决定
        boolean[] selfOn = computeSelfGates();

        // 第一遍：只看外面送进来的电，用来判断电容有没有充上
        boolean[] base = propagate(buildSeeds(input, selfOn, false), selfOn, solveBufA, solveBufB);
        boolean chargeChanged = updateCharge(base);

        // 第二遍：正在放电的电容、正在亮的计时器、逻辑门也算小电源
        boolean[] powered = propagate(buildSeeds(input, selfOn, true), selfOn, solveBufC, solveBufD);

        // 针脚输出：线路通了、而且这一根针脚没有从外面收到电，它就是板子自己往外送的
        boolean[] outs = outBuf;
        Arrays.fill(outs, false);
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (!hasPinAll(pin)) {
                continue;
            }
            outs[pin] = powered[BoardData.pinCell(pin)] && !input[pin];
        }

        publish(powered, input, outs);
        if (chargeChanged) {
            setChanged();
        }
    }

    /**
     * 板子上有没有「每 tick 都要自己动」的零件（电容 / 延迟器 / 非门 / 与门 / 或门 / 逻辑门），
     * 有就每 tick 都得重算。非门、与门、或门、逻辑门都要读「上一 tick 旁边那格有没有电」
     * 才能决定自己出不出电，所以也算在内。
     * 计时器不在这里：它在没收到信号的时候是死的，{@link #updateTimers} 会单独说它要不要跑。
     */
    private boolean hasTimedParts() {
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            int type = BoardData.type(BoardData.get(cells, cell));
            if (type == BoardData.TYPE_CAPACITOR || type == BoardData.TYPE_DELAY
                    || type == BoardData.TYPE_NOT || type == BoardData.TYPE_LOGIC
                    || type == BoardData.TYPE_AND || type == BoardData.TYPE_OR) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计时器：<b>收到信号才开始打钟</b>，信号没了就停。
     *
     * <p>输入跟延迟器、非门一样在<b>箭头背面那一格</b>：面板上把线路从针脚接到计时器背后，
     * 或者针脚刚好就长在计时器这一格上，它才「收到针脚信号」。收到了就每 tick 往前走一格，
     * 走到半周期翻个面，于是「亮一段、灭一段」地一直循环；信号一断就立刻停在灭的那一半，
     * 计时清零，下次再来信号还是从「先亮」开始。</p>
     *
     * @return 这一 tick 有没有计时器在跑（或者刚停下）——有就说明板子每 tick 都得重算
     */
    private boolean updateTimers(boolean[] input) {
        boolean running = false;
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            int value = BoardData.get(cells, cell);
            if (BoardData.type(value) != BoardData.TYPE_TIMER) {
                timerOn[cell] = false;
                timerPhase[cell] = 0;
                continue;
            }
            int in = BoardData.neighbourCell(cell, (BoardData.facing(value) + 2) & 3);
            boolean fed = (in >= 0 && cellPowered[in]) || pinFeedsCell(cell, input);
            if (!fed) {
                // 刚断信号的那一 tick 也要算一遍，好把输出关掉、让客户端重新画
                running |= timerOn[cell];
                timerOn[cell] = false;
                timerPhase[cell] = 0;
                continue;
            }
            int half = TIMER_HALF_TICKS[BoardData.timerSpeed(value)];
            timerPhase[cell] = (timerPhase[cell] + 1) % (half * 2);
            timerOn[cell] = timerPhase[cell] < half;
            running = true;
        }
        return running;
    }

    /** 这一格上的针脚有没有从外面把信号送进来（针脚就长在计时器这格上时算「收到针脚信号」）。 */
    private boolean pinFeedsCell(int cell, boolean[] input) {
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (input[pin] && BoardData.pinCell(pin) == cell) {
                return true;
            }
        }
        return false;
    }

    /** 延迟器计时：上一 tick 输入端有电就加一，没有就清零。 */
    private void updateDelay() {
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            int value = BoardData.get(cells, cell);
            if (BoardData.type(value) != BoardData.TYPE_DELAY) {
                delayTimer[cell] = 0;
                continue;
            }
            int in = BoardData.neighbourCell(cell, (BoardData.facing(value) + 2) & 3);
            boolean driven = in >= 0 && cellPowered[in];
            delayTimer[cell] = driven ? Math.min(delayTimer[cell] + 1, DELAY_TICKS) : 0;
        }
    }

    /**
     * 非门 / 与门 / 或门 / 逻辑门：它们自己就是小电源，输出什么取决于「上一 tick」周围有没有电。
     *
     * <ul>
     *     <li>非门：箭头反面的那一格没电，它自己就带电；</li>
     *     <li>与门：箭头左右两侧都有电，箭头那一面才出电；</li>
     *     <li>或门：箭头左右两侧任意一边有电，箭头那一面就出电；</li>
     *     <li>与非 / 或非 / 异或 / 同或：同样看箭头左右两侧，按门的种类算出结果从箭头那一面出电。</li>
     * </ul>
     */
    private boolean[] computeSelfGates() {
        boolean[] selfOn = selfOnBuf;
        // 这张表是复用的：先清一遍，别把「这一格上原来是个门、现在被擦掉了」的旧结果留下来
        Arrays.fill(selfOn, false);
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            int value = BoardData.get(cells, cell);
            int type = BoardData.type(value);
            if (type == BoardData.TYPE_NOT) {
                int in = BoardData.neighbourCell(cell, (BoardData.facing(value) + 2) & 3);
                selfOn[cell] = in < 0 || !cellPowered[in];
            } else if (type == BoardData.TYPE_AND) {
                selfOn[cell] = sidesPoweredCount(cell, value) >= 2;
            } else if (type == BoardData.TYPE_OR) {
                selfOn[cell] = sidesPoweredCount(cell, value) >= 1;
            } else if (type == BoardData.TYPE_LOGIC) {
                selfOn[cell] = logicOutput(cell, value);
            }
        }
        return selfOn;
    }

    /** 箭头左右两侧（垂直于箭头的两格）里有几格通电，0~2。 */
    private int sidesPoweredCount(int cell, int value) {
        int facing = BoardData.facing(value);
        int count = 0;
        if (sidePowered(cell, (facing + 1) & 3)) {
            count++;
        }
        if (sidePowered(cell, (facing + 3) & 3)) {
            count++;
        }
        return count;
    }

    /** 逻辑门的输出：读箭头左右两侧那两个输入格，按门的种类算一个结果。 */
    private boolean logicOutput(int cell, int value) {
        int facing = BoardData.facing(value);
        boolean left = sidePowered(cell, (facing + 1) & 3);
        boolean right = sidePowered(cell, (facing + 3) & 3);
        return switch (BoardData.logicKind(value)) {
            case BoardData.LOGIC_NAND -> !(left && right);
            case BoardData.LOGIC_NOR -> !(left || right);
            case BoardData.LOGIC_XOR -> left != right;
            default -> left == right;
        };
    }

    /** 某一侧相邻的格子这一 tick 有没有电。 */
    private boolean sidePowered(int cell, int dir) {
        int neighbour = BoardData.neighbourCell(cell, dir);
        return neighbour >= 0 && cellPowered[neighbour];
    }

    /** 电容：电流到达时充满，之后每 tick 放掉一点。返回电量有没有变化。 */
    private boolean updateCharge(boolean[] base) {
        boolean changed = false;
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            if (BoardData.type(BoardData.get(cells, cell)) != BoardData.TYPE_CAPACITOR) {
                if (charge[cell] != 0) {
                    charge[cell] = 0;
                    changed = true;
                }
                continue;
            }
            if (base[cell]) {
                if (charge[cell] != CAPACITOR_HOLD_TICKS) {
                    charge[cell] = CAPACITOR_HOLD_TICKS;
                    changed = true;
                }
            } else if (charge[cell] > 0) {
                charge[cell]--;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 起点：开关、正在输出的非门、还在放电的电容、正在打钟的计时器（已经收到信号的那一个），
     * 加上每个「外面有电」的针脚。
     */
    private boolean[] buildSeeds(boolean[] input, boolean[] selfOn, boolean includeCharge) {
        boolean[] seeds = seedsBuf;
        Arrays.fill(seeds, false);
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            int value = BoardData.get(cells, cell);
            int type = BoardData.type(value);
            if (type == BoardData.TYPE_EMPTY) {
                continue;
            }
            if (type == BoardData.TYPE_SWITCH && BoardData.switchOn(value)) {
                seeds[cell] = true;
            } else if ((type == BoardData.TYPE_NOT || type == BoardData.TYPE_LOGIC
                    || type == BoardData.TYPE_AND || type == BoardData.TYPE_OR) && selfOn[cell]) {
                seeds[cell] = true;
            } else if (type == BoardData.TYPE_TIMER && timerOn[cell]) {
                seeds[cell] = true;
            } else if (includeCharge && type == BoardData.TYPE_CAPACITOR && charge[cell] > 0) {
                seeds[cell] = true;
            }
        }
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (!input[pin]) {
                continue;
            }
            int cell = BoardData.pinCell(pin);
            if (BoardData.type(BoardData.get(cells, cell)) != BoardData.TYPE_EMPTY) {
                seeds[cell] = true;
            }
        }
        return seeds;
    }

    /** 这根针脚「戳出去」以后贴着的是哪个方块。 */
    private BlockPos pinNeighbour(int pin) {
        BoardPart owner = BoardPart.of(BoardData.pinPart(pin));
        int side = BoardData.pinSide(pin);
        return owner.partPos(worldPosition, facing).relative(BoardData.localToWorld(facing, side));
    }

    /**
     * 上一 tick 板子正在往外送电的那些方块。
     *
     * <p>读输入的时候要跳过它们：不然板子会把自己送出去的电又当成「外面来的电」，
     * 结果就是拉杆关掉以后输入端还亮着、输出端也一直亮着，或者一直闪。</p>
     */
    private long[] selfDrivenBlocks() {
        // 针脚本身加上「每根电线可能拉到一个端子」，线数不封顶：只有不够用的时候才换一张更大的
        int need = BoardData.PIN_COUNT * 2 + wires().size() * 2;
        if (drivenBuf.length < need) {
            drivenBuf = new long[Math.max(need, drivenBuf.length * 2)];
        }
        long[] out = drivenBuf;
        int count = 0;
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (!pinOutput[pin]) {
                continue;
            }
            count = addDriven(out, count, pinNeighbour(pin).asLong());
        }
        // 电线末端的接线端子：板子正在往它贴着的那个方块送电，那块方块的「电」也是板子自己的
        for (WireLink wire : wires()) {
            if (!wire.toTerminal() || !pinOutput[wire.pin() & 63] || !level.isLoaded(wire.target())) {
                continue;
            }
            if (level.getBlockEntity(wire.target()) instanceof WireTerminalBlockEntity terminal) {
                count = addDriven(out, count, terminal.attachedPos().asLong());
            }
        }
        // 尾巴上留着的是上一轮的旧位置，读的时候一律按 drivenCount 限界，别整段扫
        drivenCount = count;
        return out;
    }

    private static int addDriven(long[] out, int count, long pos) {
        for (int i = 0; i < count; i++) {
            if (out[i] == pos) {
                return count;
            }
        }
        if (count < out.length) {
            out[count] = pos;
            return count + 1;
        }
        return count;
    }

    /** 这个位置在不在「板子正给自己送电」的那几个方块里（只看前 {@link #drivenCount} 个）。 */
    private static boolean wasDriven(long[] driven, int count, BlockPos pos) {
        long key = pos.asLong();
        for (int i = 0; i < count; i++) {
            if (driven[i] == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这个方块是不是「板子自己在给它送电」。
     *
     * <p>红石粉是一整条线：只要这条线上有一格是板子自己在供的电，整条线读回来的电就都靠不住，
     * 所以这里顺着连在一起的红石粉走一圈（最多 {@link #MAX_DUST_WALK} 格），
     * 免得板子把自己的电从 A 格送出去、又从 B 格读回来。</p>
     */
    private boolean selfDriven(BlockPos neighbour, long[] driven, Map<Long, Boolean> dustCache) {
        if (wasDriven(driven, drivenCount, neighbour)) {
            return true;
        }
        if (!level.getBlockState(neighbour).is(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE)) {
            return false;
        }
        Long key = neighbour.asLong();
        Boolean cached = dustCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean result = dustLineDriven(neighbour, driven, dustCache);
        dustCache.put(key, result);
        return result;
    }

    /** 顺着红石粉走一圈，看看这条线上有没有一格是板子自己在驱动的。 */
    private boolean dustLineDriven(BlockPos start, long[] driven, Map<Long, Boolean> dustCache) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        List<Long> line = new ArrayList<>();
        queue.add(start);
        seen.add(start.asLong());
        line.add(start.asLong());
        boolean result = false;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (wasDriven(driven, drivenCount, pos)) {
                result = true;
                break;
            }
            if (seen.size() >= MAX_DUST_WALK) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                long key = next.asLong();
                if (seen.contains(key) || !level.isLoaded(next)) {
                    continue;
                }
                if (!level.getBlockState(next).is(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE)) {
                    continue;
                }
                seen.add(key);
                line.add(key);
                queue.add(next);
            }
        }
        // 同一条线上每一格结论都一样，记下来省得反复走
        for (long key : line) {
            dustCache.put(key, result);
        }
        return result;
    }

    private boolean[] collectInputs() {
        boolean[] input = inputBuf;
        Arrays.fill(input, false);
        long[] driven = selfDrivenBlocks();
        Map<Long, Boolean> dustCache = this.dustCache;
        dustCache.clear();
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (!hasPinAll(pin)) {
                continue;
            }
            int side = BoardData.pinSide(pin);
            BlockPos neighbour = pinNeighbour(pin);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            if (isBoard(neighbour)) {
                CircuitBoardBlockEntity other = boardAt(neighbour);
                if (other != null) {
                    CircuitBoardBlockEntity om = other.master();
                    if (om != null && om != this) {
                        int mirrored = BoardData.pin(BoardData.oppositeSide(side), BoardData.pinAlong(pin));
                        input[pin] = om.pinOutput[mirrored];
                    }
                }
                continue;
            }
            if (selfDriven(neighbour, driven, dustCache)) {
                // 这个方块上的电（或者它那条红石粉上的电）是板子自己送的，不算外面送进来的
                continue;
            }
            // 这里的方向是「从板子指向邻居」（板子竖着放时要按朝向换算），正好是原版 getSignal 要的方向
            Direction outward = BoardData.localToWorld(facing, side);
            if (level.getSignal(neighbour, outward) > 0) {
                input[pin] = true;
            }
        }
        for (WireLink wire : wires()) {
            int pin = wire.pin();
            if (pin < 0 || pin >= BoardData.PIN_COUNT || pinOutput[pin]) {
                // 这根针脚正在往外送电，读回来的东西可能就是自己送的
                continue;
            }
            if (!level.isLoaded(wire.target())) {
                continue;
            }
            if (wire.toBoard()) {
                CircuitBoardBlockEntity other = boardAt(wire.target());
                if (other != null) {
                    CircuitBoardBlockEntity om = other.master();
                    if (om != null && om.pinOutput[wire.targetPin() & 63]) {
                        input[pin] = true;
                    }
                }
            } else if (wire.toTerminal()) {
                // 端子那一头：问「这一串线上有没有外面的电」。
                // 不能直接读端子贴着的那块方块——那块方块的电常常就是这根针脚自己送过去的
                if (level.getBlockEntity(wire.target()) instanceof WireTerminalBlockEntity terminal
                        && terminal.externalPowered()) {
                    input[pin] = true;
                }
            } else if (level.getSignal(wire.target(), directionFromTo(worldPosition, wire.target())) > 0) {
                input[pin] = true;
            }
        }
        return input;
    }

    /** 从 from 指向 to 的近似方向（读某个方块的红石信号时用）。 */
    private static Direction directionFromTo(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) {
            return dy >= 0 ? Direction.UP : Direction.DOWN;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * 算通电：三极管 / 与门 / 或门要靠「控制端有电」才打开，而控制端可能又要靠别的零件导通，
     * 所以这里反复算，直到结果不再变化（最多 {@link #MAX_PROPAGATE_ROUNDS} 圈）。
     *
     * <p>结果写进调用方给的 current / next 两个现成数组里（它俩会轮流当草稿），
     * 不再每一圈都新开两个 boolean[256] —— 带计时器的板子每 tick 都要算，新开数组太浪费。</p>
     */
    private boolean[] propagate(boolean[] seeds, boolean[] selfOn, boolean[] current, boolean[] next) {
        Arrays.fill(current, false);
        for (int round = 0; round < MAX_PROPAGATE_ROUNDS; round++) {
            propagateOnce(seeds, current, selfOn, next);
            if (Arrays.equals(next, current)) {
                return next;
            }
            boolean[] swap = current;
            current = next;
            next = swap;
        }
        return current;
    }

    /** 一趟洪泛：控制端读上一趟的结果（gating）。 */
    private void propagateOnce(boolean[] seeds, boolean[] gating, boolean[] selfOn, boolean[] powered) {
        Arrays.fill(powered, false);
        ArrayDeque<Integer> queue = solveQueue;
        queue.clear();
        for (int cell = 0; cell < BoardData.CELL_COUNT; cell++) {
            if (seeds[cell]) {
                powered[cell] = true;
                queue.add(cell);
            }
        }
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            // 飞线：这一格有电，连着的那一头立刻也有电（中间隔着多少格都一样）
            spreadFlies(cell, powered, queue);
            for (int dir = 0; dir < 4; dir++) {
                if (!canPass(cell, gating, selfOn, dir)) {
                    continue;
                }
                int next = BoardData.neighbourCell(cell, dir);
                if (next < 0 || powered[next]) {
                    continue;
                }
                if (!canPass(next, gating, selfOn, dir)) {
                    continue;
                }
                powered[next] = true;
                queue.add(next);
            }
        }
    }

    /**
     * 飞线导电：cell 有电时，和它连着的那一头也算有电（双向，不看方向、不看中间隔了什么）。
     * 飞线两头都焊在已经有线路的格子上，所以这里不再判断能不能「穿」。
     */
    private void spreadFlies(int cell, boolean[] powered, ArrayDeque<Integer> queue) {
        for (int i = 0; i < flies.size(); i++) {
            FlyLink fly = flies.get(i);
            if (!fly.touches(cell)) {
                continue;
            }
            int other = fly.other(cell);
            if (!powered[other]) {
                powered[other] = true;
                queue.add(other);
            }
        }
    }

    /** 这一格的电流能不能沿着 dir 走（带控制端的零件按上一趟的结果判断）。 */
    private boolean canPass(int cell, boolean[] gating, boolean[] selfOn, int dir) {
        int value = BoardData.get(cells, cell);
        int type = BoardData.type(value);
        if (type == BoardData.TYPE_EMPTY) {
            return false;
        }
        int facing = BoardData.facing(value);
        return switch (type) {
            case BoardData.TYPE_TRACE -> true;
            case BoardData.TYPE_SWITCH -> BoardData.switchOn(value);
            case BoardData.TYPE_DIODE, BoardData.TYPE_CAPACITOR -> facing == dir;
            // 非门 / 与门 / 或门 / 逻辑门不「透电」：只有它自己输出（selfOn）的时候才往箭头那边送电
            case BoardData.TYPE_NOT, BoardData.TYPE_LOGIC, BoardData.TYPE_AND, BoardData.TYPE_OR ->
                    selfOn[cell] && facing == dir;
            // 计时器也不「透电」：收到信号、而且正好是亮的那一半，才朝箭头那边出电
            case BoardData.TYPE_TIMER -> timerOn[cell] && facing == dir;
            case BoardData.TYPE_RESISTOR, BoardData.TYPE_JUMPER ->
                    facing == dir || ((facing + 2) & 3) == dir;
            // 三极管：电流从箭头背面进来，两侧任意一边有电才沿箭头放过去
            case BoardData.TYPE_TRANSISTOR ->
                    facing == dir && sidesPowered(cell, facing, gating, 1);
            case BoardData.TYPE_DELAY -> facing == dir && delayTimer[cell] >= DELAY_TICKS;
            default -> false;
        };
    }

    /** 垂直于 facing 的两侧里有几侧通电（三极管要一侧）。 */
    private boolean sidesPowered(int cell, int facing, boolean[] gating, int required) {
        int count = 0;
        for (int turn = 1; turn <= 3; turn += 2) {
            int control = BoardData.neighbourCell(cell, (facing + turn) & 3);
            if (control >= 0 && gating[control]) {
                count++;
                if (count >= required) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 把算好的结果写进针脚 / 方块面，有变化就通知周围的红石。 */
    private void publish(boolean[] powered, boolean[] input, boolean[] outs) {
        boolean changed = false;
        boolean[] sideNow = sideBuf;
        Arrays.fill(sideNow, false);
        boolean[] pinChanged = pinChangedBuf;
        Arrays.fill(pinChanged, false);
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            boolean on = outs[pin];
            if (on != pinOutput[pin]) {
                pinOutput[pin] = on;
                pinChanged[pin] = true;
                changed = true;
            }
            if (on) {
                sideNow[BoardData.pinSide(pin)] = true;
            }
        }
        if (!Arrays.equals(input, pinInput)) {
            System.arraycopy(input, 0, pinInput, 0, BoardData.PIN_COUNT);
            changed = true;
        }
        if (!Arrays.equals(sideNow, boardSidePowered)) {
            System.arraycopy(sideNow, 0, boardSidePowered, 0, 4);
            changed = true;
        }
        if (!Arrays.equals(powered, cellPowered)) {
            System.arraycopy(powered, 0, cellPowered, 0, BoardData.CELL_COUNT);
            changed = true;
        }
        // 电线末端的接线端子：这根针脚一变，就让端子旁边的方块（红石灯、门……）重新看一眼，
        // 顺便叫端子自己重算——端子可能挂在几十格外，收不到板子这边的方块更新
        for (WireLink wire : wires) {
            if (!wire.toTerminal() || !pinChanged[wire.pin() & 63] || !level.isLoaded(wire.target())) {
                continue;
            }
            if (level.getBlockEntity(wire.target()) instanceof WireTerminalBlockEntity terminal) {
                terminal.requestRefresh();
            }
            level.updateNeighborsAt(wire.target(), ModRegistry.WIRE_TERMINAL.get());
        }
        if (changed) {
            setChanged();
            markClientDirty();
            notifyNeighbours();
        }
    }

    private void notifyNeighbours() {
        if (level == null) {
            return;
        }
        for (BoardPart other : BoardPart.values()) {
            BlockPos pos = other.partPos(worldPosition, facing);
            if (level.isLoaded(pos)) {
                level.updateNeighborsAt(pos, ModRegistry.CIRCUIT_BOARD.get());
            }
        }
    }
}
