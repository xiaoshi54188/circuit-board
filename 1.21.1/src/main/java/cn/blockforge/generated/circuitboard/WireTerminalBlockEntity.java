package cn.blockforge.generated.circuitboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 接线端子：电线 / 跳线 / 飞线钉在目标方块旁边的那个小铜疙瘩。
 *
 * <p>一个端子就像一根小接线柱，上面可以挂好几根线（每根有自己的编号：1、2、3……），
 * 所以同一个方块的同一个面也能接不止一根线。每根线是下面两种之一：</p>
 * <ul>
 *     <li>{@link #CABLE_PAIR}：跳线 / 飞线，两头都是端子，双向通电——哪头有电另一头就跟着有电；</li>
 *     <li>{@link #CABLE_SOURCE}：单向电线，读 {@code peer} 那个方块的信号，有电就送进自己贴着的方块。</li>
 * </ul>
 *
 * <p>「由电路板某根针脚供电」的端子（{@link #MODE_BOARD}）也一样能挂线：电线从针脚接过来、跳线从别的端子
 * 接过来，都挂在这一个端子上，电就在两者之间来回走。一个端子可以被<b>好几根针脚</b>一起驱动，
 * 每一根都记在 {@link Drive} 里，拆掉其中一根不会动别的。</p>
 *
 * <p>停电的时候不会「卡住」：算电的时候会把「这一串线上的所有端子」都排除掉，所以线不会把自己送出去的
 * 电当成外面送进来的电（拉杆关掉了还一直亮就是这个毛病）。</p>
 *
 * <p><b>每根线各算各的</b>：一个端子上挂好几根线时，不是「端子有电就所有线都亮」，而是每根线单独看：</p>
 * <ul>
 *     <li>单向电线（{@link #CABLE_SOURCE}）：只认自己那一头的方块，那头有电这根线才亮；</li>
 *     <li>双向线（{@link #CABLE_PAIR}）：两头本来就是同一根线，这一串有电它就亮。</li>
 * </ul>
 * <p>所以两根线钉在同一个端子上时，通其中一根，另一根该暗还是暗。</p>
 */
public class WireTerminalBlockEntity extends BlockEntity {
    /** 什么都没接（刚放下、数据还没读到）。 */
    public static final int MODE_NONE = 0;
    /** 由电路板针脚供电（电线接板子的那种，老玩法）。 */
    public static final int MODE_BOARD = 1;
    /** 旧存档里的单向线 / 跳线，读档时会被转成下面的 Cable。 */
    private static final int MODE_LEGACY_SOURCE = 2;
    private static final int MODE_LEGACY_PAIR = 3;

    /** 双向的线：和 peer 那个端子是一根线的两头。 */
    public static final int CABLE_PAIR = 0;
    /** 单向的线：读 peer 那个方块，往自己贴着的方块送电。 */
    public static final int CABLE_SOURCE = 1;

    /**
     * 端子上挂的一根线。
     *
     * <p>{@code color} 是这根线的颜色编号（见 {@link CableColors}）：一个端子上挂了好几根线时，
     * 每根线颜色都不一样，站在外面一眼就能分清哪根是哪根。编号是<b>存在线上的</b>，
     * 所以跳线两头存的必须是同一个编号——同一根线两头画出来才是同一个颜色。</p>
     */
    public record Cable(int kind, BlockPos peer, int color) {
    }

    /**
     * 驱动这个端子的一根板子针脚：哪块板（主方块位置）、哪一根针脚。
     *
     * <p>一个端子可以被好几根针脚驱动（哪一根有电，端子就有电），拆掉一根不会影响别的。</p>
     */
    public record Drive(BlockPos board, int pin) {
        public boolean matches(BlockPos boardPos, int boardPin) {
            return pin == boardPin && board.equals(boardPos);
        }
    }

    /** 单向电线的那一头可能在 32 格外，收不到方块更新，隔这么久自己补算一次（tick）。 */
    private static final int REMOTE_POLL_TICKS = 2;
    /** 光靠方块更新可能会漏掉个别情况，隔这么久保险重算一次（tick）。 */
    private static final int SAFETY_REFRESH_TICKS = 20;

    private int mode = MODE_NONE;
    /** 板子模式：供电的电路板（主方块位置）——这是 {@link #drives} 里第一根针脚的副本，老存档要用。 */
    private BlockPos source = BlockPos.ZERO;
    /** 板子模式：供电的那一根针脚，-1 表示还没连好。 */
    private int pin = -1;
    /** 驱动这个端子的针脚（可以有好几根）。 */
    private final List<Drive> drives = new ArrayList<>();
    /** 从端子指向它贴着的那个方块。 */
    private Direction facing = Direction.UP;
    /** 这个端子上挂着的线。 */
    private final List<Cable> cables = new ArrayList<>();
    /**
     * 每根线通不通电（和 {@link #cables} 一一对应）。<b>一根线一位，各算各的</b>：
     * 两根线钉在同一个端子上，通其中一根不会把另一根也点亮。
     *
     * <p>服务端算电时填好，跟着方块更新打包发给客户端（客户端就照这个画亮 / 不亮）。</p>
     */
    private boolean[] cableOn = new boolean[8];
    /** 这份「每根线的通断」是第几版：只有真变过才给客户端发一次包。 */
    private int flagVersion;
    /** 上一次发给客户端的是第几版。 */
    private int sentFlagVersion = -1;
    /** 现在有没有在往外送电（服务端算出来的）。 */
    private boolean powered;
    /** 这一整串线有没有被「外面的东西」供电（不算板子针脚送进来的电）；板子读输入用。 */
    private boolean externalPowered;
    /** 有没有把当前状态同步给过客户端（刚载入时先同步一次）。 */
    private boolean synced;

    // ------------------------------------------------------------------ 算电用的缓存

    /**
     * 这一整串线上的端子（算电用）。<b>同一串线上的所有端子共用同一份</b>：谁先算谁建好，
     * 再把指针发给同串的每一个，所以一条线上挂几百个端子也只有一份，不会每个端子都自己走一遍、各存一份。
     * 顺便也省掉了每 tick 再查一遍方块实体。
     */
    private List<WireTerminalBlockEntity> networkTerminals;
    /** {@link #networkTerminals} 是按哪个接线版本算出来的。 */
    private long networkVersion = -1L;
    /** 旁边有方块变了 / 刚接上线：下一 tick 重算。 */
    private boolean dirty = true;
    /** 下一次「保险重算」在哪个游戏刻。 */
    private long nextRefresh;

    public WireTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TERMINAL_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ 接线

    /** 接到某块板的某根针脚上（板子送电专用）。已经接在这根针脚上就只是换一下朝向。 */
    public void linkBoard(BlockPos sourcePos, int sourcePin, Direction attachFace) {
        this.facing = attachFace;
        addDrive(sourcePos, sourcePin);
    }

    /**
     * 再加一根「驱动这个端子的针脚」。
     *
     * <p>同一个端子可以接好几根针脚：电线的另一头既可以接在方块上，也可以直接接到这个端子上，
     * 哪一根针脚有电，这个端子（和它上面挂的跳线）就有电。</p>
     */
    public void addDrive(BlockPos boardPos, int boardPin) {
        if (boardPin < 0) {
            return;
        }
        BlockPos board = boardPos.immutable();
        if (hasDrive(board, boardPin)) {
            setChanged();
            markDirty();
            return;
        }
        drives.add(new Drive(board, boardPin));
        refreshLegacyLink();
        setChanged();
        markDirty();
    }

    /** 某一根针脚不驱动这个端子了（拆线 / 板子被拆掉）；别的针脚不受影响。 */
    public void removeDrive(BlockPos boardPos, int boardPin) {
        for (int i = drives.size() - 1; i >= 0; i--) {
            if (drives.get(i).matches(boardPos, boardPin)) {
                drives.remove(i);
            }
        }
        refreshLegacyLink();
        setChanged();
        markDirty();
    }

    public boolean hasDrive(BlockPos boardPos, int boardPin) {
        for (Drive drive : drives) {
            if (drive.matches(boardPos, boardPin)) {
                return true;
            }
        }
        return false;
    }

    /** 驱动这个端子的针脚（只读，面板上显示「由哪根针脚供电」用）。 */
    public List<Drive> drives() {
        return drives;
    }

    /**
     * 老字段跟着 {@link #drives} 走：老存档里只认 mode / source / pin 这三个字段，
     * 新存档也照样把它们写出去，这样降级回旧版本时线不会全丢。
     */
    private void refreshLegacyLink() {
        if (drives.isEmpty()) {
            mode = MODE_NONE;
            source = BlockPos.ZERO;
            pin = -1;
            return;
        }
        Drive first = drives.get(0);
        mode = MODE_BOARD;
        source = first.board();
        pin = first.pin();
    }

    public int mode() {
        return mode;
    }

    /** 这个端子是不是由板子针脚驱动的。 */
    public boolean boardDriven() {
        return mode == MODE_BOARD;
    }

    public BlockPos source() {
        return source;
    }

    public int pin() {
        return pin;
    }

    public Direction facing() {
        return facing;
    }

    /** 换一下「端子在方块哪一面」（复用同一个端子位置时用）。 */
    public void setFacing(Direction attachFace) {
        this.facing = attachFace;
        setChanged();
        markDirty();
    }

    /** 端子贴着（也就是要送电）的那个方块。 */
    public BlockPos attachedPos() {
        return worldPosition.relative(facing);
    }

    /** 现在有没有电。 */
    public boolean powered() {
        return powered;
    }

    /**
     * 这一整串线是不是被「外面的东西」（拉杆、红石粉、另一块板……）供电了。
     *
     * <p>和 {@link #powered()} 的区别：这里<b>不算</b>由板子针脚送进来的电。
     * 板子读自己那根针脚的输入时问的就是这个，不然它就会把自己送出去的电又当成外面送进来的电。</p>
     */
    public boolean externalPowered() {
        return externalPowered;
    }

    // ------------------------------------------------------------------ 挂线 / 拆线

    /** 这个端子上挂着的所有线。 */
    public List<Cable> cables() {
        return cables;
    }

    public int cableCount() {
        return cables.size();
    }

    public Cable cable(int index) {
        return index >= 0 && index < cables.size() ? cables.get(index) : null;
    }

    /**
     * 第 index 根线通不通电。
     *
     * <p>每根线是<b>分开算</b>的（见类注释）：单向电线只看自己那一头，双向线看这一串线。
     * 所以两根线钉在同一个端子上时，通其中一根，另一根不会跟着亮。</p>
     */
    public boolean isCableOn(int index) {
        return index >= 0 && index < cables.size() && index < cableOn.length && cableOn[index];
    }

    /**
     * 记下一根线通不通电（算电时由 {@link LinkSupport#measure} 和 {@link #applyCableFlags} 调用）。
     *
     * <p>真变了才让版本号 +1：客户端只在这个数变了的时候才需要重画。</p>
     */
    void setCableOn(int index, boolean on) {
        if (index < 0) {
            return;
        }
        if (index >= cableOn.length) {
            cableOn = Arrays.copyOf(cableOn, Math.max(index + 1, cableOn.length * 2));
        }
        if (cableOn[index] != on) {
            cableOn[index] = on;
            flagVersion++;
        }
    }

    /** 第 index 根线被拆掉了：后面的线往前挪一位，它们的通断也跟着一起挪。 */
    private void dropCableFlag(int index) {
        if (index < 0 || index >= cableOn.length) {
            return;
        }
        if (index + 1 < cableOn.length) {
            System.arraycopy(cableOn, index + 1, cableOn, index, cableOn.length - index - 1);
        }
        cableOn[cableOn.length - 1] = false;
        flagVersion++;
    }

    /** 把「每根线的通断」对齐到现在的线数：读档 / 收到的包比线少的时候兜个底。 */
    private void resetCableFlags(int size) {
        if (cableOn.length < size) {
            cableOn = new boolean[Math.max(size, 8)];
        } else {
            Arrays.fill(cableOn, false);
        }
        flagVersion++;
    }

    /**
     * 在端子上挂一根线，返回它的编号（从 1 开始）；这根线已经在了就返回它原来的编号，接不下才返回 -1。
     *
     * <p>一个端子上能挂多少根线是<b>不封顶</b>的（以前最多 12 根，现在随便接）。</p>
     */
    public int addCable(int kind, BlockPos peer) {
        return addCable(kind, peer, -1);
    }

    /**
     * 挂一根指定颜色的线（颜色编号见 {@link CableColors}）；{@code color} 是负数就自己挑一个没用过的。
     *
     * <p>跳线两头各挂一根，必须由 {@link #addPairCable} 一次定好颜色再挂，不然两头颜色对不上。</p>
     */
    public int addCable(int kind, BlockPos peer, int color) {
        BlockPos target = peer.immutable();
        if (kind == CABLE_PAIR && target.equals(worldPosition)) {
            return -1;
        }
        for (int i = 0; i < cables.size(); i++) {
            Cable cable = cables.get(i);
            if (cable.kind() == kind && cable.peer().equals(target)) {
                // 这根线已经在了：颜色按原来那个，不要动
                return i + 1;
            }
        }
        cables.add(new Cable(kind, target, color >= 0 ? CableColors.wrap(color) : freeColor(null)));
        setChanged();
        markDirty();
        return cables.size();
    }

    /**
     * 一根跳线两头各挂一根线，颜色由两边一起挑：两头都没用过的颜色，保证一根线的两头是一个颜色。
     *
     * @return 有没有挂上
     */
    public static boolean addPairCable(WireTerminalBlockEntity first, BlockPos posB,
                                       WireTerminalBlockEntity second, BlockPos posA) {
        if (first == null || second == null) {
            return false;
        }
        int color = first.freeColor(second);
        int a = first.addCable(CABLE_PAIR, posB, color);
        int b = second.addCable(CABLE_PAIR, posA, color);
        return a > 0 && b > 0;
    }

    /**
     * 挑一个颜色：先从最小的编号开始找自己没用过的；{@code other} 不是空的就也要避开它用过的。
     *
     * <p>颜色都用完了（一个端子上挂了十根以上的线）就按线数轮着来，至少不会全挤在一个颜色上。</p>
     */
    public int freeColor(WireTerminalBlockEntity other) {
        boolean[] used = new boolean[CableColors.COUNT];
        markUsedColors(used);
        if (other != null) {
            other.markUsedColors(used);
        }
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return cables.size() % CableColors.COUNT;
    }

    private void markUsedColors(boolean[] used) {
        for (Cable cable : cables) {
            int color = cable.color();
            if (color >= 0 && color < used.length) {
                used[color] = true;
            }
        }
    }

    /**
     * 老存档里的线没存颜色：按线两头的坐标算一个颜色编号。
     *
     * <p>算的时候只用「两头坐标」这一对东西（跟哪头是哪个端子无关），所以同一根跳线的两头
     * 算出来是同一个颜色。这一行只会在读老存档的时候跑一次，跑完颜色就存下来了。</p>
     */
    private void resolveLegacyColors() {
        boolean changed = false;
        for (int i = 0; i < cables.size(); i++) {
            Cable cable = cables.get(i);
            if (cable.color() >= 0) {
                continue;
            }
            int want = hashColor(worldPosition, cable.peer());
            cables.set(i, new Cable(cable.kind(), cable.peer(), freeColorNear(want)));
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    /** 从 {@code want} 开始往后找一个这个端子上还没用过的颜色。 */
    private int freeColorNear(int want) {
        int start = CableColors.wrap(want);
        for (int step = 0; step < CableColors.COUNT; step++) {
            int color = (start + step) % CableColors.COUNT;
            if (!usesColor(color)) {
                return color;
            }
        }
        return start;
    }

    private boolean usesColor(int color) {
        for (Cable cable : cables) {
            if (cable.color() == color) {
                return true;
            }
        }
        return false;
    }

    /** 两个位置的「对称」哈希：谁在前面谁在后面不影响结果。 */
    private static int hashColor(BlockPos a, BlockPos b) {
        long x0 = Math.min(a.getX(), b.getX());
        long y0 = Math.min(a.getY(), b.getY());
        long z0 = Math.min(a.getZ(), b.getZ());
        long x1 = Math.max(a.getX(), b.getX());
        long y1 = Math.max(a.getY(), b.getY());
        long z1 = Math.max(a.getZ(), b.getZ());
        long hash = x0 * 73856093L ^ y0 * 19349663L ^ z0 * 83492791L
                ^ x1 * 38069L ^ y1 * 15121L ^ z1 * 20903L;
        return (int) Math.abs(hash % CableColors.COUNT);
    }

    /** 拆掉第 index 根线（从 0 数）；双向线会连另一头一起拆掉。 */
    public boolean removeCable(int index) {
        if (index < 0 || index >= cables.size()) {
            return false;
        }
        Cable removed = cables.remove(index);
        dropCableFlag(index);
        setChanged();
        markDirty();
        if (removed.kind() == CABLE_PAIR && level != null && !level.isClientSide() && level.isLoaded(removed.peer())) {
            if (level.getBlockEntity(removed.peer()) instanceof WireTerminalBlockEntity other && other != this) {
                int peerIndex = other.indexOfCable(worldPosition);
                if (peerIndex >= 0) {
                    other.cables.remove(peerIndex);
                    other.dropCableFlag(peerIndex);
                    other.setChanged();
                    other.markDirty();
                }
            }
        }
        return true;
    }

    /** 只把自己的这根线摘掉，不去动另一头（拆端子时给对面用的，避免来回递归）。 */
    public void forgetCableTo(BlockPos peer) {
        int index = indexOfCable(peer);
        if (index < 0) {
            return;
        }
        cables.remove(index);
        dropCableFlag(index);
        setChanged();
        markDirty();
    }

    /** 拆掉这个端子之前，先让每一根双向线的另一头把指向我的那根也忘掉。 */
    public void detachAllPeers() {
        if (level == null || level.isClientSide()) {
            return;
        }
        List<Cable> copy = new ArrayList<>(cables);
        for (Cable cable : copy) {
            if (cable.kind() != CABLE_PAIR) {
                continue;
            }
            if (level.getBlockEntity(cable.peer()) instanceof WireTerminalBlockEntity other && other != this) {
                other.forgetCableTo(worldPosition);
            }
        }
    }

    /** 另一端被拆掉 / 换成了别的方块：这根线就自己收掉。 */
    private void validateCables() {
        if (cables.isEmpty() || level == null) {
            return;
        }
        boolean changed = false;
        for (int i = cables.size() - 1; i >= 0; i--) {
            Cable cable = cables.get(i);
            if (cable.kind() != CABLE_PAIR || !level.isLoaded(cable.peer())) {
                continue;
            }
            if (!(level.getBlockEntity(cable.peer()) instanceof WireTerminalBlockEntity other)
                    || other.indexOfCable(worldPosition) < 0) {
                cables.remove(i);
                dropCableFlag(i);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            markDirty();
        }
    }

    /** 这个端子上有没有连着 position 的那根线，有就返回它的下标。 */
    private int indexOfCable(BlockPos position) {
        for (int i = 0; i < cables.size(); i++) {
            if (cables.get(i).peer().equals(position)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 板子被「别的方式」拆掉了（/setblock、别的模组、炸掉没走正常流程……）：
     * 那几根针脚就作废，别让这个端子一直卡在「等板子送电」上、连跳线都不通。
     */
    private void validateDrives() {
        if (drives.isEmpty() || level == null) {
            return;
        }
        boolean changed = false;
        for (int i = drives.size() - 1; i >= 0; i--) {
            BlockPos board = drives.get(i).board();
            if (!level.isLoaded(board)) {
                continue;
            }
            if (!level.getBlockState(board).is(ModRegistry.CIRCUIT_BOARD.get())) {
                drives.remove(i);
                changed = true;
            }
        }
        if (changed) {
            refreshLegacyLink();
            setChanged();
            markDirty();
        }
    }

    // ------------------------------------------------------------------ 每 tick 算电

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // 没动静就什么都不干：这是「优化」的关键，以前每个端子每 tick 都重新走一遍线。
        // 由板子针脚驱动的端子算得勤一点（见 solve 里的间隔），板子那边一变，最多两 tick 就能跟上
        if (!dirty && level.getGameTime() < nextRefresh) {
            return;
        }
        solve();
    }

    /** 旁边有方块变了（拉杆、红石粉、红石灯……），下一 tick 重算一次。 */
    public void requestRefresh() {
        dirty = true;
    }

    /** 重新算一次并以「现在是带电的吗」返回（刚接好线 / 拆线前用）。 */
    public boolean refreshPower() {
        if (level == null || level.isClientSide()) {
            return powered;
        }
        solve();
        return powered;
    }

    /**
     * 这个端子上面挂着的针脚里，有没有哪一根现在正在输出。
     *
     * <p>一个端子可以接好几根针脚（电线直接从针脚接过来），只要有<b>一根</b>有电，端子就有电。</p>
     */
    public boolean boardPinPowered() {
        if (level == null) {
            return false;
        }
        if (drives.isEmpty()) {
            // 老存档 / 刚读进来还没补齐 drives：退回老字段，别让线突然不亮
            return mode == MODE_BOARD && pin >= 0
                    && level.getBlockEntity(source) instanceof CircuitBoardBlockEntity board
                    && board.isPinPowered(pin);
        }
        for (Drive drive : drives) {
            if (level.getBlockEntity(drive.board()) instanceof CircuitBoardBlockEntity board
                    && board.isPinPowered(drive.pin())) {
                return true;
            }
        }
        return false;
    }

    /** 把这一整串线算一遍：同一串线上的端子本来就是一回事，一次算完全都改掉。 */
    private void solve() {
        validateDrives();
        validateCables();
        List<WireTerminalBlockEntity> terminals = terminals();
        // 一遍算完：通不通电、「外面送进来的电」、要不要主动轮询，三个结论一次拿到
        // （以前是走两遍，同一根红石粉要被读两次）
        LinkSupport.Power power = LinkSupport.measure(level, terminals);
        boolean on = power.on();
        long now = level.getGameTime();
        long interval = power.poll() ? REMOTE_POLL_TICKS : SAFETY_REFRESH_TICKS;
        for (int i = 0, n = terminals.size(); i < n; i++) {
            WireTerminalBlockEntity terminal = terminals.get(i);
            // 「外面送进来的电」单独留一份：板子读针脚输入时要的就是它（不能把自己送出去的算进来）
            terminal.externalPowered = power.external();
            terminal.dirty = false;
            terminal.nextRefresh = now + interval;
            // 双向线跟着整串线走（单向线在 measure 里已经按「自己那一头」填好了）
            terminal.applyCableFlags(on);
            terminal.applyPower(on);
        }
    }

    /**
     * 双向线（跳线 / 飞线）的通断：这一串线有电它就通。
     *
     * <p>单向电线不在这里掺和：它只认自己那一头，{@link LinkSupport#measure} 已经各自填好了。
     * 这样同一个端子上挂着两根线时，通其中一根，另一根不会被带着一起亮。</p>
     */
    private void applyCableFlags(boolean on) {
        for (int i = 0, n = cables.size(); i < n; i++) {
            if (cables.get(i).kind() == CABLE_PAIR) {
                setCableOn(i, on);
            }
        }
    }

    /**
     * 这一串线上的端子。接线动过（版本号变了）才重新走一遍，平时直接用缓存。
     *
     * <p>走一遍也就几个格子的 BFS，但一条线上可能挂着成百上千个端子，每个端子都自己走一遍、
     * 各自存一份，就是「端子越多、翻倍地慢」。所以谁先算，谁就把结果<b>发给同串的每一个端子</b>共用：
     * 一条线只走一遍、只存一份，后面的端子直接拿来就用。</p>
     */
    private List<WireTerminalBlockEntity> terminals() {
        long version = LinkSupport.linkVersion();
        if (networkTerminals != null && networkVersion == version) {
            return networkTerminals;
        }
        List<BlockPos> nodes = new ArrayList<>(4);
        LinkSupport.collectNetwork(level, worldPosition, nodes);
        List<WireTerminalBlockEntity> found = new ArrayList<>(nodes.size());
        for (int i = 0, n = nodes.size(); i < n; i++) {
            if (level.getBlockEntity(nodes.get(i)) instanceof WireTerminalBlockEntity terminal) {
                found.add(terminal);
            }
        }
        if (found.isEmpty()) {
            // 理论上不会走到这里（起点自己一定是个端子），兜个底，别让外面拿到空指针
            networkTerminals = found;
            networkVersion = version;
            return found;
        }
        for (int i = 0, n = found.size(); i < n; i++) {
            WireTerminalBlockEntity terminal = found.get(i);
            terminal.networkTerminals = found;
            terminal.networkVersion = version;
        }
        return found;
    }

    /**
     * 把算出来的结果落到自己身上；真变了才去惊动旁边（红石灯就是靠这个亮 / 灭的）。
     *
     * <p>「要不要给客户端发一次包」看两件事：整串线通不通变了、或者某根线的通断变了。
     * 只有某根线悄悄变了（比如另一根线的电源关了）的时候，不去惊动周围的红石——那只会白算一遍。</p>
     */
    private void applyPower(boolean on) {
        if (level == null || level.isClientSide()) {
            powered = on;
            synced = true;
            return;
        }
        boolean powerChanged = on != powered || !synced;
        boolean flagsChanged = flagVersion != sentFlagVersion;
        if (!powerChanged && !flagsChanged) {
            return;
        }
        powered = on;
        synced = true;
        sentFlagVersion = flagVersion;
        // 这里不 setChanged()：「通不通电」是每 tick 自己算出来的，写进存档也没用，
        // 重进世界第一 tick 照样重算一遍。红石钟一秒里来回几十次，每次都标脏整块区块纯属白费。
        BlockState state = getBlockState();
        if (!state.is(ModRegistry.WIRE_TERMINAL.get())) {
            return;
        }
        // 客户端要照新的「每根线通不通」重画
        level.sendBlockUpdated(worldPosition, state, state, 3);
        if (!powerChanged) {
            return;
        }
        // 自己变了：让周围（尤其是贴着的那个红石灯 / 红石粉）重新看一眼有没有电
        level.updateNeighborsAt(worldPosition, ModRegistry.WIRE_TERMINAL.get());
        // 贴着的那块方块四周也惊动一下：别处接在同一块方块上的线要知道这边来电了
        level.updateNeighborsAt(attachedPos(), ModRegistry.WIRE_TERMINAL.get());
    }

    // ------------------------------------------------------------------ 同步

    private void markDirty() {
        dirty = true;
        LinkSupport.linksChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
            level.updateNeighborsAt(worldPosition, ModRegistry.WIRE_TERMINAL.get());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("mode", mode);
        tag.putInt("face", facing.ordinal());
        tag.putIntArray("drives", encodeDrives());
        // 新的写法：每根线带上颜色（"cables" 是没颜色时的老写法，这里不用再发一遍）
        tag.putIntArray("cables2", encodeCables());
        tag.putBoolean("on", powered);
        // 每根线通不通电（按位打包，32 根线一个 int）
        tag.putIntArray("cable_on", encodeCableFlags());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 客户端渲染用：这根柱子现在通不通电。 */
    public boolean clientLit() {
        return powered;
    }

    /** 每根线 5 个 int：种类、颜色、目标 x、y、z。 */
    private int[] encodeCables() {
        int[] data = new int[cables.size() * 5];
        for (int i = 0; i < cables.size(); i++) {
            Cable cable = cables.get(i);
            data[i * 5] = cable.kind();
            data[i * 5 + 1] = CableColors.wrap(cable.color());
            data[i * 5 + 2] = cable.peer().getX();
            data[i * 5 + 3] = cable.peer().getY();
            data[i * 5 + 4] = cable.peer().getZ();
        }
        return data;
    }

    /** 老写法的线（没颜色），只为了「降级回旧版本时线不会全丢」才写这一份。 */
    private int[] encodeCablesLegacy() {
        int[] data = new int[cables.size() * 4];
        for (int i = 0; i < cables.size(); i++) {
            Cable cable = cables.get(i);
            data[i * 4] = cable.kind();
            data[i * 4 + 1] = cable.peer().getX();
            data[i * 4 + 2] = cable.peer().getY();
            data[i * 4 + 3] = cable.peer().getZ();
        }
        return data;
    }

    /** 每根「驱动针脚」4 个 int：板的 x、y、z、针脚号。 */
    private int[] encodeDrives() {
        int[] data = new int[drives.size() * 4];
        for (int i = 0; i < drives.size(); i++) {
            Drive drive = drives.get(i);
            data[i * 4] = drive.board().getX();
            data[i * 4 + 1] = drive.board().getY();
            data[i * 4 + 2] = drive.board().getZ();
            data[i * 4 + 3] = drive.pin();
        }
        return data;
    }

    private void decodeDrives(int[] data) {
        drives.clear();
        if (data == null) {
            return;
        }
        for (int i = 0; i + 3 < data.length; i += 4) {
            drives.add(new Drive(new BlockPos(data[i], data[i + 1], data[i + 2]), data[i + 3]));
        }
    }

    /** 读新写法的线（每根 5 个 int，第 2 个是颜色）。 */
    private void decodeCables(int[] data) {
        cables.clear();
        if (data == null) {
            return;
        }
        for (int i = 0; i + 4 < data.length; i += 5) {
            cables.add(new Cable(data[i], new BlockPos(data[i + 2], data[i + 3], data[i + 4]), data[i + 1]));
        }
        resolveLegacyColors();
    }

    /** 读老写法的线（每根 4 个 int，没有颜色）：颜色按两头坐标补一个。 */
    private void decodeCablesLegacy(int[] data) {
        cables.clear();
        if (data == null) {
            return;
        }
        for (int i = 0; i + 3 < data.length; i += 4) {
            cables.add(new Cable(data[i], new BlockPos(data[i + 1], data[i + 2], data[i + 3]), -1));
        }
        resolveLegacyColors();
    }

    /**
     * 每根线通不通电按位打包：32 根线一个 int。
     *
     * <p>不每根线存一个 int（那要多几倍的数据），也不发一整串布尔：线多的时候一个 int 就管 32 根，
     * 一根线两根线的一个 int 也就够，包很小。</p>
     */
    private int[] encodeCableFlags() {
        int words = (cables.size() + 31) >>> 5;
        if (words == 0) {
            return new int[0];
        }
        int[] data = new int[words];
        for (int i = 0, n = Math.min(cables.size(), cableOn.length); i < n; i++) {
            if (cableOn[i]) {
                data[i >>> 5] |= 1 << (i & 31);
            }
        }
        return data;
    }

    /**
     * 读「每根线通不通电」。
     *
     * <p>老存档 / 老版本的包里没有这一项（{@code data == null}）：退回老样子——整串线有电就都亮，
     * 至少不会让线突然全暗掉。服务端下一 tick 重算时就会换成一根一根算的结果。</p>
     */
    private void decodeCableFlags(int[] data) {
        int size = cables.size();
        resetCableFlags(size);
        if (data == null) {
            Arrays.fill(cableOn, 0, size, powered);
            return;
        }
        for (int i = 0; i < size; i++) {
            int word = i >>> 5;
            if (word < data.length) {
                cableOn[i] = (data[word] & (1 << (i & 31))) != 0;
            }
        }
    }

    // ------------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("mode", mode);
        tag.putInt("sx", source.getX());
        tag.putInt("sy", source.getY());
        tag.putInt("sz", source.getZ());
        tag.putInt("pin", pin);
        tag.putIntArray("drives", encodeDrives());
        tag.putInt("face", facing.ordinal());
        tag.putIntArray("cables2", encodeCables());
        // 老写法也写一份：万一降级回旧版本，线不会全丢（旧版本只会读这一份，颜色它不认识也无所谓）
        tag.putIntArray("cables", encodeCablesLegacy());
        tag.putBoolean("on", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = intOr(tag, "mode", MODE_NONE);
        source = new BlockPos(intOr(tag, "sx", 0), intOr(tag, "sy", 0), intOr(tag, "sz", 0));
        pin = intOr(tag, "pin", -1);
        Direction[] all = Direction.values();
        int face = intOr(tag, "face", Direction.UP.ordinal());
        facing = all[Math.max(0, Math.min(all.length - 1, face))];
        // 老存档里没有 mode 这个字段，只有 pin / source，按板子模式读
        if (mode == MODE_NONE && pin >= 0) {
            mode = MODE_BOARD;
        }
        // 老存档的「单向线 / 跳线」只有一个 partner，读进来转成一根线
        if (mode == MODE_LEGACY_SOURCE || mode == MODE_LEGACY_PAIR) {
            BlockPos oldPartner = new BlockPos(intOr(tag, "px", 0), intOr(tag, "py", 0),
                    intOr(tag, "pz", 0));
            int kind = mode == MODE_LEGACY_SOURCE ? CABLE_SOURCE : CABLE_PAIR;
            mode = MODE_NONE;
            if (!oldPartner.equals(BlockPos.ZERO)) {
                cables.add(new Cable(kind, oldPartner, -1));
            }
        }
        // 驱动这个端子的针脚（新的写法）；老存档没有这个字段，就用 mode / source / pin 补一根
        decodeDrives(intArrayOrNull(tag, "drives"));
        if (drives.isEmpty() && mode == MODE_BOARD && pin >= 0) {
            drives.add(new Drive(source.immutable(), pin));
        }
        if (drives.isEmpty()) {
            mode = MODE_NONE;
        } else {
            refreshLegacyLink();
        }
        // 线：新写法带颜色；只有老存档 / 老版本发来的包才是 "cables"（没颜色，读完补上）
        if (tag.contains("cables2")) {
            decodeCables(intArrayOrNull(tag, "cables2"));
        } else if (tag.contains("cables")) {
            decodeCablesLegacy(intArrayOrNull(tag, "cables"));
        }
        // 上面老写法（MODE_LEGACY_* 那一段）直接加进来的线也在这里补上颜色
        resolveLegacyColors();
        // 客户端的「通不通电」是服务端同步过来的
        powered = tag.getBoolean("on");
        // 每根线通不通电（老存档 / 老版本的包里没有这一项，读的时候会退回「整串线有电就都亮」）
        decodeCableFlags(intArrayOrNull(tag, "cable_on"));
        // 刚载入 / 刚放下：下一 tick 一定重算一次，省得拿着旧存档里的状态发光
        networkTerminals = null;
        networkVersion = -1L;
        dirty = true;
    }

    // ---- 1.21.1 的存档标签（CompoundTag）没有「取不到就给默认值」的现成方法，这里补两个小工具 ----

    /** 存过就取出来，没存过就是 null（老存档里常常没有这一项）。 */
    private static int[] intArrayOrNull(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getIntArray(key) : null;
    }

    /** 存过就取出来，没存过就用默认值。 */
    private static int intOr(CompoundTag tag, String key, int fallback) {
        return tag.contains(key) ? tag.getInt(key) : fallback;
    }
}
