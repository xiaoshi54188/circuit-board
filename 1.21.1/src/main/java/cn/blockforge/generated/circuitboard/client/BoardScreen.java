package cn.blockforge.generated.circuitboard.client;

import cn.blockforge.generated.circuitboard.BoardData;
import cn.blockforge.generated.circuitboard.BoardEditPayload;
import cn.blockforge.generated.circuitboard.BoardFlyPayload;
import cn.blockforge.generated.circuitboard.BoardWirePayload;
import cn.blockforge.generated.circuitboard.CableColors;
import cn.blockforge.generated.circuitboard.CircuitBoardBlockEntity;
import cn.blockforge.generated.circuitboard.CopperWireItem;
import cn.blockforge.generated.circuitboard.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 电路板的面板界面。
 *
 * <p>普通模式：左边是绿色面板，用鼠标左键画线 / 放零件、右键转向或擦除；
 * 右边一列是零件按钮，点一下就换画笔。</p>
 *
 * <p>选针脚模式（手里拿着电线右键板子进来的）：面板四周会多出一圈针脚，
 * 直接点针脚就行，不用去世界里戳那些挤在一起的小点。</p>
 */
public class BoardScreen extends Screen {
    private static final int MARGIN = 8;
    /** 右边零件按钮的一格。 */
    private static final int BTN_W = 84;
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 2;
    /** 零件按钮排成两列，零件多了也不会顶出屏幕。 */
    private static final int PANEL_COLS = 2;
    /** 面板顶上那一行：板号 + 方位说明。 */
    private static final int HEADER_H = 14;
    /** 两段文字之间空出来的距离。 */
    private static final int LINE_GAP = 3;
    /** 底部操作提示固定留两行，工具换来换去版面也不会跳。 */
    private static final int HINT_LINES = 2;
    /** 连线面板：一根电线一行（线多了可以滚轮翻页）。 */
    private static final int WIRE_ROW_H = 20;
    private static final int WIRE_ROW_GAP = 2;
    /** 连线面板顶上那块「已选针脚 + 根数」的卡片高度，下面的列表位置才不会跳。 */
    private static final int WIRE_HEAD_H = 36;
    /** 底下「接一根新线」按钮的高度。 */
    private static final int WIRE_ADD_H = 18;
    private static final int SCROLLBAR_W = 5;

    /** 逻辑门的工具编号从这里往上排（100 + 哪一种门），普通零件直接就是类型号。 */
    private static final int TOOL_LOGIC = 100;
    /** 飞线不是插在格子里的零件，自己占一个工具编号。 */
    private static final int TOOL_FLY = 200;

    private static final int[] TOOLS = {
            BoardData.TYPE_TRACE, BoardData.TYPE_CAPACITOR, BoardData.TYPE_DIODE,
            BoardData.TYPE_TRANSISTOR, BoardData.TYPE_NOT, BoardData.TYPE_RESISTOR, BoardData.TYPE_SWITCH,
            BoardData.TYPE_AND, BoardData.TYPE_OR, BoardData.TYPE_DELAY,
            BoardData.TYPE_TIMER,
            TOOL_LOGIC + BoardData.LOGIC_NAND, TOOL_LOGIC + BoardData.LOGIC_NOR,
            TOOL_LOGIC + BoardData.LOGIC_XOR, TOOL_LOGIC + BoardData.LOGIC_XNOR,
            TOOL_FLY,
            BoardData.TYPE_EMPTY
    };
    private static final String[] TOOL_KEYS = {
            "gui.circuit_board.tool_trace",
            "gui.circuit_board.tool_capacitor",
            "gui.circuit_board.tool_diode",
            "gui.circuit_board.tool_transistor",
            "gui.circuit_board.tool_not",
            "gui.circuit_board.tool_resistor",
            "gui.circuit_board.tool_switch",
            "gui.circuit_board.tool_and",
            "gui.circuit_board.tool_or",
            "gui.circuit_board.tool_delay",
            "gui.circuit_board.tool_timer",
            "gui.circuit_board.tool_nand",
            "gui.circuit_board.tool_nor",
            "gui.circuit_board.tool_xor",
            "gui.circuit_board.tool_xnor",
            "gui.circuit_board.tool_fly",
            "gui.circuit_board.tool_erase"
    };
    private static final String[] HINT_KEYS = {
            "gui.circuit_board.hint_trace",
            "gui.circuit_board.hint_capacitor",
            "gui.circuit_board.hint_diode",
            "gui.circuit_board.hint_transistor",
            "gui.circuit_board.hint_not",
            "gui.circuit_board.hint_resistor",
            "gui.circuit_board.hint_switch",
            "gui.circuit_board.hint_and",
            "gui.circuit_board.hint_or",
            "gui.circuit_board.hint_delay",
            "gui.circuit_board.hint_timer",
            "gui.circuit_board.hint_nand",
            "gui.circuit_board.hint_nor",
            "gui.circuit_board.hint_xor",
            "gui.circuit_board.hint_xnor",
            "gui.circuit_board.hint_fly",
            "gui.circuit_board.hint_erase"
    };

    private static final int COLOR_OVERLAY = 0x8A000000;
    private static final int COLOR_FRAME = 0xFF241209;
    private static final int COLOR_FRAME_LIGHT = 0xFF7A5230;
    private static final int COLOR_BOARD = 0xFF0E3B1C;
    private static final int COLOR_GRID = 0xFF1B5E20;
    private static final int COLOR_GRID_SOFT = 0x33164D26;
    private static final int COLOR_PAD = 0xFFD9A227;
    private static final int COLOR_TRACE = 0xFF9E1B1B;
    private static final int COLOR_TRACE_CORE = 0xFFFF7043;
    private static final int COLOR_CAPACITOR = 0xFF0D47A1;
    private static final int COLOR_CAPACITOR_MARK = 0xFFFFD54F;
    private static final int COLOR_DIODE = 0xFF00695C;
    private static final int COLOR_DIODE_MARK = 0xFFB2DFDB;
    private static final int COLOR_TRANSISTOR = 0xFF37474F;
    private static final int COLOR_TRANSISTOR_PAD = 0xFFFFB300;
    private static final int COLOR_RESISTOR = 0xFF8D6E63;
    private static final int COLOR_RESISTOR_BAND = 0xFF3E2723;
    private static final int COLOR_RESISTOR_LEAD = 0xFFCFD8DC;
    private static final int COLOR_SWITCH = 0xFF6D4C41;
    private static final int COLOR_SWITCH_ON = 0xFFAEEA00;
    private static final int COLOR_GATE = 0xFF3949AB;
    private static final int COLOR_GATE_MARK = 0xFF9FA8DA;
    private static final int COLOR_DELAY = 0xFF6A1B9A;
    private static final int COLOR_DELAY_MARK = 0xFFE1BEE7;
    private static final int COLOR_JUMPER = 0xFFB0BEC5;
    private static final int COLOR_FLY_PICK = 0xFF00E676;
    private static final int COLOR_NOT = 0xFFECEFF1;
    private static final int COLOR_NOT_DOT = 0xFF263238;
    private static final int COLOR_NAND = 0xFF26A69A;
    private static final int COLOR_NOR = 0xFF7E57C2;
    private static final int COLOR_XOR = 0xFFFF7043;
    private static final int COLOR_XNOR = 0xFFFFB300;
    private static final int COLOR_TIMER = 0xFFF9A825;
    private static final int COLOR_TIMER_DIAL = 0xFFF57F17;
    private static final int COLOR_TIMER_HAND = 0xFFFFFDE7;
    private static final int COLOR_PIN_INPUT = 0xFF4FC3F7;
    private static final int COLOR_BUTTON = 0xFF263238;
    private static final int COLOR_BUTTON_ON = 0xFF1B5E20;
    private static final int COLOR_BUTTON_HOVER = 0xFF37474F;
    private static final int COLOR_TEXT = 0xFFE8F5E9;
    private static final int COLOR_TEXT_DIM = 0xFF9CCC65;
    private static final int COLOR_TEXT_FAINT = 0xFF78909C;
    private static final int COLOR_WIRE_ROW = 0xFF17331D;
    private static final int COLOR_WIRE_ROW_HOVER = 0xFF2E5A36;
    private static final int COLOR_WIRE_ROW_PENDING = 0xFF8E1414;
    private static final int COLOR_CHIP_PENDING = 0xFF5A0B0B;
    private static final int COLOR_KIND_BOARD = 0xFF9FA8DA;
    private static final int COLOR_KIND_TERMINAL = 0xFFFFD54F;
    private static final int COLOR_KIND_READ = 0xFF80CBC4;
    private static final int COLOR_SCROLL_TRACK = 0xFF3E6B47;
    private static final int COLOR_SCROLL_THUMB = 0xFFA5D6A7;
    private static final int COLOR_ADD = 0xFF1B5E20;
    private static final int COLOR_ADD_HOVER = 0xFF2E7D32;

    private final BlockPos pos;
    private final boolean wireMode;
    private final int pendingPin;

