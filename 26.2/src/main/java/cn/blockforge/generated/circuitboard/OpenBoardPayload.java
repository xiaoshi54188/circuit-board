package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：打开某块电路板的界面。
 *
 * <p>wireMode 为真时打开的是「选针脚」界面（手里拿着电线点的），pendingPin 是这次已经选好的针脚（-1 表示还没选）。</p>
 */
public record OpenBoardPayload(BlockPos pos, boolean wireMode, int pendingPin) implements CustomPacketPayload {
    public static final Type<OpenBoardPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GeneratedMod.MOD_ID, "open_board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBoardPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public OpenBoardPayload decode(RegistryFriendlyByteBuf buffer) {
                    BlockPos pos = buffer.readBlockPos();
                    boolean wireMode = buffer.readBoolean();
                    int pendingPin = buffer.readVarInt() - 1;
                    return new OpenBoardPayload(pos, wireMode, pendingPin);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, OpenBoardPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeBoolean(payload.wireMode());
                    buffer.writeVarInt(payload.pendingPin() + 1);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
