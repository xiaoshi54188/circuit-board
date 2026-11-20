package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端 -> 客户端：把整块面板的数据发给客户端（打开界面时、操作被拒绝时用）。 */
public record BoardSyncPayload(BlockPos pos, int[] cells) implements CustomPacketPayload {
    public static final Type<BoardSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GeneratedMod.MOD_ID, "board_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardSyncPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public BoardSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    BlockPos pos = buffer.readBlockPos();
                    int[] cells = buffer.readVarIntArray();
                    return new BoardSyncPayload(pos, cells);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, BoardSyncPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeVarIntArray(payload.cells());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
