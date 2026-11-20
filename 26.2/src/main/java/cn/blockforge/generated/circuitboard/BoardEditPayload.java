package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端 -> 服务端：在面板的第 index 格写入 value（服务端负责校验和扣电容）。 */
public record BoardEditPayload(BlockPos pos, int index, int value) implements CustomPacketPayload {
    public static final Type<BoardEditPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GeneratedMod.MOD_ID, "board_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardEditPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public BoardEditPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new BoardEditPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, BoardEditPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeVarInt(payload.index());
                    buffer.writeVarInt(payload.value());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
