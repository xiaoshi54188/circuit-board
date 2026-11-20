package cn.blockforge.generated.circuitboard;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 电路板物品里存的「板子设计」：面板画了什么、是几号板、已经复制过几份。
 *
 * <p>一块板子拆下来会原样掉回自己这块板（线路不会丢），用复制电路板复制出来的复制品也靠它
 * 把面板内容带走，所以放下去的时候和原来一模一样。</p>
 */
public final class BoardDesign {
    /** 存在物品自定义数据里的键名。 */
    private static final String KEY = "circuit_board_design";

    /** cells 是面板数据，flies 是面板上的飞线，number 是板号，copies 是这块板已经复制过几份。 */
    public record Data(int[] cells, int[] flies, int number, int copies) {
    }

    private BoardDesign() {
    }

    /** 造一块带设计的电路板物品；copyIndex > 0 时名字叫「N号电路板复制品M」。 */
    public static ItemStack createStack(int number, int copies, int[] cells, int[] flies, int copyIndex) {
        ItemStack stack = new ItemStack(ModRegistry.CIRCUIT_BOARD_ITEM.get());
        CompoundTag design = new CompoundTag();
        design.putIntArray("cells", cells == null ? new int[0] : cells.clone());
        design.putIntArray("flies", flies == null ? new int[0] : flies.clone());
        design.putInt("number", number);
        design.putInt("copies", copies);
        design.putInt("copy", copyIndex);
        CompoundTag root = new CompoundTag();
        root.put(KEY, design);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
        if (copyIndex > 0) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.translatable("item.circuit_board.copy_name", number, copyIndex));
        }
        return stack;
    }

    /** 读物品里的设计；普通（没设计）的电路板返回 null。 */
    public static Data read(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        CompoundTag root = custom.copyTag();
        if (!root.contains(KEY)) {
            return null;
        }
        CompoundTag design = root.getCompoundOrEmpty(KEY);
        int[] cells = design.getIntArray("cells").orElse(null);
        if (cells != null && cells.length != BoardData.INT_COUNT) {
            cells = null;
        }
        int[] flies = design.getIntArray("flies").orElse(null);
        if (flies != null && flies.length % 2 != 0) {
            flies = null;
        }
        return new Data(cells, flies, design.getIntOr("number", 0), design.getIntOr("copies", 0));
    }
}
