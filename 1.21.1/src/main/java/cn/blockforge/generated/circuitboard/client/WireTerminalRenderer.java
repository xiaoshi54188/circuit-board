package cn.blockforge.generated.circuitboard.client;

import cn.blockforge.generated.circuitboard.CableColors;
import cn.blockforge.generated.circuitboard.WireTerminalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 给接线端子画线：一个端子上挂着几根线，这里就画几根铜线。
 *
 * <p>跳线 / 飞线是拉到另一头的端子，单向电线是拉回它读的那个方块。通着电的线会变亮，
 * 界面里点了「要删」的那一根会画成红色。</p>
 *
 * <p>一个端子上挂好几根线的时候，每根线是<b>不同的颜色</b>（见 {@link CableColors}），
 * 哪根是哪根一眼就分得清；颜色存在线上，所以跳线的两头是同一个颜色。</p>
 *
 * <p>由板子针脚供电的端子也照样画它挂着的线（它现在也能挂跳线）；从针脚拉到这个端子的那根铜线
 * 由板子自己画，所以这里不会重复。</p>
 */
public class WireTerminalRenderer implements BlockEntityRenderer<WireTerminalBlockEntity> {
    /** 每帧重复用的一份草稿：渲染跑在渲染线程上，所以同一个渲染器复用它就行（不每帧新开数组）。 */
    private final WireTerminalRenderState state = new WireTerminalRenderState();
    /** 界面里点了「要删」的那根线（只有这个状态是红色）。 */
    private static final int COLOR_WIRE_PENDING = 0xFFFF1744;

    public WireTerminalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WireTerminalBlockEntity terminal, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        WireTerminalRenderState state = this.state;
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        state.count = 0;
        BlockPos origin = terminal.getBlockPos();
        int count = terminal.cableCount();
        state.ensure(count);
        for (int i = 0; i < count; i++) {
            WireTerminalBlockEntity.Cable cable = terminal.cable(i);
            if (cable == null) {
                continue;
            }
            boolean pending = WireHighlight.isTerminalWirePending(origin, i, cable);
            // 双向线的两头各挂着一模一样的一根，只画其中一头就够（正好省一半）。
            // 例外：这一头正标着「要删」（得自己画成红的）；另一头正标着「要删」
            // （那一头会整根画红，这一头别再叠一根铜色的上去）。
            if (cable.kind() == WireTerminalBlockEntity.CABLE_PAIR && !pending
                    && (peerDraws(terminal, cable, cameraPos)
                    || WireHighlight.isPeerWirePending(cable.peer(), cable.kind(), origin))) {
                continue;
            }
            BlockPos delta = cable.peer().subtract(origin);
            int base = state.count * 3;
            state.targets[base] = delta.getX() + 0.5F;
            state.targets[base + 1] = delta.getY() + 0.5F;
            state.targets[base + 2] = delta.getZ() + 0.5F;
            // 一个端子上挂好几根线时，每根线颜色都不一样（编号存在线里，跳线两头是同一个颜色）
            int color = cable.color() >= 0 ? cable.color() : 0;
            // 每根线按<b>自己</b>通不通电上色：同一个端子上通一根，另一根不会跟着亮
            state.colors[state.count] = terminal.isCableOn(i) ? CableColors.lit(color) : CableColors.of(color);
            state.pending[state.count] = pending;
            // 这根线离眼睛多远：近处画满细节，远处的线本来就只有几个像素宽，少画几边少分几段
            double midX = origin.getX() + 0.5 + delta.getX() * 0.5;
            double midY = origin.getY() + 0.5 + delta.getY() * 0.5;
            double midZ = origin.getZ() + 0.5 + delta.getZ() * 0.5;
            double distance = cameraPos.distanceToSqr(midX, midY, midZ);
            state.details[state.count] = (byte) (distance <= 16.0 * 16.0 ? CableGeometry.DETAIL_FULL
                    : distance <= 40.0 * 40.0 ? CableGeometry.DETAIL_MID : CableGeometry.DETAIL_LOW);
            state.count++;
        }
        if (state.count <= 0) {
            return;
        }
        // 所有铜线共用一个渲染类型，一次拿缓冲全部写进去
        draw(state, pose, buffer.getBuffer(CableGeometry.renderType()));
    }

    /** 真正把几何写进顶点缓冲。 */
    private static void draw(WireTerminalRenderState state, PoseStack pose, VertexConsumer vertices) {
        {
            PoseStack.Pose pose2 = pose.last();
            for (int i = 0; i < state.count; i++) {
                int base = i * 3;
                float bx = state.targets[base];
                float by = state.targets[base + 1];
                float bz = state.targets[base + 2];
                int detail = state.details[i];
                int color = state.pending[i] ? COLOR_WIRE_PENDING : state.colors[i];
                CableGeometry.hangingCable(vertices, pose2, 0.5F, 0.5F, 0.5F, bx, by, bz, color, detail);
                // 线头的小铜珠：起点在端子方块正中间，那一格是实心的，画了也看不见，直接省掉；
                // 另一头只在离得近的时候画一颗（远处那几个像素根本看不出有没有珠子）
                if (detail == CableGeometry.DETAIL_FULL) {
                    CableGeometry.connector(vertices, pose2, bx, by, bz, color, 6);
                }
            }
        }
    }

    /**
     * 这根双向线该不该由<b>另一头</b>去画。
     *
     * <p>同一根线的两头各挂着一模一样的一根，只画一遍就够。让「离眼睛更近的那一头」来画：
     * 两头算出来的远近是同一个数，所以不会两头都画、也不会两头都不画；也顺手躲开了
     * 「排在前面那一头离得太远、根本没进画面」这种情况。</p>
     *
     * <p>另一头没加载（不在这个世界里）时，永远由这一头画。</p>
     */
    private static boolean peerDraws(WireTerminalBlockEntity terminal, WireTerminalBlockEntity.Cable cable,
                                     Vec3 cameraPos) {
        Level level = terminal.getLevel();
        if (level == null || !level.isLoaded(cable.peer())) {
            return false;
        }
        BlockPos origin = terminal.getBlockPos();
        BlockPos peer = cable.peer();
        double mine = cameraPos.distanceToSqr(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
        double theirs = cameraPos.distanceToSqr(peer.getX() + 0.5, peer.getY() + 0.5, peer.getZ() + 0.5);
        if (mine < theirs) {
            return false;
        }
        if (mine > theirs) {
            return true;
        }
        // 一样近（很少见）：按位置定一个先后，两头算出来一定是同一个答案
        return origin.asLong() < peer.asLong();
    }

    /** 线可能拉出去三十多格，所以不能因为端子自己不在画面里就不画。 */
    @Override
    public boolean shouldRenderOffScreen(WireTerminalBlockEntity terminal) {
        return true;
    }

    @Override
    public boolean shouldRender(WireTerminalBlockEntity terminal, Vec3 cameraPos) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
