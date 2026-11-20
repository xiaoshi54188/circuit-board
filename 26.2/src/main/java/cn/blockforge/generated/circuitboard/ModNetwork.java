package cn.blockforge.generated.circuitboard;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 网络注册。四种包在两端都登记（服务器和客户端要能互相认出频道），
 * 但「打开界面 / 刷新面板」的真正处理逻辑只在客户端装上，服务端时是空实现。
 */
public final class ModNetwork {
    /** 客户端才会替换成真正的实现（在 BoardClient 里）。 */
    public static Consumer<OpenBoardPayload> openBoardHandler = payload -> {
    };
    public static Consumer<BoardSyncPayload> boardSyncHandler = payload -> {
    };
    public static Consumer<OpenTerminalPayload> openTerminalHandler = payload -> {
    };

    private ModNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(BoardEditPayload.TYPE, BoardEditPayload.CODEC, ModNetwork::onEdit)
                .playToServer(BoardWirePayload.TYPE, BoardWirePayload.CODEC, ModNetwork::onWire)
                .playToServer(BoardFlyPayload.TYPE, BoardFlyPayload.CODEC, ModNetwork::onFly)
                .playToServer(TerminalEditPayload.TYPE, TerminalEditPayload.CODEC, ModNetwork::onTerminalEdit)
                .playToClient(OpenBoardPayload.TYPE, OpenBoardPayload.CODEC, (payload, context) ->
                        context.enqueueWork(() -> openBoardHandler.accept(payload)))
                .playToClient(BoardSyncPayload.TYPE, BoardSyncPayload.CODEC, (payload, context) ->
                        context.enqueueWork(() -> boardSyncHandler.accept(payload)))
                .playToClient(OpenTerminalPayload.TYPE, OpenTerminalPayload.CODEC, (payload, context) ->
                        context.enqueueWork(() -> openTerminalHandler.accept(payload)));
    }

    /** 客户端改了一格，服务端校验后写进方块实体。 */
    private static void onEdit(BoardEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(payload.pos());
        if (blockEntity instanceof CircuitBoardBlockEntity board) {
            board.applyEdit(player, payload.index(), payload.value());
        }
    }

    /** 客户端在「选针脚」界面里点了针脚。 */
    private static void onWire(BoardWirePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CopperWireItem.onWireAction(player, payload.pos(), payload.pin(), payload.action(), payload.wire());
        }
    }

    /** 客户端在端子面板里点了「拆掉第 N 根线」。 */
    private static void onTerminalEdit(TerminalEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.distanceToSqr(payload.pos().getX() + 0.5D, payload.pos().getY() + 0.5D,
                payload.pos().getZ() + 0.5D) > 64.0D) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos()) instanceof WireTerminalBlockEntity terminal) {
            if (payload.action() == 0) {
                boolean removed = terminal.removeCable(payload.index());
                player.sendSystemMessage(Component.translatable(removed
                                ? "msg.circuit_board.terminal_wire_removed"
                                : "msg.circuit_board.terminal_wire_none",
                        payload.index() + 1));
                return;
            }
            if (payload.action() == 1) {
                // 从端子这一头拆掉一根「板子针脚接过来」的线：板子那边那根也一起收掉
                List<WireTerminalBlockEntity.Drive> drives = terminal.drives();
                if (payload.index() < 0 || payload.index() >= drives.size()) {
                    player.sendSystemMessage(Component.translatable("msg.circuit_board.terminal_wire_none",
                            payload.index() + 1));
                    return;
                }
                WireTerminalBlockEntity.Drive drive = drives.get(payload.index());
                boolean onBoard = player.level().getBlockEntity(drive.board())
                        instanceof CircuitBoardBlockEntity board
                        && board.removeWireTo(drive.pin(), payload.pos());
                if (!onBoard) {
                    // 板子已经不在了（或者那根线早没了）：最少把这个端子的接法摘掉
                    terminal.removeDrive(drive.board(), drive.pin());
                }
                player.sendSystemMessage(Component.translatable("msg.circuit_board.terminal_pin_removed"));
            }
        }
    }

    /** 客户端在面板里用飞线点了两格（拉线 / 拆线）。 */
    private static void onFly(BoardFlyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(payload.pos());
        if (blockEntity instanceof CircuitBoardBlockEntity board) {
            board.applyFly(player, payload.a(), payload.b(), payload.action());
        }
    }
}
