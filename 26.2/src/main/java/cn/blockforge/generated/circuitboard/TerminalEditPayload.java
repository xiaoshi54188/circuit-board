package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：在端子面板里拆掉第 index 根线（action 0 = 拆线）。
 */
public record TerminalEditPayload(BlockPos pos, int index, int action) implements CustomPacketPayload {
    public static final Type<TerminalEditPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GeneratedMod.MOD_ID, "terminal_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalEditPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public TerminalEditPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new TerminalEditPayload(buffer.readBlockPos(), buffer.readVarInt(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, TerminalEditPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeVarInt(payload.index());
                    buffer.writeVarInt(payload.action());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