    private int[] cells = BoardData.emptyCells();
    private int tool = BoardData.TYPE_TRACE;
    private int dir;
    /** 计时器周期档位（0 最快，3 最慢）。 */
    private int speed = 1;
    private boolean dragging;
    /** 飞线：已经点好的第一格（-1 表示还没点）。 */
    private int flyStart = -1;
    /** 飞线模式下的临时提示（点了空格子之类），换成别的工具就清掉。 */
    private String flyWarn;
    /** 连线面板：现在展开的是哪根针脚（-1 表示没展开）。 */
    private int expandedPin = -1;
    /** 连线面板：点了「要删」的那根线是第几根（-1 表示没有）。 */
    private int pendingDelete = -1;
    /** 连线面板：列表往下滚了几行（线多了用滚轮翻页）。 */
    private int wireScroll;
    /** 界面是第一次打开吗（窗口大小变了会重新 init，那时候别把选中的针脚和滚动位置清掉）。 */
    private boolean initialised;

    private int cell;
    private int boardPx;
    private int strip;
    private int boardLeft;
    private int boardTop;
    private int panelLeft;
    private int panelTop;
    private int frameLeft;
    private int frameTop;
    private int frameRight;
    private int frameBottom;
    private int hintTop;

    private ItemStack[] icons = new ItemStack[0];

    public BoardScreen(BlockPos pos, boolean wireMode, int pendingPin) {
        super(Component.translatable(wireMode ? "gui.circuit_board.title_wire" : "gui.circuit_board.title"));
        this.pos = pos;
        this.wireMode = wireMode;
        this.pendingPin = pendingPin;
    }

    @Override
    protected void init() {
        super.init();
        this.strip = wireMode ? 13 : 8;
        int panelW = wireMode
                ? Math.max(PANEL_COLS * BTN_W + (PANEL_COLS - 1) * BTN_GAP, 208)
                : PANEL_COLS * BTN_W + (PANEL_COLS - 1) * BTN_GAP;
        int rows = (TOOLS.length + PANEL_COLS - 1) / PANEL_COLS;
        int panelH = rows * (BTN_H + BTN_GAP) - BTN_GAP;
        int hintH = font.lineHeight * HINT_LINES + LINE_GAP;
        int bottomH = BTN_H + 4 + hintH;

        int budgetW = this.width - (MARGIN * 2 + strip * 2 + 6 + panelW);
        int budgetH = this.height - (MARGIN * 2 + HEADER_H + strip * 2 + bottomH);
        int maxCell = Math.min(budgetW / BoardData.SIZE, budgetH / BoardData.SIZE);
        this.cell = Math.max(8, Math.min(14, maxCell));
        this.boardPx = cell * BoardData.SIZE;

        int contentH = Math.max(boardPx + strip * 2 + bottomH, panelH);
        int frameW = MARGIN * 2 + strip * 2 + boardPx + 6 + panelW;
        int frameH = MARGIN * 2 + contentH + HEADER_H;

        // 屏幕小的时候宁可贴边也别把下边的提示挤出屏幕
        this.frameLeft = Math.max(0, (this.width - frameW) / 2);
        this.frameTop = Math.max(0, (this.height - frameH) / 2);
        this.frameRight = frameLeft + frameW;
        this.frameBottom = frameTop + frameH;
        this.boardLeft = frameLeft + MARGIN + strip;
        this.boardTop = frameTop + MARGIN + HEADER_H + strip;
        this.panelLeft = boardLeft + boardPx + strip + 6;
        this.panelTop = boardTop;
        this.hintTop = boardTop + boardPx + strip + 4;

        this.icons = new ItemStack[TOOLS.length];
        for (int i = 0; i < TOOLS.length; i++) {
            icons[i] = iconFor(TOOLS[i]);
        }
        if (!initialised) {
            initialised = true;
            restorePending();
        }
        refresh();
    }

    private static ItemStack iconFor(int tool) {
        if (tool == TOOL_FLY) {
            return new ItemStack(ModRegistry.FLY_WIRE.get());
        }
        if (tool >= TOOL_LOGIC) {
            return switch (tool - TOOL_LOGIC) {
                case BoardData.LOGIC_NAND -> new ItemStack(ModRegistry.NAND_GATE.get());
                case BoardData.LOGIC_NOR -> new ItemStack(ModRegistry.NOR_GATE.get());
                case BoardData.LOGIC_XOR -> new ItemStack(ModRegistry.XOR_GATE.get());
                default -> new ItemStack(ModRegistry.XNOR_GATE.get());
            };
        }
        return switch (tool) {
            case BoardData.TYPE_CAPACITOR -> new ItemStack(ModRegistry.CAPACITOR.get());
            case BoardData.TYPE_DIODE -> new ItemStack(ModRegistry.DIODE.get());
            case BoardData.TYPE_TRANSISTOR -> new ItemStack(ModRegistry.TRANSISTOR.get());
            case BoardData.TYPE_NOT -> new ItemStack(ModRegistry.NOT_GATE.get());
            case BoardData.TYPE_RESISTOR -> new ItemStack(ModRegistry.RESISTOR.get());
            case BoardData.TYPE_SWITCH -> new ItemStack(ModRegistry.SWITCH.get());
            case BoardData.TYPE_AND -> new ItemStack(ModRegistry.AND_GATE.get());
            case BoardData.TYPE_OR -> new ItemStack(ModRegistry.OR_GATE.get());
            case BoardData.TYPE_DELAY -> new ItemStack(ModRegistry.DELAY.get());
            case BoardData.TYPE_JUMPER -> new ItemStack(ModRegistry.JUMPER.get());
            case BoardData.TYPE_TIMER -> new ItemStack(ModRegistry.TIMER.get());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // 这里故意不调用 WireHighlight.clear()：点了「要删」的那根红线关掉面板以后还要在世界里看得见，
        // 重新打开面板也会把那一根重新选中，接着点第二下就删掉。
        pendingDelete = -1;
        super.onClose();
    }

    /**
     * 重新打开面板时，把上次点了「要删」的那根线重新选中：展开它那根针脚、列表滚到它那一行。
     * 那根线要是已经被拆掉了，就把红色标记清掉。
     */
    private void restorePending() {
        CircuitBoardBlockEntity board = board();
        if (board == null) {
            return;
        }
        int index = WireHighlight.pendingBoardIndex(board.masterPos());
        int pin = WireHighlight.pendingBoardPin(board.masterPos());
        if (index < 0 || pin < 0) {
            return;
        }
        List<CircuitBoardBlockEntity.WireEntry> entries = board.wireEntriesOf(pin);
        for (int row = 0; row < entries.size(); row++) {
            if (entries.get(row).index() != index) {
                continue;
            }
            if (WireHighlight.isBoardWirePending(board.masterPos(), index, entries.get(row).wire())) {
                expandedPin = pin;
                pendingDelete = row;
                scrollPendingIntoView();
            }
            return;
        }
        // 客户端这一刻还没收到这块板的线路数据时什么都找不到：那就先别动红色标记，
        // 等数据同步过来，世界里的渲染器会自己按「编号 + 接在哪」核对，对不上就不会乱画。
    }

    private CircuitBoardBlockEntity board() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getBlockEntity(pos) instanceof CircuitBoardBlockEntity board ? board : null;
    }

    private void refresh() {
        CircuitBoardBlockEntity board = board();
        if (board != null) {
            cells = board.copyCells();
        }
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        refresh();

        graphics.fill(0, 0, this.width, this.height, COLOR_OVERLAY);

        // 边框：外面一圈深色，里面一圈铜色
        graphics.fill(frameLeft, frameTop, frameRight, frameBottom, COLOR_FRAME);
        graphics.fill(frameLeft + 2, frameTop + 2, frameRight - 2, frameBottom - 2, COLOR_FRAME_LIGHT);
        graphics.fill(frameLeft + 3, frameTop + 3, frameRight - 3, frameBottom - 3, COLOR_BOARD);

        drawHeader(graphics);
        drawBoard(graphics, mouseX, mouseY);
        if (wireMode) {
            drawPinStrip(graphics, mouseX, mouseY);
            drawWirePanel(graphics, mouseX, mouseY);
        } else {
            drawToolPanel(graphics, mouseX, mouseY);
        }
        drawFooter(graphics, mouseX, mouseY);
    }

    /** 顶上那一行：左边是这块板的名号，右边告诉新手面板和真实方向怎么对。 */
    private void drawHeader(GuiGraphics graphics) {
        CircuitBoardBlockEntity board = board();
        int number = board == null ? 0 : board.number();
        Component name = number > 0
                ? Component.translatable("gui.circuit_board.board_name", number)
                : Component.translatable("gui.circuit_board.title");
        graphics.drawString(font, name, frameLeft + MARGIN, frameTop + MARGIN - 1, COLOR_PAD);
        Component compass = compassText();
        graphics.drawString(font, compass, frameRight - MARGIN - font.width(compass), frameTop + MARGIN - 1,
                COLOR_TEXT_DIM);
    }

