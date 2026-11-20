package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 电路板方块：一整块板由 2x2 个方块拼成（{@link BoardPart}），四面边缘各有一排针脚。
 *
 * <p>空手或拿着别的东西右键 -> 打开绿色的面板界面画线；
 * 拿着电线右键 -> 打开「选针脚」界面，选好以后再去点另一端，太密的针脚就不会点不到了。</p>
 */
public class CircuitBoardBlock extends Block implements EntityBlock {
    /** 这个方块是 2x2 板子里的哪一块。 */
    public static final EnumProperty<BoardPart> PART =
            EnumProperty.create("part", BoardPart.class);
    /**
     * 板面朝哪一边：up / down 是平着放（放地上、贴天花板），
     * north / south / east / west 是竖着贴在墙上。板子的厚度永远贴着 facing 那一侧。
     */
    public static final EnumProperty<Direction> FACING = EnumProperty.create("facing", Direction.class);

    /** 板子厚度（以方块为单位）。 */
    private static final float THICK = BoardData.PLATE_TOP * 16.0F;

    public CircuitBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, BoardPart.NW).setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CircuitBoardBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CircuitBoardBlockEntity board) {
                board.serverTick();
            }
        };
    }

    /** 薄板贴着 facing 那一侧，竖着放的时候就是贴着墙的那一片。 */
    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case UP -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, THICK, 16.0D);
            case DOWN -> Block.box(0.0D, 16.0D - THICK, 0.0D, 16.0D, 16.0D, 16.0D);
            case NORTH -> Block.box(0.0D, 0.0D, 16.0D - THICK, 16.0D, 16.0D, 16.0D);
            case SOUTH -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, THICK);
            case EAST -> Block.box(0.0D, 0.0D, 0.0D, THICK, 16.0D, 16.0D);
            case WEST -> Block.box(16.0D - THICK, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return openPanel(level, pos, player, false);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModRegistry.COPY_BOARD.get())) {
            // 拿着复制电路板：蹲下右键就是复制这一块板
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                CopyBoardItem.tryCopy(level, pos, serverPlayer);
            }
            return ItemInteractionResult.SUCCESS;
        }
        boolean wire = stack.is(ModRegistry.COPPER_WIRE.get());
        return toItemResult(openPanel(level, pos, player, wire));
    }

    /** 老式「右键结果」转成 1.21.1 物品右键要的那种结果。 */
    private static ItemInteractionResult toItemResult(InteractionResult result) {
        if (result == InteractionResult.SUCCESS) {
            return ItemInteractionResult.SUCCESS;
        }
        if (result == InteractionResult.CONSUME) {
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 打开面板；wireMode 为真时进入选针脚模式。 */
    public static InteractionResult openPanel(Level level, BlockPos pos, Player player, boolean wireMode) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof CircuitBoardBlockEntity board) {
                board.syncTo(serverPlayer);
                int pendingPin = wireMode ? CopperWireItem.pendingPin(serverPlayer, board) : -1;
                PacketDistributor.sendToPlayer(serverPlayer, new OpenBoardPayload(pos, wireMode, pendingPin));
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * 输出红石信号。direction 是「接收方指着本方块」的方向，所以真正带电的那一面是它的反面。
     */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        int side = BoardData.sideOfWorld(state.getValue(FACING), direction.getOpposite());
        if (side < 0) {
            return 0;
        }
        if (level.getBlockEntity(pos) instanceof CircuitBoardBlockEntity board) {
            return board.outputSignal(side);
        }
        return 0;
    }

    /**
     * 和端子一样：只给「贴着板子那一面」的方块送弱信号，不做强充能（返回 0）。
     *
     * <p>强充能会把贴着的那一格变成整格电源，它六个面挨着的方块都会读到 15——
     * 于是「连了一个红石灯，旁边三个没连的红石灯也亮」。弱信号足够点亮红石灯、推动活塞、
     * 点亮红石粉，但电走不出被贴着的那个方块，没接线的方块就不会跟着亮。</p>
     */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * 拆掉任意一块方块，整块 2x2 板子一起消失，并且只掉一份「带设计」的板子：
     * 掉下来的板子放回去，画好的线路还在。
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            Direction facing = state.getValue(FACING);
            BlockPos master = state.getValue(PART).masterPos(pos, facing);
            if (!player.isCreative() && level.getBlockEntity(master) instanceof CircuitBoardBlockEntity board) {
                board.ensureNumber();
                ItemStack drop = BoardDesign.createStack(board.number(), board.copyCount(),
                        board.copyCells(), board.copyFlies(), 0);
                Block.popResource(level, pos, drop);
            }
            // 连在这块板上的接线端子也一起收掉，不然世界里会留下没用的铜疙瘩
            if (level.getBlockEntity(master) instanceof CircuitBoardBlockEntity board) {
                board.removeTerminals();
            }
            for (BoardPart part : BoardPart.values()) {
                BlockPos partPos = part.partPos(master, facing);
                if (partPos.equals(pos)) {
                    continue;
                }
                BlockState other = level.getBlockState(partPos);
                if (other.is(this) && other.getValue(PART) == part) {
                    level.setBlock(partPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        return state;
    }
}
