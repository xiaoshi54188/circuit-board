package cn.blockforge.generated.circuitboard;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 电路板上的零件（电容 / 二极管 / 三极管 / 电阻）。
 *
 * <p>鼠标停在物品上会多显示一行灰字，说明它插到板子上以后干什么，省得新手去猜。</p>
 */
public class CircuitPartItem extends Item {
    private final String descKey;

    public CircuitPartItem(Properties properties, String descKey) {
        super(properties);
        this.descKey = descKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
    }
}
