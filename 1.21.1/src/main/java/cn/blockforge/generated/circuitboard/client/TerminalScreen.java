package cn.blockforge.generated.circuitboard.client;

import cn.blockforge.generated.circuitboard.CableColors;
import cn.blockforge.generated.circuitboard.CircuitBoardBlockEntity;
import cn.blockforge.generated.circuitboard.CopperWireItem;
import cn.blockforge.generated.circuitboard.TerminalEditPayload;
import cn.blockforge.generated.circuitboard.WireTerminalBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 接线端子的小面板：空手（或者拿着电线）右键那个小铜疙瘩就打开它。
 *
 * <p>这里列出这个端子上所有的接法：先列「哪几根板子针脚在给这个端子送电」，再列挂在这个端子上的线
 * （跳线 / 飞线 / 单向电线）。一根柱子上挂几根是不封顶的，每根都有编号。</p>
 *
 * <p>左键点一行，它会先变成「要删」的红色（世界里的那根铜线也跟着变红）；再点一下才真的拆掉，
 * 右键点一下就是取消这次删除。拆针脚那一行，板子那边接过来的那根线也一起收掉。</p>
 *
 * <p>每根线左边的编号底色就是这根线在世界里的颜色，号跟线一眼就对上了。</p>
 *
 * <p>线多到一屏放不下时可以用滚轮翻页；关掉面板不会取消红色，回来还能接着删。</p>
 */
public class TerminalScreen extends Screen {
    private static final int COLOR_OVERLAY = 0x8A000000;
    private static final int COLOR_FRAME = 0xFF241209;
    private static final int COLOR_FRAME_LIGHT = 0xFF7A5230;
    private static final int COLOR_BOARD = 0xFF0E3B1C;
    private static final int COLOR_ROW = 0xFF17331D;
    private static final int COLOR_ROW_HOVER = 0xFF2E5A36;
    private static final int COLOR_ROW_PENDING = 0xFF8E1414;
    private static final int COLOR_CHIP = 0xFF0B2210;
    private static final int COLOR_CHIP_PENDING = 0xFF5A0B0B;
    private static final int COLOR_TEXT = 0xFFE8F5E9;
    private static final int COLOR_TEXT_DIM = 0xFF9CCC65;
    private static final int COLOR_TEXT_FAINT = 0xFF78909C;
    private static final int COLOR_PAD = 0xFFD9A227;
    private static final int COLOR_PAD_LIT = 0xFFFFC266;
    private static final int COLOR_SCROLL = 0xFF3E6B47;
    private static final int COLOR_SCROLL_THUMB = 0xFFA5D6A7;
    private static final int COLOR_ADD_EDGE = 0xFF66BB6A;
    /** 针脚那一行的编号底色（和跳线的线区分开）。 */
    private static final int COLOR_CHIP_PIN = 0xFF3A2A08;

    private static final int PANEL_W = 252;
    private static final int ROW_H = 18;
    private static final int ROW_GAP = 2;
    private static final int PAD = 9;
    /** 面板顶部标题区的高度（两行：标题 + 接在哪、共几根）。 */
    private static final int HEAD_H = 38;
    /** 底部提示区的高度。 */
    private static final int FOOT_H = 22;
    private static final int SCROLLBAR_W = 5;

    /** 面板里的一行：要么是一根「板子针脚接过来」的线，要么是一根挂在这个端子上的线。 */
    private record Row(boolean drive, int index,
                       WireTerminalBlockEntity.Drive driveRef,
                       WireTerminalBlockEntity.Cable cable) {
    }

    private final BlockPos pos;
    private boolean initialised;
    /** 点了第一下、等着第二下确认的那一行（-1 表示没有）。 */
    private int pendingDelete = -1;
    private int scroll;

    private int left;
    private int top;
    private int right;
    private int bottom;
    private int listTop;

