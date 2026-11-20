package cn.blockforge.generated.circuitboard;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 电路板的物品形态：放下去的时候一次占 2x2 个方块。
 *
 * <p>拆下来的板子、复制出来的复制品都带着「设计」，所以放回去以后画好的线路还在。
 * 鼠标停在物品上会多显示几行说明，告诉新手这块板怎么用。</p>
 */
public class BoardItem extends BlockItem {
    public BoardItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }
        BlockPos master = context.getClickedPos();
        if (!level.mayInteract(player, master)) {
            return InteractionResult.FAIL;
        }
        // 点地面顶面就平着放；点墙上那一面就竖着贴上去
        Direction facing = context.getClickedFace();
        for (BoardPart part : BoardPart.values()) {
            BlockPos pos = part.partPos(master, facing);
            if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).canBeReplaced(context)) {
                player.sendSystemMessage(Component.translatable("msg.circuit_board.need_space"));
                return InteractionResult.FAIL;
            }
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BoardDesign.Data design = BoardDesign.read(stack);
        int number = design != null && design.number() > 0 ? design.number() : BoardNumbers.take(level);
        int copies = design != null ? design.copies() : 0;

        BlockState state = getBlock().defaultBlockState().setValue(CircuitBoardBlock.FACING, facing);
        // 先放主方块（板子左上角那一块），另外三块才找得到它
        for (BoardPart part : BoardPart.values()) {
            BlockPos pos = part.partPos(master, facing);
            level.setBlock(pos, state.setValue(CircuitBoardBlock.PART, part), 3);
        }
        if (level.getBlockEntity(master) instanceof CircuitBoardBlockEntity board) {
            board.applyDesign(number, copies, design != null ? design.cells() : null,
                    design != null ? design.flies() : null);
        }
        level.playSound(null, master, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.9F, 1.0F);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BoardDesign.Data design = BoardDesign.read(stack);
        if (design != null && design.number() > 0) {
            tooltip.add(Component.translatable("item.circuit_board.circuit_board.number",
                    design.number(), design.copies()).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("item.circuit_board.circuit_board.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.circuit_board.circuit_board.desc2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.circuit_board.circuit_board.desc3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