    /** 面板上的「上下左右」现在对着世界的哪一边（板子平放和竖着放不一样）。 */
    private Component compassText() {
        CircuitBoardBlockEntity board = board();
        Direction facing = board != null ? board.facing() : Direction.UP;
        Direction up = BoardData.downOf(facing).getOpposite();
        Direction right = BoardData.rightOf(facing);
        return Component.translatable("gui.circuit_board.compass",
                directionName(up), directionName(right), directionName(up.getOpposite()),
                directionName(right.getOpposite()));
    }

    private static Component directionName(Direction direction) {
        return switch (direction) {
            case NORTH -> Component.translatable("gui.circuit_board.north");
            case EAST -> Component.translatable("gui.circuit_board.east");
            case SOUTH -> Component.translatable("gui.circuit_board.south");
            case WEST -> Component.translatable("gui.circuit_board.west");
            case UP -> Component.translatable("gui.circuit_board.up");
            default -> Component.translatable("gui.circuit_board.down");
        };
    }

    private void drawBoard(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int row = 0; row < BoardData.SIZE; row++) {
            for (int col = 0; col < BoardData.SIZE; col++) {
                int x = boardLeft + col * cell;
                int y = boardTop + row * cell;
                drawCell(graphics, x, y, cell, BoardData.get(cells, BoardData.cellIndex(col, row)));
            }
        }
        for (int i = 0; i <= BoardData.SIZE; i++) {
            int x = boardLeft + i * cell;
            graphics.fill(x, boardTop, x + 1, boardTop + boardPx, i % 8 == 0 ? COLOR_GRID : COLOR_GRID_SOFT);
            int y = boardTop + i * cell;
            graphics.fill(boardLeft, y, boardLeft + boardPx, y + 1, i % 8 == 0 ? COLOR_GRID : COLOR_GRID_SOFT);
        }
        // 四条边的金色边框，指示这里会长针脚
        graphics.fill(boardLeft - 1, boardTop - 1, boardLeft + boardPx + 1, boardTop, COLOR_PAD);
        graphics.fill(boardLeft - 1, boardTop + boardPx, boardLeft + boardPx + 1, boardTop + boardPx + 1, COLOR_PAD);
        graphics.fill(boardLeft - 1, boardTop, boardLeft, boardTop + boardPx, COLOR_PAD);
        graphics.fill(boardLeft + boardPx, boardTop, boardLeft + boardPx + 1, boardTop + boardPx, COLOR_PAD);

