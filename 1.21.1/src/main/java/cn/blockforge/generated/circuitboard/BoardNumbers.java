package cn.blockforge.generated.circuitboard;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 世界里「下一块电路板是几号」的计数器，存在存档里，所以退出游戏再进来号数也不会重来。
 *
 * <p>玩家不需要管它：放下一块新板子时，板子会自动领一个号，面板上和复制品名字里都能看到，
 * 例如「3号电路板」「3号电路板复制品1」。</p>
 */
public final class BoardNumbers extends SavedData {
    /** 存档文件名（存成 data/circuit_board_board_numbers.dat）。 */
    public static final String FILE_ID = "circuit_board_board_numbers";

    /** 1.21.1 的存档数据靠这个「工厂」来描述：怎么新建、怎么读回来。 */
    public static final SavedData.Factory<BoardNumbers> FACTORY = new SavedData.Factory<>(
            BoardNumbers::new, BoardNumbers::load, DataFixTypes.LEVEL);

    private int next = 1;

    public BoardNumbers() {
    }

    public BoardNumbers(int next) {
        this.next = Math.max(1, next);
    }

    /** 从存档里读：只有一项 {"next": 下一个号}。 */
    private static BoardNumbers load(CompoundTag tag, HolderLookup.Provider registries) {
        return new BoardNumbers(tag.getInt("next"));
    }

    /** 写进存档。 */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", next);
        return tag;
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
            return server.getDataStorage().computeIfAbsent(FACTORY, FILE_ID).take();
        }
        return 0;
    }
}
