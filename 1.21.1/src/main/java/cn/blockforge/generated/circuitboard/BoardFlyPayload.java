package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：在面板的「飞线」模式里点了两格。
 * action 见 {@link CircuitBoardBlockEntity#FLY_ADD} / {@link CircuitBoardBlockEntity#FLY_REMOVE}。
 */
public record BoardFlyPayload(BlockPos pos, int a, int b, int action) implements CustomPacketPayload {
    public static final Type<BoardFlyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "board_fly"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardFlyPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public BoardFlyPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new BoardFlyPayload(buffer.readBlockPos(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, BoardFlyPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeVarInt(payload.a());
                    buffer.writeVarInt(payload.b());
                    buffer.writeVarInt(payload.action());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