        // 边上已经长了针脚的格子，画一个小铜点
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            int cellIndex = BoardData.pinCell(pin);
            if (BoardData.type(BoardData.get(cells, cellIndex)) == BoardData.TYPE_EMPTY) {
                continue;
            }
            if (!wireMode) {
                drawPad(graphics, pin, 3, COLOR_PAD);
            }
        }

        drawFlies(graphics, mouseX, mouseY);

        if (!wireMode) {
            int hovered = cellAt(mouseX, mouseY);
            if (hovered >= 0) {
                int col = BoardData.colOf(hovered);
                int row = BoardData.rowOf(hovered);
                int x = boardLeft + col * cell;
                int y = boardTop + row * cell;
                graphics.fill(x, y, x + cell, y + 1, 0xCCFFFFFF);
                graphics.fill(x, y + cell - 1, x + cell, y + cell, 0xCCFFFFFF);
                graphics.fill(x, y, x + 1, y + cell, 0xCCFFFFFF);
                graphics.fill(x + cell - 1, y, x + cell, y + cell, 0xCCFFFFFF);
            }
        }
    }

    /** 在针脚位置画一个小方块（选针脚模式下就是这个按钮）。 */
    private void drawPad(GuiGraphics graphics, int pin, int size, int color) {
        int cx = pinCentreX(pin);
        int cy = pinCentreY(pin);
        int half = size / 2;
        graphics.fill(cx - half, cy - half, cx - half + size, cy - half + size, color);
    }

    private int pinCentreX(int pin) {
        int side = BoardData.pinSide(pin);
        int along = BoardData.pinAlong(pin);
        return switch (side) {
            case 0, 2 -> boardLeft + (int) ((along + 0.5F) * cell);
            case 1 -> boardLeft + boardPx + 6;
            default -> boardLeft - 6;
        };
    }

    private int pinCentreY(int pin) {
        int side = BoardData.pinSide(pin);
        int along = BoardData.pinAlong(pin);
        return switch (side) {
            case 1, 3 -> boardTop + (int) ((along + 0.5F) * cell);
            case 0 -> boardTop - 6;
            default -> boardTop + boardPx + 6;
        };
    }

    /**
     * 面板四条边上那一圈针脚。
     *
     * <p>针脚越大说明这根针脚上挂的线越多（一根针脚可以接很多根，所以用大小一眼看出来）；
     * 颜色沿用：红=正在送电，蓝=正在收电，金=接了线，铜色=还空着。鼠标指着的那一根会亮起来。</p>
     */
    private void drawPinStrip(GuiGraphics graphics, int mouseX, int mouseY) {
        CircuitBoardBlockEntity board = board();
        if (board == null) {
            return;
        }
        int hovered = pinAt(mouseX, mouseY);
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (!board.hasPinAll(pin)) {
                continue;
            }
            int cx = pinCentreX(pin);
            int cy = pinCentreY(pin);
            boolean lit = board.isPinPowered(pin);
            boolean fed = board.isPinInput(pin);
            int wires = board.wireEntriesOf(pin).size();
            boolean wired = wires > 0;
            int color = lit ? 0xFFFF3B30 : fed ? COLOR_PIN_INPUT : wired ? 0xFFFFB300 : COLOR_PAD;
            int size = 6 + Math.min(3, wires);
            if (pin == hovered) {
                size += 2;
            }
            drawPinLeg(graphics, pin, cx, cy);
            // 深色描边 + 针脚本体，深绿色面板上也看得清
            graphics.fill(cx - size / 2 - 1, cy - size / 2 - 1, cx + size / 2 + 1, cy + size / 2 + 1,
                    0xFF101E14);
            graphics.fill(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, color);
            graphics.fill(cx - size / 2, cy - size / 2, cx + size / 2, cy - size / 2 + 1, 0xAAFFFFFF);
            if (pin == hovered) {
                // 指着的那一根：四边加一圈白框
                graphics.fill(cx - size / 2 - 3, cy - size / 2 - 3, cx + size / 2 + 3, cy - size / 2 - 2,
                        0xFFFFFFFF);
                graphics.fill(cx - size / 2 - 3, cy + size / 2 + 2, cx + size / 2 + 3, cy + size / 2 + 3,
                        0xFFFFFFFF);
                graphics.fill(cx - size / 2 - 3, cy - size / 2 - 2, cx - size / 2 - 2, cy + size / 2 + 2,
                        0xFFFFFFFF);
                graphics.fill(cx + size / 2 + 2, cy - size / 2 - 2, cx + size / 2 + 3, cy + size / 2 + 2,
                        0xFFFFFFFF);
            }
            if (pin == pendingPin) {
                graphics.fill(cx - size / 2 - 2, cy - size / 2 - 2, cx + size / 2 + 2, cy - size / 2 - 1,
                        0xFF00E676);
                graphics.fill(cx - size / 2 - 2, cy + size / 2 + 1, cx + size / 2 + 2, cy + size / 2 + 2,
                        0xFF00E676);
            }
            // 接了线的针脚，外面再点一颗小铜星，远看也知道这里有线
            if (wired) {
                int ox = switch (BoardData.pinSide(pin)) {
                    case 1 -> cx + size / 2 + 2;
                    case 3 -> cx - size / 2 - 3;
                    default -> cx;
                };
                int oy = switch (BoardData.pinSide(pin)) {
                    case 0 -> cy - size / 2 - 3;
                    case 2 -> cy + size / 2 + 2;
                    default -> cy;
                };
                graphics.fill(ox, oy, ox + 1, oy + 1, 0xFFFFE082);
            }
        }
    }

    /** 针脚和板边之间那一小截「脚」，让针脚看起来是长在板子上的。 */
    private void drawPinLeg(GuiGraphics graphics, int pin, int cx, int cy) {
        int thick = 2;
        switch (BoardData.pinSide(pin)) {
            case 0 -> graphics.fill(cx - thick / 2, boardTop - 4, cx + thick / 2, boardTop, 0xFF7A5230);
            case 1 -> graphics.fill(boardLeft + boardPx, cy - thick / 2, boardLeft + boardPx + 4, cy + thick / 2,
                    0xFF7A5230);
            case 2 -> graphics.fill(cx - thick / 2, boardTop + boardPx, cx + thick / 2, boardTop + boardPx + 4,
                    0xFF7A5230);
            default -> graphics.fill(boardLeft - 4, cy - thick / 2, boardLeft, cy + thick / 2, 0xFF7A5230);
        }
    }

    private void drawToolPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < TOOLS.length; i++) {
            int x = toolButtonX(i);
            int y = toolButtonY(i);
            boolean selected = tool == TOOLS[i];
            boolean hover = mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H;
            int color = selected ? COLOR_BUTTON_ON : hover ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
            graphics.fill(x, y, x + BTN_W, y + BTN_H, color);
            if (selected) {
                graphics.fill(x, y, x + 2, y + BTN_H, 0xFFAEEA00);
            }
            drawIcon(graphics, i, x + 3, y + (BTN_H - 16) / 2);
            graphics.drawString(font, Component.translatable(TOOL_KEYS[i]), x + 22, y + 4, COLOR_TEXT);
        }
    }

    private int toolButtonX(int index) {
        return panelLeft + (index % PANEL_COLS) * (BTN_W + BTN_GAP);
    }

    private int toolButtonY(int index) {
        return panelTop + (index / PANEL_COLS) * (BTN_H + BTN_GAP);
    }

    private void drawIcon(GuiGraphics graphics, int index, int x, int y) {
        int tool = TOOLS[index];
        if (tool == BoardData.TYPE_TRACE) {
            graphics.fill(x + 2, y + 6, x + 14, y + 10, COLOR_TRACE);
            graphics.fill(x + 3, y + 7, x + 13, y + 9, COLOR_TRACE_CORE);
            return;
        }
        if (tool == BoardData.TYPE_EMPTY) {
            graphics.fill(x + 2, y + 4, x + 14, y + 12, 0xFFCFD8DC);
            graphics.fill(x + 2, y + 11, x + 14, y + 12, 0xFF90A4AE);
            graphics.fill(x + 2, y + 4, x + 5, y + 12, 0xFFFF8A80);
            return;
        }
        if (!icons[index].isEmpty()) {
            graphics.renderItem(icons[index], x, y);
        }
    }

    // ------------------------------------------------------------------ 连线面板

    /** 连线面板的右边界。 */
    private int wirePanelRight() {
        return Math.max(panelLeft + 80, frameRight - MARGIN);
    }

    /** 列表最上面那一行。 */
    private int wireListTop() {
        return panelTop + WIRE_HEAD_H;
    }

    /** 「接一根新线」按钮的最上面（屏幕特别矮的时候也别把列表挤没了）。 */
    private int wireAddTop() {
        int bottom = frameBottom - MARGIN - font.lineHeight * 2 - 6 - WIRE_ADD_H;
        return Math.max(panelTop + WIRE_HEAD_H + WIRE_ROW_H + 6, bottom);
    }

    /** 列表下边界（再往下是按钮和提示）。 */
    private int wireListBottom() {
        return wireAddTop() - 6;
    }

    /** 列表一屏能放几行。 */
    private int visibleWireRows() {
        int available = wireListBottom() - wireListTop();
        return Math.max(1, available / (WIRE_ROW_H + WIRE_ROW_GAP));
    }

    /**
     * 连线面板（拿着电线右键板子进来的）。
     *
     * <p>左键点一根针脚，右边就展开这根针脚上接着的线：一行一根，最前面是编号。
     * 点一下某根线它会先变成「要删」的红色（世界里的那根铜线也跟着变红），
     * 再点一下才真的拆掉；右键点一下就是取消这次删除。列表最下面固定有一个
     * 「接一根新线」的按钮，点了以后关掉界面去点目标方块。</p>
     *
     * <p>一根针脚上能接多少根线不封顶，所以列表能用滚轮翻页，右侧还会画一根滚动条。</p>
     */
    private void drawWirePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = panelLeft;
        int right = wirePanelRight();
        int width = right - left;
        CircuitBoardBlockEntity board = board();
        if (board == null) {
            return;
        }
        clampWireScroll();

        // 顶部卡片：选中的是哪根针脚、上面接了几根线
        drawWireHeader(graphics, board, left, right, mouseX, mouseY);

        List<CircuitBoardBlockEntity.WireEntry> entries =
                expandedPin >= 0 ? board.wireEntriesOf(expandedPin) : List.of();

        if (expandedPin < 0) {
            int y = wireListTop() + 8;
            drawWrapped(graphics, Component.translatable("gui.circuit_board.wire_pick_pin_hint"),
                    left, y, width, COLOR_TEXT_FAINT);
            drawAddButton(graphics, mouseX, mouseY, false);
            drawWireFootHint(graphics, left, width);
            return;
        }

        int visible = visibleWireRows();
        boolean overflow = entries.size() > visible;
        int rowRight = right - (overflow ? SCROLLBAR_W + 3 : 0);
        int listBottom = wireListTop() + visible * (WIRE_ROW_H + WIRE_ROW_GAP) - WIRE_ROW_GAP;

        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.circuit_board.wire_empty"),
                    left, wireListTop() + 4, COLOR_TEXT_FAINT);
        }
        graphics.enableScissor(left, wireListTop(), rowRight, listBottom + 1);
        for (int i = 0; i < visible; i++) {
            int row = wireScroll + i;
            if (row >= entries.size()) {
                break;
            }
            int y = wireListTop() + i * (WIRE_ROW_H + WIRE_ROW_GAP);
            drawWireRow(graphics, board, entries, row, y, left, rowRight, mouseX, mouseY);
        }
        graphics.disableScissor();

        if (overflow) {
            drawWireScrollbar(graphics, rowRight + 3, wireListTop(), listBottom - WIRE_ROW_GAP,
                    entries.size(), visible);
        }
        drawAddButton(graphics, mouseX, mouseY, true);
        drawWireFootHint(graphics, left, width);
    }

    /** 顶部卡片：针脚名字 + 接了几根线 + 这一根针脚现在在送电还是收电。 */
    private void drawWireHeader(GuiGraphics graphics, CircuitBoardBlockEntity board,
                                int left, int right, int mouseX, int mouseY) {
        graphics.fill(left, panelTop, right, panelTop + WIRE_HEAD_H - 6, 0xFF0B2210);
        graphics.fill(left, panelTop, left + 2, panelTop + WIRE_HEAD_H - 6, COLOR_PAD);
        // 「手上已经选好哪根针脚」比针脚状态更要紧，有就先显示它
        Component pending = pendingPinText();
        int secondY = panelTop + 5 + font.lineHeight + 2;
        if (expandedPin < 0) {
            graphics.drawString(font, Component.translatable("gui.circuit_board.wire_no_pick"),
                    left + 6, panelTop + 5, COLOR_TEXT_DIM);
            graphics.drawString(font, pending != null
                            ? pending : Component.translatable("gui.circuit_board.wire_no_pick_2"),
                    left + 6, secondY, pending != null ? 0xFF00E676 : COLOR_TEXT_FAINT);
            return;
        }
        Component title = Component.translatable("gui.circuit_board.wire_pin_title",
                CopperWireItem.pinLabel(expandedPin), board.wireEntriesOf(expandedPin).size());
        graphics.drawString(font, title, left + 6, panelTop + 5, COLOR_PAD);
        Component state;
        int color;
        if (pending != null) {
            state = pending;
            color = 0xFF00E676;
        } else if (board.isPinPowered(expandedPin)) {
            state = Component.translatable("gui.circuit_board.wire_state_out");
            color = 0xFFFF6E6E;
        } else if (board.isPinInput(expandedPin)) {
            state = Component.translatable("gui.circuit_board.wire_state_in");
            color = COLOR_PIN_INPUT;
        } else {
            state = Component.translatable("gui.circuit_board.wire_state_idle");
            color = COLOR_TEXT_FAINT;
        }
        graphics.drawString(font, state, left + 6, secondY, color);
        // 卡片右边放一圈小灯：接了几根线就亮几个，一眼能看出这根针脚有多热闹
        int count = board.wireEntriesOf(expandedPin).size();
        int dots = Math.min(6, count);
        for (int i = 0; i < dots; i++) {
            int x = right - 8 - (dots - i) * 6;
            graphics.fill(x, panelTop + 6, x + 3, panelTop + 9, 0xFFFFC266);
        }
    }

    /** 手上已经选好了哪根针脚（还没选返回 null）。 */
    private Component pendingPinText() {
        if (pendingPin >= 0) {
            return Component.translatable("gui.circuit_board.wire_pending",
                    CopperWireItem.pinLabel(pendingPin));
        }
        if (pendingPin == -2) {
            return Component.translatable("gui.circuit_board.wire_pending_other");
        }
        return null;
    }

    /** 列表里的一行：#编号 + 线接到哪一类东西 + 坐标 + 通没通电。 */
    private void drawWireRow(GuiGraphics graphics, CircuitBoardBlockEntity board,
                             List<CircuitBoardBlockEntity.WireEntry> entries, int row, int y,
                             int left, int right, int mouseX, int mouseY) {
        CircuitBoardBlockEntity.WireEntry entry = entries.get(row);
        CircuitBoardBlockEntity.WireLink wire = entry.wire();
        boolean pending = row == pendingDelete;
        boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + WIRE_ROW_H;
        int bg = pending ? COLOR_WIRE_ROW_PENDING : hover ? COLOR_WIRE_ROW_HOVER : COLOR_WIRE_ROW;
        graphics.fill(left, y, right, y + WIRE_ROW_H, bg);
        // 左边的编号格子：底色是这根线在世界里的颜色，跟画面上那根线对得上
        graphics.fill(left, y, left + 20, y + WIRE_ROW_H,
                pending ? COLOR_CHIP_PENDING : CableColors.chip(entry.index()));
        graphics.drawCenteredString(font, Component.literal(String.valueOf(entry.index() + 1)),
                left + 10, y + 6, pending ? 0xFFFFCDD2 : COLOR_PAD);
        // 通着电的线，编号格子右边点一颗小铜灯
        if (board.isWireActive(entry.index())) {
            graphics.fill(left + 21, y + 8, left + 24, y + 11, 0xFFFFC266);
        }
        // 接到什么东西：板 / 端子 / 只读
        Component kind;
        int kindColor;
        if (wire.toBoard()) {
            kind = Component.translatable("gui.circuit_board.wire_kind_board");
            kindColor = COLOR_KIND_BOARD;
        } else if (wire.toTerminal()) {
            kind = Component.translatable("gui.circuit_board.wire_kind_terminal");
            kindColor = COLOR_KIND_TERMINAL;
        } else {
            kind = Component.translatable("gui.circuit_board.wire_kind_read");
            kindColor = COLOR_KIND_READ;
        }
        graphics.drawString(font, kind, left + 28, y + 6, pending ? 0xFFFFFFFF : kindColor);
        // 坐标靠右对齐，中间那一大段空着也不乱
        BlockPos target = wire.target();
        Component coords = Component.literal(target.getX() + ", " + target.getY() + ", " + target.getZ());
        graphics.drawString(font, coords, right - 6 - font.width(coords), y + 6,
                pending ? 0xFFFFCDD2 : COLOR_TEXT_FAINT);
        if (pending) {
            graphics.fill(left, y, left + 2, y + WIRE_ROW_H, 0xFFFF1744);
            graphics.fill(left, y + WIRE_ROW_H - 1, right, y + WIRE_ROW_H, 0xFFFF1744);
        } else if (hover) {
            graphics.fill(left, y, left + 2, y + WIRE_ROW_H, 0xFF66BB6A);
        }
    }

    /** 列表右边的滚动条（线多到一屏放不下时才有）。 */
    private void drawWireScrollbar(GuiGraphics graphics, int x, int y0, int y1, int count, int visible) {
        graphics.fill(x, y0, x + SCROLLBAR_W, y1, COLOR_SCROLL_TRACK);
        int track = Math.max(1, y1 - y0);
        int thumb = Math.max(12, track * visible / count);
        int maxScroll = Math.max(1, count - visible);
        int thumbY = y0 + (track - thumb) * wireScroll / maxScroll;
        graphics.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumb, COLOR_SCROLL_THUMB);
    }

    /** 底下那个「接一根新线」的按钮：没选针脚的时候画成灰的。 */
    private void drawAddButton(GuiGraphics graphics, int mouseX, int mouseY, boolean enabled) {
        int left = panelLeft;
        int right = wirePanelRight();
        int y = wireAddTop();
        boolean hover = enabled && mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + WIRE_ADD_H;
        graphics.fill(left, y, right, y + WIRE_ADD_H, enabled ? (hover ? COLOR_ADD_HOVER : COLOR_ADD) : 0xFF1A2A1E);
        graphics.fill(left, y, left + 2, y + WIRE_ADD_H, enabled ? 0xFF66BB6A : 0xFF33503A);
        Component label = Component.translatable(enabled
                ? "gui.circuit_board.wire_add" : "gui.circuit_board.wire_add_disabled");
        graphics.drawCenteredString(font, label, (left + right) / 2, y + 5,
                enabled ? 0xFFB9F6CA : COLOR_TEXT_FAINT);
    }

    /** 面板最底下那两行操作提示。 */
    private void drawWireFootHint(GuiGraphics graphics, int left, int width) {
        Component hint = pendingDelete >= 0
                ? Component.translatable("gui.circuit_board.wire_delete_hint")
                : Component.translatable("gui.circuit_board.wire_panel_tip");
        graphics.enableScissor(left, frameBottom - MARGIN - font.lineHeight * 2, left + width, frameBottom - MARGIN);
        drawWrapped(graphics, hint, left, frameBottom - MARGIN - font.lineHeight * 2, width,
                pendingDelete >= 0 ? 0xFFFF8A80 : COLOR_TEXT_FAINT);
        graphics.disableScissor();
    }

    /** 把滚动位置夹在合法范围里。 */
    private void clampWireScroll() {
        CircuitBoardBlockEntity board = board();
        int count = board == null || expandedPin < 0 ? 0 : board.wireEntriesOf(expandedPin).size();
        int max = Math.max(0, count - visibleWireRows());
        if (wireScroll > max) {
            wireScroll = max;
        }
        if (wireScroll < 0) {
            wireScroll = 0;
        }
    }

    /** 把待删的那根线滚到看得见的地方。 */
    private void scrollPendingIntoView() {
        int visible = visibleWireRows();
        if (pendingDelete < wireScroll) {
            wireScroll = pendingDelete;
        } else if (pendingDelete >= wireScroll + visible) {
            wireScroll = pendingDelete - visible + 1;
        }
        clampWireScroll();
    }

    /** 列表里鼠标点到了哪一行（已经算上滚动）：-1 表示没点到。 */
    private int wireRowAt(double mouseX, double mouseY) {
        if (!wireMode || expandedPin < 0) {
            return -1;
        }
        CircuitBoardBlockEntity board = board();
        if (board == null) {
            return -1;
        }
        int left = panelLeft;
        int right = wirePanelRight();
        if (mouseX < left || mouseX >= right) {
            return -1;
        }
        int count = board.wireEntriesOf(expandedPin).size();
        int visible = visibleWireRows();
        for (int i = 0; i < visible; i++) {
            int y = wireListTop() + i * (WIRE_ROW_H + WIRE_ROW_GAP);
            if (mouseY >= y && mouseY < y + WIRE_ROW_H) {
                int row = wireScroll + i;
                return row < count ? row : -1;
            }
        }
        return -1;
    }

    /** 鼠标是不是点在「接一根新线」那个按钮上。 */
    private boolean addButtonAt(double mouseX, double mouseY) {
        return wireMode && expandedPin >= 0
                && mouseX >= panelLeft && mouseX < wirePanelRight()
                && mouseY >= wireAddTop() && mouseY < wireAddTop() + WIRE_ADD_H;
    }

    /** 画一段会自动换行的字，返回下一段该从哪一行开始（按实际行数算，所以不会挤在一起）。 */
    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        graphics.drawWordWrap(font, text, x, y, width, color);
        return y + font.wordWrapHeight(text, width) + LINE_GAP;
    }

    private boolean hasAnyPin() {
        CircuitBoardBlockEntity board = board();
        if (board == null) {
            return false;
        }
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (board.hasPinAll(pin)) {
                return true;
            }
        }
        return false;
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        CircuitBoardBlockEntity board = board();
        int wires = board == null ? 0 : board.wires().size();
        // 连线的页脚只占「电路板这一列」，右边是接线面板，别压到它上面
        int width = wireMode
                ? Math.max(60, panelLeft - boardLeft - 4)
                : Math.max(60, frameRight - MARGIN - boardLeft);
        if (!wireMode) {
            int buttonW = 72;
            int y = hintTop;
            boolean hover = mouseX >= boardLeft && mouseX < boardLeft + buttonW
                    && mouseY >= y && mouseY < y + BTN_H;
            graphics.fill(boardLeft, y, boardLeft + buttonW, y + BTN_H,
                    hover ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
            Component label = tool == BoardData.TYPE_SWITCH
                    ? Component.translatable("gui.circuit_board.switch_state",
                    Component.translatable(((dir & 1) != 0) ? "gui.circuit_board.on" : "gui.circuit_board.off"))
                    : Component.translatable("gui.circuit_board.direction",
                    Component.translatable(sideKey(dir)));
            graphics.drawCenteredString(font, label, boardLeft + buttonW / 2, y + 4, COLOR_TEXT);
            int infoLeft = boardLeft + buttonW + 6;
            if (tool == BoardData.TYPE_TIMER) {
                // 选了计时器的时候多一个「周期」按钮，点一下换速度
                int periodX = boardLeft + buttonW + 4;
                int periodW = 80;
                boolean periodHover = mouseX >= periodX && mouseX < periodX + periodW
                        && mouseY >= y && mouseY < y + BTN_H;
                graphics.fill(periodX, y, periodX + periodW, y + BTN_H,
                        periodHover ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
                Component period = Component.translatable("gui.circuit_board.timer_period",
                        Component.translatable(speedKey(speed)));
                graphics.drawCenteredString(font, period, periodX + periodW / 2, y + 4, COLOR_TEXT);
                infoLeft = periodX + periodW + 6;
            }
            int number = board == null ? 0 : board.number();
            int copies = board == null ? 0 : board.copyCount();
            if (tool != BoardData.TYPE_TIMER) {
                Component info = number > 0
                        ? Component.translatable("gui.circuit_board.footer_info", number, copies, wires)
                        : Component.translatable("gui.circuit_board.wires", wires);
                graphics.drawString(font, info, infoLeft, y + 4, COLOR_TEXT_DIM);
            }
        } else {
            graphics.drawString(font, Component.translatable("gui.circuit_board.wires", wires),
                    boardLeft, hintTop + 4, COLOR_TEXT_DIM);
            int hoveredPin = pinAt(mouseX, mouseY);
            if (hoveredPin >= 0) {
                Component label = CopperWireItem.pinLabel(hoveredPin);
                graphics.drawString(font, label, boardLeft + width - font.width(label), hintTop + 4, COLOR_PAD);
            }
        }
        Component hint;
        if (wireMode && pendingDelete >= 0) {
            hint = Component.translatable("gui.circuit_board.wire_delete_hint");
        } else if (wireMode) {
            hint = Component.translatable("gui.circuit_board.wire_hint");
        } else if (tool == TOOL_FLY && flyWarn != null) {
            hint = Component.translatable(flyWarn);
        } else if (tool == TOOL_FLY && flyStart >= 0) {
            hint = Component.translatable("gui.circuit_board.fly_first_set",
                    BoardData.rowOf(flyStart) + 1, BoardData.colOf(flyStart) + 1);
        } else {
            hint = Component.translatable(HINT_KEYS[hintIndex()]);
        }
        int hintY = hintTop + BTN_H + 4;
        int hintH = font.lineHeight * HINT_LINES + LINE_GAP;
        graphics.enableScissor(boardLeft, hintY, boardLeft + width, hintY + hintH);
        drawWrapped(graphics, hint, boardLeft, hintY, width, COLOR_TEXT);
        graphics.disableScissor();
    }

    /** 当前选中的是第几个工具。 */
    private int hintIndex() {
        for (int i = 0; i < TOOLS.length; i++) {
            if (TOOLS[i] == tool) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ 格子图标

    private static void drawCell(GuiGraphics graphics, int x, int y, int size, int value) {
        int type = BoardData.type(value);
        if (type == BoardData.TYPE_EMPTY) {
            return;
        }
        int dir = BoardData.facing(value);
        boolean horizontal = dir == 1 || dir == 3;
        int pad = Math.max(1, size / 8);
        int inner = size - pad * 2;
        switch (type) {
            case BoardData.TYPE_TRACE -> {
                graphics.fill(x + pad, y + pad, x + size - pad, y + size - pad, COLOR_TRACE);
                if (size >= 9) {
                    graphics.fill(x + pad + 1, y + pad + 1, x + size - pad - 1, y + size - pad - 1, COLOR_TRACE_CORE);
                }
            }
            case BoardData.TYPE_CAPACITOR -> {
                int half = Math.max(2, inner / 3);
                if (horizontal) {
                    graphics.fill(x + pad, y + size / 2 - 1, x + size - pad, y + size / 2 + 1, COLOR_RESISTOR_LEAD);
                    graphics.fill(x + size / 2 - half, y + pad, x + size / 2 + half, y + size - pad, COLOR_CAPACITOR);
                    graphics.fill(x + size / 2 + half - 2, y + pad, x + size / 2 + half, y + size - pad,
                            COLOR_CAPACITOR_MARK);
                } else {
                    graphics.fill(x + size / 2 - 1, y + pad, x + size / 2 + 1, y + size - pad, COLOR_RESISTOR_LEAD);
                    graphics.fill(x + pad, y + size / 2 - half, x + size - pad, y + size / 2 + half, COLOR_CAPACITOR);
                    graphics.fill(x + pad, y + size / 2 + half - 2, x + size - pad, y + size / 2 + half,
                            COLOR_CAPACITOR_MARK);
                }
            }
            case BoardData.TYPE_DIODE -> {
                if (horizontal) {
                    graphics.fill(x + pad, y + size / 2 - 1, x + size - pad, y + size / 2 + 1, COLOR_RESISTOR_LEAD);
                    graphics.fill(x + size / 2 - 2, y + pad, x + size / 2 + 2, y + size - pad, COLOR_DIODE);
                } else {
                    graphics.fill(x + size / 2 - 1, y + pad, x + size / 2 + 1, y + size - pad, COLOR_RESISTOR_LEAD);
                    graphics.fill(x + pad, y + size / 2 - 2, x + size - pad, y + size / 2 + 2, COLOR_DIODE);
                }
                drawArrow(graphics, x, y, size, dir, COLOR_DIODE_MARK);
            }
            case BoardData.TYPE_TRANSISTOR -> {
                graphics.fill(x + pad + 1, y + pad + 1, x + size - pad - 1, y + size - pad - 1, COLOR_TRANSISTOR);
                // 两侧金色的是一对控制端
                if (horizontal) {
                    graphics.fill(x + pad + 1, y + pad, x + size - pad - 1, y + pad + 2, COLOR_TRANSISTOR_PAD);
                    graphics.fill(x + pad + 1, y + size - pad - 2, x + size - pad - 1, y + size - pad,
                            COLOR_TRANSISTOR_PAD);
                } else {
                    graphics.fill(x + pad, y + pad + 1, x + pad + 2, y + size - pad - 1, COLOR_TRANSISTOR_PAD);
                    graphics.fill(x + size - pad - 2, y + pad + 1, x + size - pad, y + size - pad - 1,
                            COLOR_TRANSISTOR_PAD);
                }
                drawArrow(graphics, x, y, size, dir, 0xFFFFE082);
            }
            case BoardData.TYPE_RESISTOR -> {
                int band = Math.max(1, inner / 4);
                if (horizontal) {
                    graphics.fill(x + pad, y + size / 2 - 1, x + size - pad, y + size / 2 + 1, COLOR_RESISTOR_LEAD);
                    graphics.fill(x + pad + 1, y + size / 3, x + size - pad - 1, y + size - size / 3, COLOR_RESISTOR);
                    graphics.fill(x + size / 2 - band, y + size / 3, x + size / 2 - band + 1,
                            y + size - size / 3, COLOR_RESISTOR_BAND);
                } else {
                    graphics.fill(x + size / 2 - 1, y + pad, x + size / 2 + 1, y + size - pad, COLOR_RESISTOR_LEAD);
                    graphics.fill(x + size / 3, y + pad + 1, x + size - size / 3, y + size - pad - 1, COLOR_RESISTOR);
                    graphics.fill(x + size / 3, y + size / 2 - band, x + size - size / 3,
                            y + size / 2 - band + 1, COLOR_RESISTOR_BAND);
                }
            }
            case BoardData.TYPE_SWITCH -> {
                boolean on = BoardData.switchOn(value);
                graphics.fill(x + pad + 1, y + pad + 1, x + size - pad - 1, y + size - pad - 1,
                        on ? COLOR_SWITCH_ON : COLOR_SWITCH);
                if (on) {
                    graphics.fill(x + pad + 2, y + pad + 2, x + size - pad - 2, y + size - pad - 2, 0xCC0E3B1C);
                }
                graphics.fill(x + pad + 2, y + size / 2 - 1, x + size - pad - 2, y + size / 2 + 1,
                        on ? 0xFFAEEA00 : 0xFFBCAAA4);
            }
            case BoardData.TYPE_AND, BoardData.TYPE_OR -> {
                graphics.fill(x + pad + 1, y + pad + 1, x + size - pad - 1, y + size - pad - 1, COLOR_GATE);
                if (horizontal) {
                    graphics.fill(x + pad + 1, y + pad, x + size - pad - 1, y + pad + 2, COLOR_GATE_MARK);
                    graphics.fill(x + pad + 1, y + size - pad - 2, x + size - pad - 1, y + size - pad,
                            COLOR_GATE_MARK);
                } else {
                    graphics.fill(x + pad, y + pad + 1, x + pad + 2, y + size - pad - 1, COLOR_GATE_MARK);
                    graphics.fill(x + size - pad - 2, y + pad + 1, x + size - pad, y + size - pad - 1,
                            COLOR_GATE_MARK);
                }
                if (type == BoardData.TYPE_AND) {
                    graphics.fill(x + size / 2 - 1, y + size / 2 - 1, x + size / 2 + 1, y + size / 2 + 1,
                            0xFFFFFFFF);
                }
                drawArrow(graphics, x, y, size, dir, 0xFFFFFFFF);
            }
            case BoardData.TYPE_DELAY -> {
                graphics.fill(x + pad + 1, y + pad + 1, x + size - pad - 1, y + size - pad - 1, COLOR_DELAY);
                graphics.fill(x + size / 2, y + size / 2 - 1, x + size - pad - 2, y + size / 2 + 1,
                        COLOR_DELAY_MARK);
                graphics.fill(x + size / 2 - 1, y + size / 2 - 1, x + size / 2 + 1, y + pad + 2, COLOR_DELAY_MARK);
                drawArrow(graphics, x, y, size, dir, 0xFFFFFFFF);
            }
            case BoardData.TYPE_NOT -> {
                graphics.fill(x + pad + 1, y + pad + 1, x + size - pad - 1, y + size - pad - 1, COLOR_NOT);
                int dot = Math.max(1, size / 6);
                int cx = x + size / 2;
                int cy = y + size / 2;
                switch (dir & 3) {
                    case 0 -> graphics.fill(cx - dot, y + pad, cx + dot, y + pad + dot * 2, COLOR_NOT_DOT);
                    case 2 -> graphics.fill(cx - dot, y + size - pad - dot * 2, cx + dot, y + size - pad,
                            COLOR_NOT_DOT);
                    case 1 -> graphics.fill(x + size - pad - dot * 2, cy - dot, x + size - pad, cy + dot,
                            COLOR_NOT_DOT);
                    default -> graphics.fill(x + pad, cy - dot, x + pad + dot * 2, cy + dot, COLOR_NOT_DOT);
                }
                drawArrow(graphics, x, y, size, dir, 0xFF455A64);
            }
            case BoardData.TYPE_LOGIC -> {
                int kind = BoardData.logicKind(value);
                int body = switch (kind) {
                    case BoardData.LOGIC_NAND -> COLOR_NAND;
                    case BoardData.LOGIC_NOR -> COLOR_NOR;
                    case BoardData.LOGIC_XOR -> COLOR_XOR;
                    default -> COLOR_XNOR;
                };
                graphics.fill(x + pad, y + pad, x + size - pad, y + size - pad, body);
                // 箭头两侧那两个金色小点就是两个输入
                if (horizontal) {
                    graphics.fill(x + pad, y + pad, x + size - pad, y + pad + 1, COLOR_GATE_MARK);
                    graphics.fill(x + pad, y + size - pad - 1, x + size - pad, y + size - pad, COLOR_GATE_MARK);
                } else {
                    graphics.fill(x + pad, y + pad, x + pad + 1, y + size - pad, COLOR_GATE_MARK);
                    graphics.fill(x + size - pad - 1, y + pad, x + size - pad, y + size - pad, COLOR_GATE_MARK);
                }
                // 中间的小符号：同或 / 与非 / 或非 画自己的记号，异或画个叉
                int cx = x + size / 2;
                int cy = y + size / 2;
                if (size >= 10) {
                    if (kind == BoardData.LOGIC_XOR || kind == BoardData.LOGIC_XNOR) {
                        for (int i = 0; i < 3; i++) {
                            graphics.fill(cx - 2 + i, cy - 2 + i, cx - 1 + i, cy - 1 + i, 0xFFFFFFFF);
                            graphics.fill(cx + 2 - i, cy - 2 + i, cx + 3 - i, cy - 1 + i, 0xFFFFFFFF);
                        }
                    } else if (kind == BoardData.LOGIC_NAND) {
                        graphics.fill(cx - 2, cy - 1, cx + 3, cy + 1, 0xFFFFFFFF);
                    } else if (kind == BoardData.LOGIC_NOR) {
                        graphics.fill(cx - 2, cy - 2, cx + 3, cy - 1, 0xFFFFFFFF);
                        graphics.fill(cx - 2, cy + 1, cx + 3, cy + 2, 0xFFFFFFFF);
                    }
                }
                drawArrow(graphics, x, y, size, dir, 0xFFFFFFFF);
            }
            case BoardData.TYPE_JUMPER -> {
                if (horizontal) {
                    graphics.fill(x + pad, y + size / 2 - 2, x + size - pad, y + size / 2 + 2, COLOR_JUMPER);
                    graphics.fill(x + pad, y + size / 2 + 1, x + pad + 2, y + size / 2 + 3, 0xFF90A4AE);
                    graphics.fill(x + size - pad - 2, y + size / 2 + 1, x + size - pad, y + size / 2 + 3,
                            0xFF90A4AE);
                } else {
                    graphics.fill(x + size / 2 - 2, y + pad, x + size / 2 + 2, y + size - pad, COLOR_JUMPER);
                    graphics.fill(x + size / 2 + 1, y + pad, x + size / 2 + 3, y + pad + 2, 0xFF90A4AE);
                    graphics.fill(x + size / 2 + 1, y + size - pad - 2, x + size / 2 + 3, y + size - pad,
                            0xFF90A4AE);
                }
            }
            case BoardData.TYPE_TIMER -> {
                graphics.fill(x + pad + 1, y + pad + 1, x + size - pad - 1, y + size - pad - 1, COLOR_TIMER_DIAL);
                int dial = Math.max(2, (inner - 2) / 2);
                int cx = x + size / 2;
                int cy = y + size / 2;
                graphics.fill(cx - dial, cy - dial, cx + dial, cy + dial, COLOR_TIMER);
                // 指针朝箭头那边偏一点，一眼能看出这一格从哪边出电
                if (horizontal) {
                    int hx = dir == 1 ? cx + dial - 1 : cx - dial;
                    graphics.fill(Math.min(cx, hx), cy - 1, Math.max(cx, hx) + 1, cy + 1, COLOR_TIMER_HAND);
                } else {
                    int hy = dir == 2 ? cy + dial - 1 : cy - dial;
                    graphics.fill(cx - 1, Math.min(cy, hy), cx + 1, Math.max(cy, hy) + 1, COLOR_TIMER_HAND);
                }
                // 箭头背面那个蓝色小点 = 输入：这里通电（收到信号）它才开始打钟
                int inDot = Math.max(1, size / 6);
                switch ((dir + 2) & 3) {
                    case 0 -> graphics.fill(cx - inDot, y + pad, cx + inDot, y + pad + inDot * 2,
                            COLOR_PIN_INPUT);
                    case 2 -> graphics.fill(cx - inDot, y + size - pad - inDot * 2, cx + inDot, y + size - pad,
                            COLOR_PIN_INPUT);
                    case 1 -> graphics.fill(x + size - pad - inDot * 2, cy - inDot, x + size - pad, cy + inDot,
                            COLOR_PIN_INPUT);
                    default -> graphics.fill(x + pad, cy - inDot, x + pad + inDot * 2, cy + inDot,
                            COLOR_PIN_INPUT);
                }
                drawArrow(graphics, x, y, size, dir, 0xFFFFFFFF);
            }
            default -> {
            }
        }
    }

    /** 在格子朝箭头的那一边画一个小三角，表示电流往哪边走。 */
    private static void drawArrow(GuiGraphics graphics, int x, int y, int size, int dir, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int reach = Math.max(2, size / 2 - 1);
        for (int i = 0; i < 3; i++) {
            int w = Math.max(1, (reach - i) / 2);
            switch (dir & 3) {
                case 0 -> graphics.fill(cx - w, y + i, cx + w + 1, y + i + 1, color);
                case 2 -> graphics.fill(cx - w, y + size - i - 1, cx + w + 1, y + size - i, color);
                case 1 -> graphics.fill(x + size - i - 1, cy - w, x + size - i, cy + w + 1, color);
                default -> graphics.fill(x + i, cy - w, x + i + 1, cy + w + 1, color);
            }
        }
    }

    // ------------------------------------------------------------------ 飞线

    /** 把面板上拉好的飞线画出来（每根一个颜色，跟世界里画的那根对得上；两头有小焊点），顺便高亮已经点好的那一格。 */
    private void drawFlies(GuiGraphics graphics, int mouseX, int mouseY) {
        CircuitBoardBlockEntity board = board();
        if (board != null) {
            List<CircuitBoardBlockEntity.FlyLink> flies = board.flies();
            for (int i = 0; i < flies.size(); i++) {
                CircuitBoardBlockEntity.FlyLink fly = flies.get(i);
                int color = board.isFlyPowered(i) ? CableColors.lit(i) : CableColors.of(i);
                drawLinePx(graphics, cellPxX(fly.a()), cellPxY(fly.a()), cellPxX(fly.b()), cellPxY(fly.b()), color);
                drawFlyEnd(graphics, fly.a(), color);
                drawFlyEnd(graphics, fly.b(), color);
            }
        }
        if (flyStart >= 0) {
            // 从选好的那一格到鼠标拉一根半透明的预览线，方便看出会连到哪
            int hovered = cellAt(mouseX, mouseY);
            if (tool == TOOL_FLY && hovered >= 0 && hovered != flyStart) {
                drawLinePx(graphics, cellPxX(flyStart), cellPxY(flyStart),
                        cellPxX(hovered), cellPxY(hovered), 0x80B87333);
            }
            int x = boardLeft + BoardData.colOf(flyStart) * cell;
            int y = boardTop + BoardData.rowOf(flyStart) * cell;
            graphics.fill(x - 1, y - 1, x + cell + 1, y, COLOR_FLY_PICK);
            graphics.fill(x - 1, y + cell, x + cell + 1, y + cell + 1, COLOR_FLY_PICK);
            graphics.fill(x - 1, y, x, y + cell, COLOR_FLY_PICK);
            graphics.fill(x + cell, y, x + cell + 1, y + cell, COLOR_FLY_PICK);
        }
    }

    /** 飞线两头的焊点。 */
    private void drawFlyEnd(GuiGraphics graphics, int cellIndex, int color) {
        int cx = cellPxX(cellIndex);
        int cy = cellPxY(cellIndex);
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, color);
    }

    /** 在面板上画一根粗一点的线（一格 2 像素）。 */
    private void drawLinePx(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int span = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int steps = Math.max(1, span / 2);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t);
            int y = Math.round(y0 + (y1 - y0) * t);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    private int cellPxX(int cellIndex) {
        return boardLeft + (int) ((BoardData.colOf(cellIndex) + 0.5F) * cell);
    }

    private int cellPxY(int cellIndex) {
        return boardTop + (int) ((BoardData.rowOf(cellIndex) + 0.5F) * cell);
    }

    /** 飞线模式：左键点第一格记住它，再点第二格就连起来；点同一格取消。 */
    private void clickFly(int cellIndex) {
        flyWarn = null;
        if (BoardData.type(BoardData.get(cells, cellIndex)) == BoardData.TYPE_EMPTY) {
            flyWarn = "msg.circuit_board.fly_empty";
            return;
        }
        if (flyStart < 0) {
            flyStart = cellIndex;
            return;
        }
        if (flyStart == cellIndex) {
            flyStart = -1;
            return;
        }
        PacketDistributor.sendToServer(new BoardFlyPayload(pos, flyStart, cellIndex,
                CircuitBoardBlockEntity.FLY_ADD));
        flyStart = -1;
    }

    /** 飞线模式：右键一格，把连在这一格上的那根飞线拆掉。 */
    private void removeFlyAt(int cellIndex) {
        flyStart = -1;
        flyWarn = null;
        PacketDistributor.sendToServer(new BoardFlyPayload(pos, cellIndex, cellIndex,
                CircuitBoardBlockEntity.FLY_REMOVE));
    }

    // ------------------------------------------------------------------ 鼠标

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (wireMode) {
            CircuitBoardBlockEntity board = board();
            int pin = pinAt(mx, my);
            // 右键：先取消「要删」的那根红线；没在删就把展开的针脚收起来；都没有就直接关掉面板
            if (button == 1) {
                if (pendingDelete >= 0) {
                    pendingDelete = -1;
                    WireHighlight.clear();
                } else if (expandedPin >= 0) {
                    expandedPin = -1;
                } else {
                    onClose();
                }
                return true;
            }
            if (button != 0) {
                return super.mouseClicked(mx, my, button);
            }
            // 点在「接一根新线」的按钮上：记下发线请求，关掉界面去点目标方块
            if (board != null && addButtonAt(mx, my)) {
                pendingDelete = -1;
                WireHighlight.clear();
                PacketDistributor.sendToServer(new BoardWirePayload(pos, expandedPin,
                        CopperWireItem.ACTION_CHOOSE, 0));
                onClose();
                return true;
            }
            // 点在右边的线列表上
            int row = wireRowAt(mx, my);
            if (board != null && row >= 0) {
                CircuitBoardBlockEntity.WireEntry entry = board.wireEntriesOf(expandedPin).get(row);
                if (pendingDelete == row) {
                    // 第二下：真的拆掉（编号用的是整块板电线列表里的位次，跟画红的是同一根）
                    pendingDelete = -1;
                    WireHighlight.clear();
                    PacketDistributor.sendToServer(new BoardWirePayload(pos, expandedPin,
                            CopperWireItem.ACTION_REMOVE, entry.index()));
                } else {
                    // 第一下：先变红，还没删
                    pendingDelete = row;
                    WireHighlight.markBoardWire(board.masterPos(), entry.index(), entry.wire());
                }
                return true;
            }
            // 点在针脚上：展开 / 收起它接的线
            if (pin >= 0) {
                expandedPin = expandedPin == pin ? -1 : pin;
                pendingDelete = -1;
                wireScroll = 0;
                WireHighlight.clear();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        int toolIndex = toolButtonAt(mx, my);
        if (toolIndex >= 0) {
            if (button == 0) {
                tool = TOOLS[toolIndex];
                flyStart = -1;
                flyWarn = null;
            }
            return true;
        }
        if (button == 0 && dirButtonAt(mx, my)) {
            dir = (dir + 1) & 3;
            return true;
        }
        if (button == 0 && periodButtonAt(mx, my)) {
            speed = (speed + 1) & 3;
            return true;
        }
        int cellIndex = cellAt(mx, my);
        if (cellIndex >= 0 && tool == TOOL_FLY) {
            if (button == 0) {
                clickFly(cellIndex);
                return true;
            }
            if (button == 1) {
                removeFlyAt(cellIndex);
                return true;
            }
        }
        if (cellIndex >= 0) {
            if (button == 0) {
                dragging = true;
                paint(cellIndex, false);
                return true;
            }
            if (button == 1) {
                paint(cellIndex, true);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && !wireMode) {
            int cellIndex = cellAt(mouseX, mouseY);
            if (cellIndex >= 0) {
                paint(cellIndex, false);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (wireMode && expandedPin >= 0
                && mouseX >= panelLeft && mouseX < wirePanelRight()
                && mouseY >= wireListTop() && mouseY < wireListBottom()) {
            CircuitBoardBlockEntity board = board();
            int count = board == null ? 0 : board.wireEntriesOf(expandedPin).size();
            int max = Math.max(0, count - visibleWireRows());
            if (max > 0) {
                wireScroll = Math.max(0, Math.min(max, wireScroll + (scrollY < 0 ? 1 : -1)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ 编辑

    private void paint(int cellIndex, boolean secondary) {
        int old = BoardData.get(cells, cellIndex);
        int value;
        if (secondary) {
            int oldType = BoardData.type(old);
            if (oldType == BoardData.TYPE_SWITCH) {
                value = BoardData.switchValue(!BoardData.switchOn(old));
            } else if (BoardData.hasFacing(oldType)) {
                value = BoardData.rotate(old);
            } else {
                value = BoardData.make(BoardData.TYPE_EMPTY, 0);
            }
        } else if (tool == BoardData.TYPE_EMPTY) {
            value = BoardData.make(BoardData.TYPE_EMPTY, 0);
        } else if (tool == BoardData.TYPE_TRACE) {
            value = BoardData.make(BoardData.TYPE_TRACE, 0);
        } else if (tool == BoardData.TYPE_SWITCH) {
            value = BoardData.switchValue((dir & 1) != 0);
        } else if (tool == BoardData.TYPE_TIMER) {
            value = BoardData.timerValue(dir & 3, speed);
        } else if (tool >= TOOL_LOGIC) {
            value = BoardData.logicValue(tool - TOOL_LOGIC, dir & 3);
        } else {
            value = BoardData.make(tool, dir);
        }
        if (value == old) {
            return;
        }
        BoardData.set(cells, cellIndex, value);
        CircuitBoardBlockEntity board = board();
        if (board != null) {
            board.readCells(cells);
        }
        PacketDistributor.sendToServer(new BoardEditPayload(pos, cellIndex, value));
    }

    private int cellAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - boardLeft) / cell);
        int row = (int) Math.floor((mouseY - boardTop) / cell);
        if (col < 0 || col >= BoardData.SIZE || row < 0 || row >= BoardData.SIZE) {
            return -1;
        }
        return BoardData.cellIndex(col, row);
    }

    private int toolButtonAt(double mouseX, double mouseY) {
        for (int i = 0; i < TOOLS.length; i++) {
            int x = toolButtonX(i);
            int y = toolButtonY(i);
            if (mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H) {
                return i;
            }
        }
        return -1;
    }

    private boolean dirButtonAt(double mouseX, double mouseY) {
        if (wireMode) {
            return false;
        }
        int buttonW = 72;
        return mouseX >= boardLeft && mouseX < boardLeft + buttonW
                && mouseY >= hintTop && mouseY < hintTop + BTN_H;
    }

    /** 「周期」按钮：只有选了计时器的时候才有。 */
    private boolean periodButtonAt(double mouseX, double mouseY) {
        if (wireMode || tool != BoardData.TYPE_TIMER) {
            return false;
        }
        int x = boardLeft + 72 + 4;
        return mouseX >= x && mouseX < x + 80 && mouseY >= hintTop && mouseY < hintTop + BTN_H;
    }

    private static String speedKey(int speed) {
        return switch (speed & 3) {
            case 0 -> "gui.circuit_board.speed_fast";
            case 1 -> "gui.circuit_board.speed_normal";
            case 2 -> "gui.circuit_board.speed_slow";
            default -> "gui.circuit_board.speed_very_slow";
        };
    }

    /** 选针脚模式下：鼠标指着哪根针脚（-1 表示没有）。 */
    private int pinAt(double mouseX, double mouseY) {
        if (!wireMode) {
            return -1;
        }
        CircuitBoardBlockEntity board = board();
        if (board == null) {
            return -1;
        }
        int best = -1;
        double bestDistance = Math.max(6.0D, cell * 0.8D);
        bestDistance *= bestDistance;
        for (int pin = 0; pin < BoardData.PIN_COUNT; pin++) {
            if (!board.hasPinAll(pin)) {
                continue;
            }
            double dx = pinCentreX(pin) - mouseX;
            double dy = pinCentreY(pin) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = pin;
            }
        }
        return best;
    }

    private static String sideKey(int side) {
        return switch (side & 3) {
            case 0 -> "gui.circuit_board.north";
            case 1 -> "gui.circuit_board.east";
            case 2 -> "gui.circuit_board.south";
            default -> "gui.circuit_board.west";
        };
    }
}
