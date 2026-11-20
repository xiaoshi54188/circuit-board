package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 接线端子（小铜疙瘩）：电线 / 跳线 / 飞线的接头。
 *
 * <p>端子有电就朝四周发出红石信号 15，红石灯、门、活塞、红石粉都会正常亮 / 动。</p>
 *
 * <p>一个端子上可以挂好几根线（每根有编号），所以方块的同一个面也能接不止一根线。
 * 空手右键端子会打开一个小面板，能看清这个端子上接了几根线、各连到哪，并且选中编号删掉。
 * 端子不能拿在手上，是接线的时候自动装上去的；拆掉它，它上面的线也就都断了。</p>
 */
public class WireTerminalBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);

    public WireTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WireTerminalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof WireTerminalBlockEntity terminal) {
                terminal.serverTick();
            }
        };
    }

    /**
     * 旁边有方块变了（拉杆扳了、红石粉重新算过了、红石灯亮了……）就记一笔，
     * 下一 tick 只重算这一串线，不用每 tick 都白跑。
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) {
            terminal.requestRefresh();
        }
    }

    /**
     * 空手右键端子：打开「这个柱子上接了哪几根线」的小面板，可以按编号删线。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenTerminalPayload(pos));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 在 pos 装一个端子，接到 source 那块板的 pin 上。facing 是「从端子指向要送电的方块」的方向。
     * 返回有没有装上（位置被占了就装不上）。
     *
     * <p>这里已经有端子就直接<b>接上去</b>：不管这个端子原来是谁的（跳线挂的、别的针脚挂的），
     * 电线都能接到它上面——一个端子可以接好几根针脚、好几根跳线，电在它们之间是相通的。</p>
     */
    public static boolean place(Level level, BlockPos pos, BlockPos source, int pin, Direction facing) {
        if (level.isClientSide()) {
            return false;
        }
        BlockState there = level.getBlockState(pos);
        if (there.is(ModRegistry.WIRE_TERMINAL.get())) {
            if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) {
                // 板子针脚驱动的端子：朝向是它贴着哪个方块，不能随便改（改了电就送错地方了）
                if (terminal.boardDriven() && terminal.facing() != facing) {
                    return false;
                }
                terminal.linkBoard(source, pin, terminal.boardDriven() ? terminal.facing() : facing);
                terminal.refreshPower();
                level.updateNeighborsAt(pos, ModRegistry.WIRE_TERMINAL.get());
                return true;
            }
            return false;
        }
        if (!there.canBeReplaced()) {
            return false;
        }
        level.setBlock(pos, ModRegistry.WIRE_TERMINAL.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) {
            terminal.linkBoard(source, pin, facing);
            level.updateNeighborsAt(pos, ModRegistry.WIRE_TERMINAL.get());
            return true;
        }
        return false;
    }

    /**
     * 确保 pos 这里有一个端子（能挂跳线、也能接电线的那种），没有就放一个。
     * facing 是「从端子指向它贴着的方块」。放不下返回 null。
     *
     * <p>已经有「由板子针脚供电」的端子也可以往上挂：电线从针脚接过来、跳线再挂上去，
     * 两头是通的。这种端子的朝向不能改（它得继续给原来那个方块送电），朝向对不上就返回 null。</p>
     */
    public static WireTerminalBlockEntity ensureTerminal(Level level, BlockPos pos, Direction facing) {
        if (level.isClientSide()) {
            return null;
        }
        BlockState there = level.getBlockState(pos);
        if (!there.is(ModRegistry.WIRE_TERMINAL.get())) {
            if (!there.canBeReplaced()) {
                return null;
            }
            level.setBlock(pos, ModRegistry.WIRE_TERMINAL.get().defaultBlockState(), 3);
        }
        if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) {
            if (terminal.facing() != facing) {
                if (terminal.boardDriven()) {
                    // 这个端子正给原来贴着的那块方块送电，朝向不能改
                    return null;
                }
                terminal.setFacing(facing);
            }
            return terminal;
        }
        return null;
    }

    /**
     * 单向电线：在 pos 装一个端子，它去读 readFrom 那个方块，往自己贴着的方块送电。
     * facing 是「从端子指向要送电的方块」的方向。
     */
    public static boolean placeSourceLink(Level level, BlockPos pos, BlockPos readFrom, Direction facing) {
        if (level.isClientSide()) {
            return false;
        }
        WireTerminalBlockEntity terminal = ensureTerminal(level, pos, facing);
        if (terminal == null) {
            return false;
        }
        terminal.addCable(WireTerminalBlockEntity.CABLE_SOURCE, readFrom);
        terminal.refreshPower();
        level.updateNeighborsAt(pos, ModRegistry.WIRE_TERMINAL.get());
        return true;
    }

    /**
     * 跳线 / 飞线：两头各来一个端子（有现成的就用现成的），中间接成一根双向的线。
     * 返回是否接好了。
     */
    public static boolean placePair(Level level, BlockPos posA, Direction facingA,
                                    BlockPos posB, Direction facingB) {
        if (level.isClientSide()) {
            return false;
        }
        if (posA.equals(posB)) {
            return false;
        }
        WireTerminalBlockEntity first = ensureTerminal(level, posA, facingA);
        if (first == null) {
            return false;
        }
        WireTerminalBlockEntity second = ensureTerminal(level, posB, facingB);
        if (second == null) {
            return false;
        }
        // 两头一起定颜色：同一根跳线两头画出来是同一个颜色（不然一半一个颜色，看着像花屏）
        if (!WireTerminalBlockEntity.addPairCable(first, posB, second, posA)) {
            return false;
        }
        first.refreshPower();
        second.refreshPower();
        level.updateNeighborsAt(posA, ModRegistry.WIRE_TERMINAL.get());
        level.updateNeighborsAt(posB, ModRegistry.WIRE_TERMINAL.get());
        return true;
    }

    /**
     * 拆掉一个端子；它上面所有的线（另一头）也一起收掉。
     * 返回被拆掉的端子里有没有「板子送电」的那种（是的话，板子那边的电线交给板子自己清）。
     */
    public static boolean removeTerminal(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return false;
        }
        // 线动过了：别的端子缓存的「这一串线」要作废
        LinkSupport.linksChanged();
        boolean wasBoard = false;
        if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) {
            wasBoard = terminal.mode() == WireTerminalBlockEntity.MODE_BOARD;
            terminal.detachAllPeers();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return wasBoard;
    }

    /** 防止「拆一头 → 另一头的 onRemove 又回头拆自己」无限递归。 */
    private static boolean removing;

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        // 线动过了：别的端子缓存的「这一串线」要作废
        LinkSupport.linksChanged();
        if (!removing) {
            if (level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) {
                removing = true;
                try {
                    terminal.detachAllPeers();
                } finally {
                    removing = false;
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * 端子只朝「它贴着的那一个方块」送电，别的方向一律 0。
     *
     * <p>原版的约定：{@code direction} 是「从收电的方块指向电源」的方向。端子记的 facing 是
     * 「从端子指向它贴着的方块」，所以正对着那个方块来问，传进来的就是 facing 的反方向。</p>
     *
     * <p>为什么要限定方向：接头是长在方块旁边的一小块铜，要是不管方向都往外发 15，
     * 那么接头旁边随便放个方块（甚至是另一个红石灯）也会被点亮，看起来就像是它也被线连上了。
     * 限定成只给贴着的那一个方块送电，线就只认自己那一头。</p>
     */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal) || !terminal.powered()) {
            return 0;
        }
        return direction == null || direction == terminal.facing().getOpposite() ? 15 : 0;
    }

    /**
     * 强充能（direct signal）一律给 0，只给贴着的那一个方块「弱」送 15。
     *
     * <p>这一条就是「连着线的红石灯一亮，紧挨着的三个红石灯也跟着亮」的根。
     * 原版的规矩：一个方块被强充能，它自己就变成一整格电源——它六个面挨着的方块
     * 都会读到 15（红石灯、石头这类完整方块都会这样）。端子要是强充能它贴着的那个
     * 红石灯，那个红石灯旁边的三个红石灯就全被带亮了，看着就像它们也接了线。</p>
     *
     * <p>所以端子只发弱信号：红石灯、门、活塞、红石粉这些东西看「旁边送过来多少电」时
     * 走的就是弱信号，照样能亮 / 能动；但强充能没有了，电就传不到第二个方块上，
     * 只有真正接了线的那一个方块会亮。</p>
     */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }
}
