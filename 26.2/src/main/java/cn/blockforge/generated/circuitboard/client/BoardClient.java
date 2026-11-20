package cn.blockforge.generated.circuitboard.client;

import cn.blockforge.generated.circuitboard.BoardSyncPayload;
import cn.blockforge.generated.circuitboard.CircuitBoardBlockEntity;
import cn.blockforge.generated.circuitboard.ModNetwork;
import cn.blockforge.generated.circuitboard.ModRegistry;
import cn.blockforge.generated.circuitboard.OpenBoardPayload;
import cn.blockforge.generated.circuitboard.OpenTerminalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** 只在客户端跑的部分：把界面的处理逻辑接到网络包上、注册针脚渲染器。 */
public final class BoardClient {
    private BoardClient() {
    }

    public static void init(IEventBus modBus) {
        ModNetwork.openBoardHandler = BoardClient::onOpen;
        ModNetwork.boardSyncHandler = BoardClient::onSync;
        ModNetwork.openTerminalHandler = BoardClient::onOpenTerminal;
        modBus.addListener(BoardClient::registerRenderers);
    }

    private static void onOpen(OpenBoardPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreenAndShow(new BoardScreen(payload.pos(), payload.wireMode(), payload.pendingPin()));
    }

    private static void onOpenTerminal(OpenTerminalPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreenAndShow(new TerminalScreen(payload.pos()));
    }

    private static void onSync(BoardSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level != null && level.getBlockEntity(payload.pos()) instanceof CircuitBoardBlockEntity board) {
            board.readCells(payload.cells());
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.BOARD_BLOCK_ENTITY.get(),
                context -> new BoardRenderer(context));
        // 跳线 / 电线两头之间那根铜线，由端子自己画
        event.registerBlockEntityRenderer(ModRegistry.TERMINAL_BLOCK_ENTITY.get(),
                context -> new WireTerminalRenderer(context));
    }
}
