package cn.blockforge.generated.circuitboard;

import cn.blockforge.generated.circuitboard.client.BoardClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "circuit_board";

    public GeneratedMod(IEventBus modBus) {
        ModRegistry.register(modBus);
        ModNetwork.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            BoardClient.init(modBus);
        }
    }
}
