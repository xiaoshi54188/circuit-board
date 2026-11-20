package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：在「选针脚」界面里点了第 pin 根针脚。
 *
 * <p>action 见 {@link CopperWireItem#ACTION_CHOOSE} / {@link CopperWireItem#ACTION_REMOVE} /
 * {@link CopperWireItem#ACTION_CANCEL}；wire 是这根针脚上第几根电线（从 0 数，界面上显示 +1）。</p>
 */
public record BoardWirePayload(BlockPos pos, int pin, int action, int wire) implements CustomPacketPayload {
    public static final Type<BoardWirePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "board_wire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardWirePayload> CODEC =
            new StreamCodec<>() {
                @Override
                public BoardWirePayload decode(RegistryFriendlyByteBuf buffer) {
                    return new BoardWirePayload(buffer.readBlockPos(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, BoardWirePayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeVarInt(payload.pin());
                    buffer.writeVarInt(payload.action());
                    buffer.writeVarInt(payload.wire());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
