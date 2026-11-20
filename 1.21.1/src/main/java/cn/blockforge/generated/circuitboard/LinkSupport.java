package cn.blockforge.generated.circuitboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 电线 / 跳线共用的一套小工具：算距离、看某个方块有没有「别的东西」在给它送电、找装端子的空位。
 *
 * <p>最要紧的是 {@link #externalPower}：读电的时候要把「自己这根线送进去的电」排除掉。
 * 端子是个红石信号源，它一边把电送给贴着的方块，一边自己也是 15 级电源。要是读电的时候
 * 把这股自己送出去的电算成「外面送进来的电」，线就会自己给自己供电、拉杆关掉了还一直亮。</p>
 *
 * <p>原版的 {@code level.getSignal(...)} 有一套「方块被强充能就当成有电」的规则，强充能正好
 * 会把端子自己发出去的信号算进来（红石灯、石头这类完整方块都会被强充能）。所以这里不用它，
 * 改成自己一格一格地看：</p>
 * <ul>
 *     <li>方块自己的信号（拉杆、红石火把、中继器、红石块……）；</li>
 *     <li>被旁边的方块强充能 —— 但端子本身的强充能一律不算（就是它在搞鬼）；</li>
 *     <li>旁边方块送过来的弱信号 —— 自己这一整串线上的端子不算。</li>
 * </ul>
 */
public final class LinkSupport {
    /** 电线 / 跳线最多能拉多远（每个方向各 32 格，也就是 32x32x32 的方盒子）。 */
    public static final int MAX_LINK_DISTANCE = 32;
    /** 顺着一条红石粉找来源时最多走多少格。 */
    private static final int MAX_DUST_WALK = 128;
    /**
     * 一根线上最多串多少个端子。这只是防止「线接成了死循环」的保险，不是接线数量的上限：
     * 一根针脚 / 一个端子上能挂多少根线不封顶，串成一整张网时这里也放得下。
     */
    private static final int MAX_LINK_NODES = 4096;
    /** 红石信号满格。 */
    private static final int MAX_SIGNAL = 15;
    /** 找空位装端子时的优先顺序：先上面，再四周，最后才是底下（免得把接头塞到方块底下看不见）。 */
    private static final Direction[] SPOT_ORDER = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
    };
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * 接线的「版本号」。每动一次线（接线、拆线、端点被挖掉）就 +1，
     * 端子拿它判断自己缓存的那一串线过期没有，省得每 tick 都从头走一遍。
     */
    private static volatile long linkVersion;

    private LinkSupport() {
    }

    /** 现在的接线版本号。 */
    public static long linkVersion() {
        return linkVersion;
    }

    /** 线动过了：所有端子缓存的「这一串线」作废。 */
    public static void linksChanged() {
        linkVersion++;
    }

    /** 两个方块在不在 32x32x32 的范围里（x / y / z 各自不超过 32 格）。 */
    public static boolean withinRange(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= MAX_LINK_DISTANCE
                && Math.abs(a.getY() - b.getY()) <= MAX_LINK_DISTANCE
                && Math.abs(a.getZ() - b.getZ()) <= MAX_LINK_DISTANCE;
    }

    // ------------------------------------------------------------------ 门 / 栅栏门 / 活板门

    /**
     * 这几个方块自己会把右键「吃掉」：拿电线点它，方块先开门 / 开栅栏，电线根本轮不到。
     *
     * <p>铁门 / 铁活板门手打不开，本来就吃不到这一下；它们也被算进来，好让上下两面和侧面走同一套接线。</p>
     */
    public static boolean eatsRightClick(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof TrapDoorBlock;
    }

    /** 点的是门的正面 / 侧面（不是顶面 / 底面）吗——这种面以前是「开门」的，提醒一句要空手开。 */
    public static boolean doorSideFace(Direction face) {
        return face != Direction.UP && face != Direction.DOWN;
    }

    /** 这个方块空手右键就能开吗（木门 / 栅栏门 / 木活板门能，铁门 / 铁活板门不能）。 */
    public static boolean handOpened(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock) {
            return DoorBlock.isWoodenDoor(state);
        }
        if (block instanceof FenceGateBlock) {
            return true;
        }
        return block instanceof TrapDoorBlock && !state.is(Blocks.IRON_TRAPDOOR);
    }

    /**
     * 拿电线 / 跳线点门这一下，端子该贴在哪一格门上；不是会抢右键的那几种方块就返回 {@code null}。
     *
     * <p>六个面都接线：门朝哪边、铰链装哪边都不影响，点在正面 / 侧面 / 顶面 / 底面都能拉线。
     * 这几种方块的右键本来会被「开门」抢走，所以拿着电线点它们的时候由电线先接手；
     * <b>想开关门就空手右键</b>（空手那一下照旧是门自己的）。</p>
     *
     * <p>门有两格高，统一认<b>上面那一格</b>：点下半格时往上一格其实还是同一扇门，端子要装到
     * 整扇门的最上面，不然会卡在门里装不下。门的上半格和下半格本来就是一扇门，
     * 给哪一格送电，整扇门都会开。</p>
     *
     * @return 真正该送电的那一格门；不是这几种方块就返回 {@code null}
     */
    public static BlockPos doorAttach(Level level, BlockPos clicked, BlockState state) {
        if (!eatsRightClick(state)) {
            return null;
        }
        if (!(state.getBlock() instanceof DoorBlock)
                || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
            // 栅栏门 / 活板门只有一格；门点到的本来就是上半格
            return clicked;
        }
        BlockPos upper = clicked.above();
        // 只剩半扇门的时候就别挪了，免得挪到别的方块上去
        return level.getBlockState(upper).is(state.getBlock()) ? upper : clicked;
    }

    // ------------------------------------------------------------------ 读电

    /**
     * 这个方块有没有在「被别人」供电。
     *
     * @param ignore 这些位置上的端子算自己这串线，它们送的电不算「外面送进来的电」
     *               （传 {@code long} 坐标的集合：一根线上可以挂成百上千个端子，
     *               以前拿 List 一格一格比是白跑的路）
     */
    public static int externalPower(Level level, BlockPos pos, Set<Long> ignore) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.REDSTONE_WIRE)) {
            // 红石粉是一整条线：只认这条线上有没有真正的电源，不认它被我们点亮成什么值
            return dustLinePower(level, pos, ignore);
        }
        int best = 0;
        // 1. 方块自己就是个电源（拉杆、红石火把、中继器、按钮、红石块……）
        for (Direction direction : DIRECTIONS) {
            int own = state.getSignal(level, pos, direction);
            if (own > best) {
                best = own;
                if (best >= MAX_SIGNAL) {
                    return MAX_SIGNAL;
                }
            }
        }
        // 2. 被旁边的方块强充能了（拉杆 / 中继器贴在上面那种）。端子的强充能不算：那可能是自己送的。
        if (state.isRedstoneConductor(level, pos)) {
            int strong = strongPowerInto(level, pos);
            if (strong > best) {
                best = strong;
                if (best >= MAX_SIGNAL) {
                    return MAX_SIGNAL;
                }
            }
        }
        // 3. 旁边的方块往这边送弱信号（红石块、别的线 / 板子送电的端子……）
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.isLoaded(neighbour) || ignore.contains(neighbour.asLong())) {
                continue;
            }
            BlockState neighbourState = level.getBlockState(neighbour);
            if (neighbourState.is(Blocks.REDSTONE_WIRE)) {
                // 红石粉统一在 dustLinePower 里处理
                continue;
            }
            // 原版的约定：问「邻居往我这边送多少电」时，传的方向是「从我指向邻居」
            int signal = signalFromNeighbour(level, neighbour, neighbourState, direction);
            if (signal > best) {
                best = signal;
                if (best >= MAX_SIGNAL) {
                    return MAX_SIGNAL;
                }
            }
        }
        return best;
    }

    /**
     * 邻居方块往「对着 pos 的那一面」送出来的信号。
     *
     * @param toSource 从 pos（收电的那一方）指向 neighbour（电源）的方向，正好是原版 getSignal 要的方向
     */
    private static int signalFromNeighbour(Level level, BlockPos neighbour, BlockState state, Direction toSource) {
        int best = state.getSignal(level, neighbour, toSource);
        if (best >= MAX_SIGNAL) {
            return MAX_SIGNAL;
        }
        // 完整方块被强充能的时候，原版也会把它当成「在往四周送电」
        if (state.isRedstoneConductor(level, neighbour)) {
            int strong = strongPowerInto(level, neighbour);
            if (strong > best) {
                best = strong;
            }
        }
        return best;
    }

    /**
     * 这个方块被旁边的方块强充能了多少。
     *
     * <p>端子的强充能一律跳过：端子有没有电是这条线自己算出来的，把它算进来就会自己给自己供电。</p>
     */
    private static int strongPowerInto(Level level, BlockPos pos) {
        int best = 0;
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            BlockState neighbourState = level.getBlockState(neighbour);
            if (neighbourState.is(ModRegistry.WIRE_TERMINAL.get())) {
                continue;
            }
            int signal = neighbourState.getDirectSignal(level, neighbour, direction);
            if (signal > best) {
                best = signal;
                if (best >= MAX_SIGNAL) {
                    return MAX_SIGNAL;
                }
            }
        }
        return best;
    }

    /** 顺着一条红石粉走一圈，看这条线上有没有真正的电源（自己这串线的端子不算）。 */
    private static int dustLinePower(Level level, BlockPos start, Set<Long> ignore) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(start);
        seen.add(start.asLong());
        int best = 0;
        while (!queue.isEmpty() && seen.size() <= MAX_DUST_WALK) {
            BlockPos pos = queue.poll();
            for (Direction direction : DIRECTIONS) {
                BlockPos neighbour = pos.relative(direction);
                if (!level.isLoaded(neighbour)) {
                    continue;
                }
                BlockState neighbourState = level.getBlockState(neighbour);
                if (neighbourState.is(Blocks.REDSTONE_WIRE)) {
                    if (seen.add(neighbour.asLong())) {
                        queue.add(neighbour);
                    }
                    continue;
                }
                if (ignore.contains(neighbour.asLong())) {
                    continue;
                }
                // 原版的约定：问「邻居往我这边送多少电」时，传的方向是「从我指向邻居」
                int signal = signalFromNeighbour(level, neighbour, neighbourState, direction);
                if (signal > best) {
                    best = signal;
                    if (best >= MAX_SIGNAL) {
                        return MAX_SIGNAL;
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ 一串线

    /**
     * 从 start 出发，把「这一整串线」上所有的端子都找出来填进 out。
     *
     * <p>串在一起的规则：跳线两头是一对；钉在同一个方块上的几个端子也算连在一起。</p>
     *
     * <p>「由板子针脚供电」的端子也是这一串里的一员：电线可以从针脚直接接到一个已经挂着跳线的端子上，
     * 电就从针脚流进整串线，反过来整串线有电时板子也能读到。所以这里不再把它排除在外。</p>
     */
    public static void collectNetwork(Level level, BlockPos start, List<BlockPos> out) {
        out.clear();
        if (!(level.getBlockEntity(start) instanceof WireTerminalBlockEntity)) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.asLong());
        out.add(start);
        queue.add(start);
        while (!queue.isEmpty() && out.size() < MAX_LINK_NODES) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal)) {
                continue;
            }
            // 这个端子上每一根双向线，另一头的端子也算同一串（一根柱子上可以挂好几根线）
            for (WireTerminalBlockEntity.Cable cable : terminal.cables()) {
                if (cable.kind() != WireTerminalBlockEntity.CABLE_PAIR) {
                    continue;
                }
                BlockPos peer = cable.peer();
                if (level.isLoaded(peer) && level.getBlockEntity(peer) instanceof WireTerminalBlockEntity
                        && seen.add(peer.asLong())) {
                    out.add(peer);
                    queue.add(peer);
                }
            }
            // 钉在同一个方块上的端子是电连通的：一个方块上接的几根线算同一串
            BlockPos attached = terminal.attachedPos();
            for (Direction direction : DIRECTIONS) {
                BlockPos other = attached.relative(direction);
                if (seen.contains(other.asLong())) {
                    continue;
                }
                if (level.getBlockEntity(other) instanceof WireTerminalBlockEntity neighbour
                        && neighbour.attachedPos().equals(attached)) {
                    seen.add(other.asLong());
                    out.add(other);
                    queue.add(other);
                }
            }
        }
    }

    /**
     * 一整串线算一次的结果。
     *
     * @param on       这串线有没有电（板子针脚送进来的电也算）
     * @param external 外面有没有东西在给这串线送电（<b>不算</b>板子针脚送的电；板子读输入时问的就是它）
     * @param poll     这串线上有没有「不会报方块更新」的东西（板子针脚、几十格外的电源），
     *                 有的话就不能光等方块更新，得隔两 tick 自己再问一次
     */
    public record Power(boolean on, boolean external, boolean poll) {
        /** 空的一串线：没电、没外电、不用轮询。 */
        public static final Power OFF = new Power(false, false, false);
    }

    /**
     * 把这一整串线从头算一遍。
     *
     * <p>以前「有没有电」和「外面有没有送电」是<b>分开走两遍</b>的，同一根红石粉、同一块方块
     * 要被读两次；现在合成一遍：外面一确认在送电，两个结论就都定了，剩下的只再扫一眼有没有
     * 需要主动轮询的端子。</p>
     *
     * <p>顺带两个「白跑」也省掉了：同一个端子上的双向线读的都是「它贴着的那块方块」，
     * 一格端子只读一次；判断「这个位置是不是自己人」也从「一条线一个格子地比」换成了查表。</p>
     *
     * <p>{@link PowerCache}：一个方块的六个面就是六个插口，常常六个面都钉着端子，
     * 它们读的是<b>同一格方块</b>。一次算电里读过的位置直接查表，不再翻来覆去读同一个方块
     * （读一格方块要把它的六个邻居都问一遍，重复读一次就是几十次白跑）。</p>
     *
     * <p>顺手也把「<b>每根线</b>通不通电」填好（{@link WireTerminalBlockEntity#setCableOn}）：
     * 单向电线各自认自己那一头，双向线在整串线算完以后再统一填。
     * 这样两根线钉在同一个端子上时，通其中一根不会把另一根也点亮。</p>
     */
    public static Power measure(Level level, List<WireTerminalBlockEntity> component) {
        int n = component.size();
        if (n == 0) {
            return Power.OFF;
        }
        if (n == 1) {
            // 光秃秃一个端子（既没挂线、也没接板子针脚）：怎么算都是没电，连查表都不用开
            WireTerminalBlockEntity only = component.get(0);
            if (only.cableCount() == 0 && !only.boardDriven()) {
                return Power.OFF;
            }
        }
        Set<Long> ignore = new HashSet<>(Math.max(4, n * 2));
        for (int i = 0; i < n; i++) {
            ignore.add(component.get(i).getBlockPos().asLong());
        }
        boolean on = false;
        boolean external = false;
        boolean poll = false;
        // 整串线就一个端子的时候没有「同一个位置读两次」的机会，表都不用开
        PowerCache cache = n > 1 ? new PowerCache(n) : null;
        for (int i = 0; i < n; i++) {
            WireTerminalBlockEntity terminal = component.get(i);
            // 板子送电的端子：电由那几根针脚决定（它旁边的东西变化不会给我们方块更新，所以得轮询）
            boolean driven = terminal.boardDriven();
            if (driven) {
                poll = true;
                if (!on && terminal.boardPinPowered()) {
                    on = true;
                }
            }
            boolean pairRead = false;
            List<WireTerminalBlockEntity.Cable> cables = terminal.cables();
            for (int c = 0, cn = cables.size(); c < cn; c++) {
                WireTerminalBlockEntity.Cable cable = cables.get(c);
                if (cable.kind() == WireTerminalBlockEntity.CABLE_SOURCE) {
                    // 单向电线：<b>每根都读自己那一头</b>。就算整串线已经被别的线点亮了也得读——
                    // 客户端要按「这一根自己通不通」一根一根画，不能一个端子有电就整把线都亮。
                    poll = true;
                    int signal = read(level, cable.peer(), cache, ignore);
                    terminal.setCableOn(c, signal > 0);
                    if (!driven && signal > 0) {
                        // 板子驱动的端子读到的东西可能就是自己刚送出去的，不能算「外面的电」
                        external = true;
                        on = true;
                    }
                    continue;
                }
                // 双向线：两头是同一根线，读自己贴着的方块（一格端子只读一次）。
                // 通不通要等整串线算完才知道，先交给 solve 里的 applyCableFlags 统一填。
                if (driven || external || pairRead) {
                    // 板子驱动的端子读到的可能是自己送出去的电；已经确认外面在送电也不用再读
                    continue;
                }
                pairRead = true;
                if (read(level, terminal.attachedPos(), cache, ignore) > 0) {
                    external = true;
                    on = true;
                }
            }
        }
        return new Power(on, external, poll);
    }

    /**
     * 读一格方块「外面送进来多少电」，一次算电里读过的直接查表（{@code cache} 是空的就现读）。
     */
    private static int read(Level level, BlockPos pos, PowerCache cache, Set<Long> ignore) {
        if (cache == null) {
            return externalPower(level, pos, ignore);
        }
        int signal = cache.get(pos);
        if (signal < 0) {
            signal = externalPower(level, pos, ignore);
            cache.put(pos, signal);
        }
        return signal;
    }

    /**
     * 一次算电里「这一格方块外面送进来多少电」的小表。
     *
     * <p>用数组自己开一张表（不开 HashMap）：这里一格位置对应一个 0~15 的数、数量又不多，
     * 查表是几个数组访问的事，不用担心装箱和 GC。{@code values} 里的 -1 表示这一格还没读过。</p>
     */
    private static final class PowerCache {
        private final long[] keys;
        private final int[] values;
        private final int mask;
        /** 已经记了几格（到表的一半就停止再记，保证永远有空位可以停）。 */
        private int size;

        PowerCache(int expected) {
            int cap = 8;
            // 装到四分之一满就够了：探起来最多一步两步，不会连成一片
            while (cap < expected * 4 && cap < (1 << 20)) {
                cap <<= 1;
            }
            keys = new long[cap];
            values = new int[cap];
            Arrays.fill(values, -1);
            mask = cap - 1;
        }

        /** 这一格这次算过了吗：算过返回结果（0~15），没算过返回 -1。 */
        int get(BlockPos pos) {
            long key = pos.asLong();
            int index = hash(key) & mask;
            while (true) {
                int value = values[index];
                if (value < 0) {
                    return -1;
                }
                if (keys[index] == key) {
                    return value;
                }
                index = (index + 1) & mask;
            }
        }

        void put(BlockPos pos, int value) {
            long key = pos.asLong();
            int index = hash(key) & mask;
            while (values[index] >= 0) {
                if (keys[index] == key) {
                    values[index] = value;
                    return;
                }
                index = (index + 1) & mask;
            }
            // 表装到一半就不再往里记了：宁可这一格下次重读一遍，也不能把表塞满
            // （塞满以后就找不到空位停下来了）。少记几格只是少省一点，结论不受影响。
            if (size * 2 >= keys.length) {
                return;
            }
            keys[index] = key;
            values[index] = value;
            size++;
        }

        private static int hash(long key) {
            long mixed = key * 0x9E3779B97F4A7C15L;
            return (int) (mixed ^ (mixed >>> 32));
        }
    }

    /**
     * 这两个端子之间是不是已经有一根双向线了（手拿跳线 / 飞线接重复的时候用）。
     */
    public static boolean hasCable(Level level, BlockPos terminalA, BlockPos terminalB) {
        if (level.getBlockEntity(terminalA) instanceof WireTerminalBlockEntity terminal) {
            for (WireTerminalBlockEntity.Cable cable : terminal.cables()) {
                if (cable.kind() == WireTerminalBlockEntity.CABLE_PAIR && cable.peer().equals(terminalB)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 接线

    /**
     * 在这个方块旁边找一个能装端子的空位，优先用玩家点的那一面。
     * 返回「从端子指向方块」的方向，找不到返回 null。
     */
    public static Direction findSpot(Level level, BlockPos attached, Direction preferred) {
        if (preferred != null && canPlaceTerminalAt(level, attached.relative(preferred))) {
            return preferred.getOpposite();
        }
        for (Direction direction : SPOT_ORDER) {
            if (canPlaceTerminalAt(level, attached.relative(direction))) {
                return direction.getOpposite();
            }
        }
        return null;
    }

    private static boolean canPlaceTerminalAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(ModRegistry.WIRE_TERMINAL.get())) {
            return false;
        }
        return state.canBeReplaced();
    }

    /**
     * 这个方块的 face 那一面，是不是已经接了一个端子（电线 / 跳线 / 飞线都算）。
     *
     * <p>一个方块的六个面就是六个「插口」。这里只认「贴在 attached 上、而且就钉在 face 那一面」的端子——
     * 钉在别的面上的端子一根都不算，所以不会出现「点这一面，把别一面的线拆了」这种事。</p>
     *
     * <p>「由板子针脚供电」的端子也算：电线从针脚接到端子上以后，这个端子照样可以再挂跳线。</p>
     */
    public static WireTerminalBlockEntity linkTerminalOnFace(Level level, BlockPos attached, Direction face) {
        if (face == null) {
            return null;
        }
        BlockPos pos = attached.relative(face);
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal
                && terminal.attachedPos().equals(attached)) {
            return terminal;
        }
        return null;
    }

    /** 这个方块的 face 那一面现在还能不能装一个接头（空着、而且是能替换的方块）。 */
    public static boolean faceFree(Level level, BlockPos attached, Direction face) {
        return face != null && canPlaceTerminalAt(level, attached.relative(face));
    }

    /**
     * 端子在 terminalPos、贴着 attached 时，它的朝向（「从端子指向贴着的方块」）。
     * 两个位置不挨着返回 null。
     */
    public static Direction attachFacing(BlockPos terminalPos, BlockPos attached) {
        for (Direction direction : DIRECTIONS) {
            if (terminalPos.relative(direction).equals(attached)) {
                return direction;
            }
        }
        return null;
    }

    /** 这两个方块之间是不是已经有一根线了。 */
    public static boolean alreadyLinked(Level level, BlockPos a, BlockPos b) {
        ArrayList<BlockPos> component = new ArrayList<>(4);
        for (Direction direction : DIRECTIONS) {
            BlockPos pos = a.relative(direction);
            if (!(level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal)
                    || !terminal.attachedPos().equals(a)) {
                continue;
            }
            collectNetwork(level, pos, component);
            for (int i = 0, n = component.size(); i < n; i++) {
                if (level.getBlockEntity(component.get(i)) instanceof WireTerminalBlockEntity other
                        && other.attachedPos().equals(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 点下去的这一下，是不是点在「一根线的一头」上：点的是端子本身（那个小铜疙瘩）。 */
    public static WireTerminalBlockEntity linkTerminalAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) {
            return terminal;
        }
        return null;
    }

    /**
     * 想从这个方块的这一面接一根线时，端子应该放在哪。
     *
     * <p>这一面已经有端子就直接用它（电线、跳线都往同一个端子上挂，端子上能挂几根是不封顶的）；
     * 没有才找空位。返回端子的位置，装不下返回 null。</p>
     */
    public static BlockPos terminalPosFor(Level level, BlockPos attached, Direction face) {
        WireTerminalBlockEntity existing = linkTerminalOnFace(level, attached, face);
        if (existing != null) {
            return existing.getBlockPos();
        }
        Direction spot = findSpot(level, attached, face);
        return spot == null ? null : attached.relative(spot.getOpposite());
    }
}
