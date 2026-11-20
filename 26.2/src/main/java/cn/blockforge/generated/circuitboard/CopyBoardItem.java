package cn.blockforge.generated.circuitboard;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 复制电路板：拿着它蹲下右键一块已经画好的电路板，就能复制出一份。
 *
 * <p>复制品的名字是「3号电路板复制品1」「3号电路板复制品2」这样按顺序排下去的，
 * 放下去以后线路和原来那块一模一样，原件上的号也一起带过去。</p>
 */
public class CopyBoardItem extends Item {
    public CopyBoardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("item.circuit_board.copy_board.desc").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.circuit_board.copy_board.desc2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            BlockPos pos = context.getClickedPos();
            if (!context.getLevel().getBlockState(pos).is(ModRegistry.CIRCUIT_BOARD.get())) {
                player.sendSystemMessage(Component.translatable("msg.circuit_board.copy_not_board"));
                return InteractionResult.SUCCESS;
            }
            tryCopy(context.getLevel(), pos, player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** 蹲下右键板子时真正干活的部分；返回有没有复制成功。 */
    public static boolean tryCopy(Level level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModRegistry.CIRCUIT_BOARD.get())) {
            return false;
        }
        if (!player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.copy_sneak"));
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof CircuitBoardBlockEntity hit)) {
            return false;
        }
        CircuitBoardBlockEntity board = hit.master();
        if (board == null) {
            return false;
        }
        int number = board.number();
        if (number <= 0) {
            board.ensureNumber();
            number = board.number();
        }
        int copy = board.nextCopyNumber();
        ItemStack copyStack = BoardDesign.createStack(number, copy, board.copyCells(), board.copyFlies(), copy);
        if (!player.getInventory().add(copyStack)) {
            player.drop(copyStack, false);
        }
        player.sendSystemMessage(Component.translatable("msg.circuit_board.copied", number, copy));
        return true;
    }
}