    public TerminalScreen(BlockPos pos) {
        super(Component.translatable("gui.circuit_board.terminal_title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        int count = rowCount();
        int available = Math.max(120, this.height - 12);
        int maxRows = Math.max(2, (available - PAD * 2 - HEAD_H - FOOT_H) / (ROW_H + ROW_GAP));
        int rows = Math.max(1, Math.min(Math.max(1, count), maxRows));
        int needed = PAD * 2 + HEAD_H + rows * (ROW_H + ROW_GAP) + FOOT_H;
        int h = Math.min(available, Math.max(126, needed));
        this.left = (this.width - PANEL_W) / 2;
        this.top = Math.max(2, (this.height - h) / 2);
        this.right = left + PANEL_W;
        this.bottom = top + h;
        this.listTop = top + PAD + HEAD_H;
        if (!initialised) {
            initialised = true;
            restorePending();
        }
        clampScroll();
    }

    /** 重新打开面板时，把上次点了「要删」的那根线重新选中（红色在关掉页面后一直留着）。 */
    private void restorePending() {
        WireTerminalBlockEntity terminal = terminal();
        if (terminal == null) {
            return;
        }
        int index = WireHighlight.pendingTerminalIndex(pos);
        if (index < 0 || index >= terminal.cableCount()) {
            return;
        }
        if (WireHighlight.isTerminalWirePending(pos, index, terminal.cable(index))) {
            // 针脚那几行排在前面，所以要往后挪
            pendingDelete = terminal.drives().size() + index;
            scrollTo(pendingDelete);
            return;
        }
        // 上次点的是「针脚那一行」：只要板子那边还标着同一根线，就把它重新选中
        for (int i = 0; i < terminal.drives().size(); i++) {
            if (isDrivePending(terminal.drives().get(i))) {
                pendingDelete = i;
                scrollTo(i);
                return;
            }
        }
    }

    /**
     * 这根针脚接线现在是不是「点了要删」的那一根。
     * 面板和板子面板共用同一份红色标记（{@link WireHighlight}），所以这里直接问板子那边。
     */
    private boolean isDrivePending(WireTerminalBlockEntity.Drive drive) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null || drive == null) {
            return false;
        }
        if (level.getBlockEntity(drive.board()) instanceof CircuitBoardBlockEntity board) {
            int pendingIndex = WireHighlight.pendingBoardIndex(board.masterPos());
            if (pendingIndex < 0 || WireHighlight.pendingBoardPin(board.masterPos()) != drive.pin()) {
                return false;
            }
            for (CircuitBoardBlockEntity.WireEntry entry : board.wireEntriesOf(drive.pin())) {
                if (entry.index() == pendingIndex && entry.wire().target().equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private WireTerminalBlockEntity terminal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getBlockEntity(pos) instanceof WireTerminalBlockEntity terminal ? terminal : null;
    }

    /** 面板里的每一行：先针脚，后线。 */
    private List<Row> buildRows() {
        WireTerminalBlockEntity terminal = terminal();
        if (terminal == null) {
            return List.of();
        }
        List<WireTerminalBlockEntity.Drive> drives = terminal.drives();
        List<WireTerminalBlockEntity.Cable> cables = terminal.cables();
        List<Row> rows = new ArrayList<>(drives.size() + cables.size());
        for (int i = 0; i < drives.size(); i++) {
            rows.add(new Row(true, i, drives.get(i), null));
        }
        for (int i = 0; i < cables.size(); i++) {
            rows.add(new Row(false, i, null, cables.get(i)));
        }
        return rows;
    }

    private int rowCount() {
        WireTerminalBlockEntity terminal = terminal();
        return terminal == null ? 0 : terminal.drives().size() + terminal.cableCount();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // 这里故意不调用 WireHighlight.clear()：红色的「要删」标记关掉面板以后还要在世界里看得见
        pendingDelete = -1;
        super.onClose();
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, COLOR_OVERLAY);
        graphics.fill(left, top, right, bottom, COLOR_FRAME);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, COLOR_FRAME_LIGHT);
        graphics.fill(left + 3, top + 3, right - 3, bottom - 3, COLOR_BOARD);

        WireTerminalBlockEntity terminal = terminal();
        List<Row> rows = buildRows();
        int count = rows.size();
        clampScroll();

        // 标题：上面是「接线端子」，下面说清楚接在哪块方块上、一共有几根线
        graphics.drawCenteredString(font, Component.translatable("gui.circuit_board.terminal_title"),
                (left + right) / 2, top + PAD, COLOR_PAD);
        Component where = terminal == null
                ? Component.translatable("gui.circuit_board.terminal_count", count)
                : Component.translatable("gui.circuit_board.terminal_where", blockName(terminal.attachedPos()),
                count);
        graphics.drawCenteredString(font, where, (left + right) / 2, top + PAD + font.lineHeight + 2, COLOR_TEXT_DIM);

        // 分隔线：标题下面一条细铜线，列表看起来是一块单独的板
        graphics.fill(left + PAD, listTop - 4, right - PAD, listTop - 3, 0x66D9A227);

        if (count == 0) {
            graphics.drawCenteredString(font, Component.translatable("gui.circuit_board.terminal_empty"),
                    (left + right) / 2, listTop + 8, COLOR_TEXT_FAINT);
            int hintY = listTop + 8 + font.lineHeight + 6;
            graphics.drawWordWrap(font, Component.translatable("gui.circuit_board.terminal_empty_hint"),
                    left + PAD, hintY, PANEL_W - PAD * 2, COLOR_TEXT_FAINT);
            drawFooter(graphics);
            return;
        }

        int visible = visibleRows();
        int rowLeft = left + PAD;
        int rowRight = right - PAD - (count > visible ? SCROLLBAR_W + 3 : 0);
        int listBottom = listTop + visible * (ROW_H + ROW_GAP) - ROW_GAP;
        graphics.enableScissor(rowLeft, listTop, rowRight, listBottom + 1);
        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            if (index >= count) {
                break;
            }
            int y = listTop + i * (ROW_H + ROW_GAP);
            drawRow(graphics, rows.get(index), index, y, rowLeft, rowRight, mouseX, mouseY);
        }
        graphics.disableScissor();

        if (count > visible) {
            drawScrollbar(graphics, rowRight + 3, listTop, listBottom, count, visible);
        }
        drawFooter(graphics);
    }

