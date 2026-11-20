package cn.blockforge.generated.circuitboard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 -> 客户端：空手右键了一个接线端子，打开「这个柱子上接了几根线」的小面板。 */
public record OpenTerminalPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenTerminalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "open_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTerminalPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public OpenTerminalPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new OpenTerminalPayload(buffer.readBlockPos());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, OpenTerminalPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
