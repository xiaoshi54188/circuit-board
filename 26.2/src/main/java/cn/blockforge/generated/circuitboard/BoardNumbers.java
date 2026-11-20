package cn.blockforge.generated.circuitboard;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 世界里「下一块电路板是几号」的计数器，存在存档里，所以退出游戏再进来号数也不会重来。
 *
 * <p>玩家不需要管它：放下一块新板子时，板子会自动领一个号，面板上和复制品名字里都能看到，
 * 例如「3号电路板」「3号电路板复制品1」。</p>
 */
public final class BoardNumbers extends SavedData {
    /** 存档里存的是 {"next": 下一个号}。 */
    public static final Codec<BoardNumbers> CODEC =
            Codec.INT.fieldOf("next").codec().xmap(BoardNumbers::new, BoardNumbers::next);
    public static final SavedDataType TYPE = new SavedDataType(
            Identifier.fromNamespaceAndPath(GeneratedMod.MOD_ID, "board_numbers"),
            BoardNumbers::new, CODEC, DataFixTypes.LEVEL);

    private int next = 1;

    public BoardNumbers() {
    }

    public BoardNumbers(int next) {
        this.next = Math.max(1, next);
    }

    public int next() {
        return next;
    }

    /** 领一个号。 */
    public int take() {
        int given = next;
        next++;
        setDirty();
        return given;
    }

    /** 给一块新板子领号；不是服务端时返回 0（客户端不需要号）。 */
    public static int take(Level level) {
        if (level instanceof ServerLevel server) {
            if (server.getDataStorage().computeIfAbsent(TYPE) instanceof BoardNumbers numbers) {
                return numbers.take();
            }
        }
        return 0;
    }
}
