package cn.blockforge.generated.circuitboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 跳线 / 飞线：拿在手里点两下，就能把两个红石方块远距离接成一根线。
 *
 * <p>用法：右键第一个方块（红石灯、红石粉、中继器都行），再右键第二个方块。两头会各长出一个接头，
 * 中间拉一根铜线；<b>两头都能当电源</b>——哪一头有电，另一头就跟着有电，跟一根真的电线一样。</p>
 *
 * <p>一个方块的六个面就是六个「插口」，同一个插口（一个接线端子）上还能挂好几根线：
 * 想在已经接了线的地方再加一根，对着同一个面再点两下就行。</p>
 *
 * <p>拆线改成了「按编号拆」：<b>空手右键那个小铜疙瘩</b>会打开一个面板，里面列出这个端子上
 * 接的所有线（1、2、3……），点一下选中会变红，再点一下才真的拆掉，右键取消。</p>
 *
 * <p>线接在哪一头，就只认那一头点到的方块：接头长在方块旁边，它旁边就算又放了别的方块，
 * 也不会被当成线的另一头、更不会被当成电源。</p>
 *
 * <p>和电线的区别：电线是「从这头往那头单向送电」，跳线是「两头连成一根线、双向都通」。</p>
 */
public class JumperItem extends Item {
    /** 说明文字用的语言键前缀（跳线是 item.circuit_board.jumper，飞线是 item.circuit_board.fly_wire）。 */
    private final String descKey;
    /** 已经点好的那一头：方块位置 + 点的是哪一面（面就是插口）。 */
    private record Pending(BlockPos pos, Direction face) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public JumperItem(Properties properties, String descKey) {
        super(properties);
        this.descKey = descKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(descKey + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(descKey + ".desc2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(descKey + ".desc3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 原版在把右键交给物品之前，会先让「方块自己」处理这一下。红石粉就是在这里被切成十字 / 圆点的，
     * 木门 / 栅栏门 / 活板门也是在这里直接被开开的，所以拿着飞线点它们时根本轮不到 {@link #useOn}。
     * 这些方块也得能被飞线连上，就得在方块之前接手：这一下我们要先看。
     *
     * <p>门、栅栏门、活板门<b>六个面都归电线</b>：点在正面 / 侧面 / 顶面 / 底面都能拉线。
     * 想开关门就空手右键，那一下照旧是门自己的。</p>
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState clickedState = level.getBlockState(clicked);
        if (clickedState.is(Blocks.REDSTONE_WIRE)) {
            return interact(context, clicked, face);
        }
        if (!LinkSupport.eatsRightClick(clickedState)) {
            // 别的方块还是走原来的路（箱子、工作台、拉杆该干嘛干嘛）
            return InteractionResult.PASS;
        }
        BlockPos door = LinkSupport.doorAttach(level, clicked, clickedState);
        if (door == null) {
            return InteractionResult.PASS;
        }
        // 点门沿 / 门面本来都是「开门」，第一次接线时顺手提一句怎么开门（只发在物品栏上方，不刷聊天栏）
        if (!level.isClientSide() && context.getPlayer() instanceof ServerPlayer player
                && LinkSupport.handOpened(clickedState) && LinkSupport.doorSideFace(face)
                && PENDING.get(player.getUUID()) == null) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.door_hand_hint"), true);
        }
        return interact(context, door, face);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return interact(context, context.getClickedPos(), context.getClickedFace());
    }

    /**
     * 真正接线的那一段。{@code clicked} / {@code face} 是「逻辑上点的是哪一格、哪一面」：
     * 点木门时它已经被换算成整扇门最上面那一格。
     */
    private InteractionResult interact(UseOnContext context, BlockPos clicked, Direction face) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            // 客户端只要把这一下吃掉就行（别让红石粉在本地被切成十字、别让门先在本地开一下），
            // 真正干活的是服务端
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        // 点在小铜疙瘩上：直接当成点它贴着的那块方块的这一面（板子针脚供电的端子也一样能挂跳线）
        if (level.getBlockEntity(clicked) instanceof WireTerminalBlockEntity terminal) {
            clicked = terminal.attachedPos();
            face = terminal.facing().getOpposite();
        }
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            // 这一面已经有一个端子了就接着往上挂线；没有的话得先找个空位装端子
            if (LinkSupport.linkTerminalOnFace(level, clicked, face) == null
                    && !LinkSupport.faceFree(level, clicked, face)) {
                player.sendSystemMessage(Component.translatable("msg.circuit_board.link_face_blocked"));
                return InteractionResult.SUCCESS;
            }
            PENDING.put(player.getUUID(), new Pending(clicked.immutable(), face));
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_pick_source",
                    LinkSupport.MAX_LINK_DISTANCE));
            return InteractionResult.SUCCESS;
        }
        return link(level, player, pending, clicked, face);
    }

    /** 已经记了第一头：把它和点下去的这一头接成一根线（同一面可以接好几根）。 */
    private InteractionResult link(Level level, ServerPlayer player, Pending pending,
                                   BlockPos clicked, Direction face) {
        BlockPos first = pending.pos();
        Direction firstFace = pending.face();
        if (first.equals(clicked)) {
            PENDING.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_cancel"));
            return InteractionResult.SUCCESS;
        }
        if (!LinkSupport.withinRange(first, clicked)) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_too_far",
                    LinkSupport.MAX_LINK_DISTANCE));
            return InteractionResult.SUCCESS;
        }
        BlockPos firstTerminal = first.relative(firstFace);
        BlockPos secondTerminal = clicked.relative(face);
        if (firstTerminal.equals(secondTerminal)) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_no_room"));
            return InteractionResult.SUCCESS;
        }
        boolean firstHas = LinkSupport.linkTerminalOnFace(level, first, firstFace) != null;
        boolean secondHas = LinkSupport.linkTerminalOnFace(level, clicked, face) != null;
        // 还没有端子的那一面得有空位，不然装不上
        if ((!firstHas && !LinkSupport.faceFree(level, first, firstFace))
                || (!secondHas && !LinkSupport.faceFree(level, clicked, face))) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_face_blocked"));
            return InteractionResult.SUCCESS;
        }
        if (firstHas && secondHas && LinkSupport.hasCable(level, firstTerminal, secondTerminal)) {
            PENDING.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_exists"));
            return InteractionResult.SUCCESS;
        }
        // facing 是「从端子指向它贴着的方块」，也就是点的那一面的反方向
        if (!WireTerminalBlock.placePair(level, firstTerminal, firstFace.getOpposite(),
                secondTerminal, face.getOpposite())) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_no_room"));
            return InteractionResult.SUCCESS;
        }
        PENDING.remove(player.getUUID());
        if (!player.isCreative()) {
            consumeSelf(player);
        }
        player.sendSystemMessage(Component.translatable("msg.circuit_board.link_ok",
                new ItemStack(this).getHoverName()));
        // 顺手把两边都惊动一下，红石灯立刻就能看到新接的线
        level.updateNeighborsAt(firstTerminal, ModRegistry.WIRE_TERMINAL.get());
        level.updateNeighborsAt(secondTerminal, ModRegistry.WIRE_TERMINAL.get());
        return InteractionResult.SUCCESS;
    }

    /** 用掉手里这个工具一个（跳线用掉跳线，飞线用掉飞线）。 */
    private void consumeSelf(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(this)) {
                stack.shrink(1);
                inventory.setChanged();
                return;
            }
        }
    }
}
