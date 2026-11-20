package cn.blockforge.generated.circuitboard.client;

import cn.blockforge.generated.circuitboard.CircuitBoardBlockEntity;
import cn.blockforge.generated.circuitboard.WireTerminalBlockEntity;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 客户端的一个小便利贴：界面里「点了要删的那根线」是哪一根。
 *
 * <p>面板里第一次点一根线，线会先变成红色（还没真删）；再点一下才真删，右键就取消。
 * 界面和世界里的方块渲染跑在同一个线程上，所以用这几个静态字段就能把「变红」传给渲染器，
 * 不用再专门发一个网络包。</p>
 *
 * <p><b>关掉面板以后红色还留着</b>：这样满世界跑的时候也看得见自己刚才点了哪一根，
 * 重新打开面板还会把那一行重新选中，接着点第二下就能删。红色只在「真删掉」「右键取消」
 * 或者「又去点了另一根线」的时候才清掉。</p>
 *
 * <p>记的不只是编号，还记了这根线连到哪：线被别的方式拆掉、编号往前挪了，也不会红到另一根线上。</p>
 */
public final class WireHighlight {
    /**
     * 记下这些标记时在哪个世界，换存档 / 换个维度就不算同一根线了。
     * 用弱引用拿着：退出存档以后这个红标记不该把整个世界留在内存里。
     */
    private static WeakReference<Level> world;

    private static BlockPos boardPos;
    private static int boardIndex = -1;
    private static int boardPin = -1;
    private static int boardKind = -1;
    private static int boardTargetPin = -1;
    private static BlockPos boardTarget;

    private static BlockPos terminalPos;
    private static int terminalIndex = -1;
    private static int terminalKind = -1;
    private static BlockPos terminalPeer;

    private WireHighlight() {
    }

    /** 板子面板：把第 index 根线标成待删。 */
    public static void markBoardWire(BlockPos masterPos, int index, CircuitBoardBlockEntity.WireLink wire) {
        clear();
        if (masterPos == null || wire == null || index < 0) {
            return;
        }
        world = new WeakReference<>(currentWorld());
        boardPos = masterPos.immutable();
        boardIndex = index;
        boardPin = wire.pin();
        boardKind = wire.kind();
        boardTargetPin = wire.targetPin();
        boardTarget = wire.target().immutable();
    }

    /** 端子面板：把第 index 根线标成待删。 */
    public static void markTerminalWire(BlockPos pos, int index, WireTerminalBlockEntity.Cable cable) {
        clear();
        if (pos == null || cable == null || index < 0) {
            return;
        }
        world = new WeakReference<>(currentWorld());
        terminalPos = pos.immutable();
        terminalIndex = index;
        terminalKind = cable.kind();
        terminalPeer = cable.peer().immutable();
    }

    /** 取消这次删除：所有红色标记都清掉。 */
    public static void clear() {
        world = null;
        boardPos = null;
        boardIndex = -1;
        boardPin = -1;
        boardKind = -1;
        boardTargetPin = -1;
        boardTarget = null;
        terminalPos = null;
        terminalIndex = -1;
        terminalKind = -1;
        terminalPeer = null;
    }

    /** 这一块板上的第 index 根线，现在是不是红色的「待删除」。 */
    public static boolean isBoardWirePending(BlockPos masterPos, int index,
                                             CircuitBoardBlockEntity.WireLink wire) {
        return wire != null && boardPos != null && boardWireMatches(masterPos, index)
                && boardPin == wire.pin() && boardKind == wire.kind()
                && boardTargetPin == wire.targetPin()
                && boardTarget != null && boardTarget.equals(wire.target());
    }

    /**
     * 这根线的<b>另一头</b>是不是正标着「要删」。
     *
     * <p>双向线的两头各挂着一模一样的半根：平时只画其中一头。可另一头被标成红色的时候，
     * 这一头就不要再叠一根铜色的上去（两根叠在同一个位置上，颜色会打架）。</p>
     */
    public static boolean isPeerWirePending(BlockPos peerPos, int kind, BlockPos selfPos) {
        return peerPos != null && selfPos != null && terminalPos != null && terminalPeer != null
                && terminalPos.equals(peerPos) && terminalPeer.equals(selfPos)
                && terminalKind == kind && sameWorld();
    }

    /** 这个端子上的第 index 根线，现在是不是红色的「待删除」。 */
    public static boolean isTerminalWirePending(BlockPos pos, int index, WireTerminalBlockEntity.Cable cable) {
        return cable != null && terminalPos != null && terminalWireMatches(pos, index)
                && terminalKind == cable.kind() && terminalPeer != null && terminalPeer.equals(cable.peer());
    }

    /** 重新打开面板时用：这块板上待删的那根线在整块板电线列表里排第几（-1 表示没有）。 */
    public static int pendingBoardIndex(BlockPos masterPos) {
        return boardPos != null && boardWireMatches(masterPos, boardIndex) ? boardIndex : -1;
    }

    /** 重新打开面板时用：待删的那根线长在哪根针脚上（-1 表示没有）。 */
    public static int pendingBoardPin(BlockPos masterPos) {
        return boardPos != null && boardWireMatches(masterPos, boardIndex) ? boardPin : -1;
    }

    /** 重新打开端子面板时用：待删的是第几根线（-1 表示没有）。 */
    public static int pendingTerminalIndex(BlockPos pos) {
        return terminalPos != null && terminalWireMatches(pos, terminalIndex) ? terminalIndex : -1;
    }

    private static boolean boardWireMatches(BlockPos masterPos, int index) {
        return masterPos != null && boardPos != null && index >= 0 && boardIndex == index
                && boardPos.equals(masterPos) && sameWorld();
    }

    private static boolean terminalWireMatches(BlockPos pos, int index) {
        return pos != null && terminalPos != null && index >= 0 && terminalIndex == index
                && terminalPos.equals(pos) && sameWorld();
    }

    private static Level currentWorld() {
        return Minecraft.getInstance().level;
    }

    private static boolean sameWorld() {
        Level level = Minecraft.getInstance().level;
        Level remembered = world == null ? null : world.get();
        return remembered != null && remembered == level;
    }
}