    private void drawRow(GuiGraphics graphics, Row row, int index,
                         int y, int rowLeft, int rowRight, int mouseX, int mouseY) {
        boolean pending = index == pendingDelete;
        boolean hover = mouseX >= rowLeft && mouseX < rowRight && mouseY >= y && mouseY < y + ROW_H;
        graphics.fill(rowLeft, y, rowRight, y + ROW_H,
                pending ? COLOR_ROW_PENDING : hover ? COLOR_ROW_HOVER : COLOR_ROW);
        // 左边那一格是编号：数字单独垫一块深色底，像电路板上的丝印。底色就是这根线的颜色，
        // 面板里的号跟世界里的线对得上（针脚那几行不是「这根端子上的第几根线」，画一个小铜点当「进电口」）
        int chip = pending ? COLOR_CHIP_PENDING
                : row.drive() ? COLOR_CHIP_PIN
                : row.cable() == null ? COLOR_CHIP : CableColors.chip(row.cable().color());
        graphics.fill(rowLeft, y, rowLeft + 20, y + ROW_H, chip);
        if (row.drive()) {
            graphics.fill(rowLeft + 8, y + 7, rowLeft + 12, y + 11,
                    pending ? 0xFFFFCDD2 : COLOR_PAD_LIT);
        } else {
            graphics.drawCenteredString(font, Component.literal(String.valueOf(row.index() + 1)),
                    rowLeft + 10, y + 5, pending ? 0xFFFFCDD2 : COLOR_PAD_LIT);
        }

        Component kind = row.drive()
                ? Component.translatable("gui.circuit_board.terminal_kind_pin",
                row.driveRef() == null ? Component.empty() : CopperWireItem.pinLabel(row.driveRef().pin()))
                : Component.translatable(row.cable() != null
                && row.cable().kind() == WireTerminalBlockEntity.CABLE_SOURCE
                ? "gui.circuit_board.terminal_kind_one_way"
                : "gui.circuit_board.terminal_kind_two_way");
        graphics.drawString(font, kind, rowLeft + 24, y + 5, pending ? 0xFFFFFFFF : COLOR_TEXT);

        BlockPos target = row.drive()
                ? row.driveRef() == null ? null : row.driveRef().board()
                : row.cable() == null ? null : row.cable().peer();
        if (target != null) {
            Component coords = Component.literal(target.getX() + ", " + target.getY() + ", " + target.getZ());
            int coordsWidth = font.width(coords);
            int coordsRight = rowRight - 6;
            graphics.drawString(font, coords, coordsRight - coordsWidth, y + 5,
                    pending ? 0xFFFFCDD2 : COLOR_TEXT_FAINT);
        }

        if (pending) {
            graphics.fill(rowLeft, y, rowLeft + 2, y + ROW_H, 0xFFFF1744);
            graphics.fill(rowLeft, y + ROW_H - 1, rowRight, y + ROW_H, 0xFFFF1744);
        } else if (hover) {
            graphics.fill(rowLeft, y, rowLeft + 2, y + ROW_H, COLOR_ADD_EDGE);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y0, int y1, int count, int visible) {
        graphics.fill(x, y0, x + SCROLLBAR_W, y1, COLOR_SCROLL);
        int track = Math.max(1, y1 - y0);
        int thumb = Math.max(12, track * visible / count);
        int maxScroll = Math.max(1, count - visible);
        int thumbY = y0 + (track - thumb) * scroll / maxScroll;
        graphics.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumb, COLOR_SCROLL_THUMB);
    }

    private void drawFooter(GuiGraphics graphics) {
        Component hint = pendingDelete >= 0
                ? Component.translatable("gui.circuit_board.terminal_delete_hint")
                : Component.translatable("gui.circuit_board.terminal_hint");
        graphics.fill(left + PAD, bottom - FOOT_H, right - PAD, bottom - FOOT_H + 1, 0x33D9A227);
        graphics.drawCenteredString(font, hint, (left + right) / 2, bottom - PAD - font.lineHeight, COLOR_TEXT_DIM);
    }

    /**
     * 针脚那一行被点中时，把世界里「从板子针脚拉到这个端子」的那根铜线标成红色。
     * 板子那边找不到对应的线（板子没了 / 线早拆了）就只高亮面板那一行。
     */
    private void markDrive(WireTerminalBlockEntity.Drive drive) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null || drive == null) {
            WireHighlight.clear();
            return;
        }
        if (level.getBlockEntity(drive.board()) instanceof CircuitBoardBlockEntity board) {
            for (CircuitBoardBlockEntity.WireEntry entry : board.wireEntriesOf(drive.pin())) {
                if (entry.wire().target().equals(pos)) {
                    WireHighlight.markBoardWire(board.masterPos(), entry.index(), entry.wire());
                    return;
                }
            }
        }
        WireHighlight.clear();
    }

    private static Component blockName(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return Component.empty();
        }
        return level.getBlockState(pos).getBlock().getName();
    }

    private int visibleRows() {
        int space = Math.max(ROW_H + ROW_GAP, bottom - FOOT_H - listTop);
        int fit = Math.max(1, space / (ROW_H + ROW_GAP));
        return Math.max(1, Math.min(fit, Math.max(1, rowCount())));
    }

    private void clampScroll() {
        int max = Math.max(0, rowCount() - visibleRows());
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    private void scrollTo(int index) {
        int visible = visibleRows();
        if (index < scroll) {
            scroll = index;
        } else if (index >= scroll + visible) {
            scroll = index - visible + 1;
        }
        clampScroll();
    }

    // ------------------------------------------------------------------ 鼠标

    /** 鼠标点在第几行（已经算上滚动）；-1 表示没点到。 */
    private int rowAt(double mouseX, double mouseY) {
        int rowLeft = left + PAD;
        int rowRight = right - PAD;
        if (mouseX < rowLeft || mouseX >= rowRight) {
            return -1;
        }
        int visible = visibleRows();
        for (int i = 0; i < visible; i++) {
            int y = listTop + i * (ROW_H + ROW_GAP);
            if (mouseY >= y && mouseY < y + ROW_H) {
                int index = scroll + i;
                return index < rowCount() ? index : -1;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        WireTerminalBlockEntity terminal = terminal();
        if (terminal == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 1) {
            // 右键：先取消这次删除；没有要删的就直接关掉面板
            if (pendingDelete >= 0) {
                pendingDelete = -1;
                WireHighlight.clear();
                return true;
            }
            onClose();
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        List<Row> rows = buildRows();
        int row = rowAt(mouseX, mouseY);
        if (row >= 0 && row < rows.size()) {
            if (pendingDelete == row) {
                // 第二下：真的拆掉（针脚那几行是拆板子接过来的线，线那几行是拆端子上的线）
                pendingDelete = -1;
                WireHighlight.clear();
                Row entry = rows.get(row);
                PacketDistributor.sendToServer(new TerminalEditPayload(pos, entry.index(),
                        entry.drive() ? 1 : 0));
            } else {
                // 第一下：先变红，还没删（世界里那根线也跟着变红）
                pendingDelete = row;
                Row entry = rows.get(row);
                if (entry.drive()) {
                    markDrive(entry.driveRef());
                } else if (entry.cable() != null) {
                    WireHighlight.markTerminalWire(pos, entry.index(), entry.cable());
                } else {
                    WireHighlight.clear();
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, rowCount() - visibleRows());
        if (max > 0 && mouseX >= left && mouseX < right && mouseY >= listTop && mouseY < bottom - FOOT_H) {
            scroll = Math.max(0, Math.min(max, scroll + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
