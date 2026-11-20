package cn.blockforge.generated.circuitboard.client;

import cn.blockforge.generated.circuitboard.GeneratedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * 画铜线用的一小套几何工具：一根圆的铜线 + 一颗线头小铜珠 + 小方块 + 四边形。
 *
 * <p>电路板上的电线、跳线两头之间的那根线，都用这里的同一套画法，看起来才是一根线。</p>
 *
 * <p>线是<b>圆的</b>：截面 8 边形、每个顶点的法线都朝外，画出来是一圈圈圆滑的明暗，
 * 不会像方棱那样一道亮一道暗（那样看着又硬又尖）。曲线也按长度自动多分几段，
 * 长线不会拉成一段折线。</p>
 *
 * <p>为什么不用原版的「调试四边形」渲染类型：那个类型没有贴图、也没有法线和光照，光影（Iris / OptiFine
 * 这类）会用自己的一套渲染程序去画，结果就是开了光影这些线整根消失。这里改成「实体」类的渲染类型：
 * 带一块自己的贴图、每个顶点都写 UV、光照和法线，光影会照常当成实体几何去画，所以开不开光影都看得见。</p>
 */
public final class CableGeometry {
    /** 铜缆的粗细（方块为单位）。 */
    public static final float CABLE_RADIUS = 0.05F;

    /** 线头小铜珠的半径。 */
    private static final float CONNECTOR_RADIUS = 0.0625F;

    /** 铜线 / 针脚 / 零件共用的一张小贴图（浅色，真正上色靠顶点颜色）。 */
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/block/cable.png");

    /** 所有顶点都取贴图正中那一格：颜色由顶点颜色决定，贴图只负责「有贴图」。 */
    private static final float UV = 0.5F;

    /** 圆管的截面边数：8 边形配上平滑法线，看着就是一根圆的铜线。 */
    private static final int TUBE_SIDES = 8;

    /** 线头小铜珠的经线条数。 */
    private static final int BEAD_SIDES = 8;

    /**
     * 画面里的线分三档粗细 / 段数：看得近就用满细节，离得远就少画几边、少分几段。
     *
     * <p>一根线最贵的是「截面几个边 × 分几段」：8 边 9 段就是 72 个四边形，4 边 4 段只有 16 个。
     * 远处的线本来就只有几个像素宽，画满细节纯属白花显卡——满世界的线一起画的时候，这就是卡的原因。
     * 近处照旧是圆的、细腻的。</p>
     */
    public static final int DETAIL_FULL = 2;
    public static final int DETAIL_MID = 1;
    public static final int DETAIL_LOW = 0;

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    /**
     * 画铜线 / 针脚 / 零件用的渲染类型。<b>只建一次，之后一直复用</b>。
     *
     * <p>这个渲染类型原来是每次画图都现建一个新的：一块板子每帧要建四五次、满世界的端子每帧各建一次。
     * 现建出来的每个都是新对象，游戏没法把它们合成一批画，等于每根线都多一次绘制提交；
     * 复用同一个对象以后，所有铜线会并进同一批。</p>
     */
    private static final RenderType WIRE_RENDER_TYPE = RenderTypes.entitySolid(TEXTURE);

    private CableGeometry() {
    }

    /** 画铜线和针脚用的渲染类型（光影也认得的实体渲染类型）。 */
    public static RenderType renderType() {
        return WIRE_RENDER_TYPE;
    }

    /** 一根带一点自然下垂的铜缆：从起点垂到中间，再抬到终点（满细节）。 */
    public static void hangingCable(VertexConsumer vertices, PoseStack.Pose pose,
                                    float ax, float ay, float az, float bx, float by, float bz,
                                    int color) {
        hangingCable(vertices, pose, ax, ay, az, bx, by, bz, color, DETAIL_FULL);
    }

    /** 一根下垂的铜缆，{@code detail} 是 {@link #DETAIL_FULL} / {@link #DETAIL_MID} / {@link #DETAIL_LOW}。 */
    public static void hangingCable(VertexConsumer vertices, PoseStack.Pose pose,
                                    float ax, float ay, float az, float bx, float by, float bz,
                                    int color, int detail) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        float sag = Math.min(0.22F, 0.04F + length * 0.02F);
        float cx = (ax + bx) * 0.5F;
        float cy = (ay + by) * 0.5F - sag;
        float cz = (az + bz) * 0.5F;
        int steps = curveSteps(length, detailMinSteps(detail), detailMaxSteps(detail));
        int sides = detailSides(detail);
        float px = ax;
        float py = ay;
        float pz = az;
        for (int step = 1; step <= steps; step++) {
            float t = step / (float) steps;
            float u = 1.0F - t;
            float qx = u * u * ax + 2.0F * u * t * cx + t * t * bx;
            float qy = u * u * ay + 2.0F * u * t * cy + t * t * by;
            float qz = u * u * az + 2.0F * u * t * cz + t * t * bz;
            cable(vertices, pose, px, py, pz, qx, qy, qz, CABLE_RADIUS, color, sides);
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    /**
     * 一根向上拱起的铜缆：飞线架在板子上空，所以是往上拱、不是往下垂。
     */
    public static void archedCable(VertexConsumer vertices, PoseStack.Pose pose,
                                   float ax, float ay, float az, float bx, float by, float bz,
                                   int color) {
        archedCable(vertices, pose, ax, ay, az, bx, by, bz, color, DETAIL_FULL);
    }

    /** 一根向上拱起的铜缆，{@code detail} 同上。 */
    public static void archedCable(VertexConsumer vertices, PoseStack.Pose pose,
                                   float ax, float ay, float az, float bx, float by, float bz,
                                   int color, int detail) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        float arch = Math.min(0.36F, 0.07F + length * 0.06F);
        float cx = (ax + bx) * 0.5F;
        float cy = (ay + by) * 0.5F + arch;
        float cz = (az + bz) * 0.5F;
        int steps = curveSteps(length, detailMinSteps(detail), detailMaxSteps(detail) + 1);
        int sides = detailSides(detail);
        float px = ax;
        float py = ay;
        float pz = az;
        for (int step = 1; step <= steps; step++) {
            float t = step / (float) steps;
            float u = 1.0F - t;
            float qx = u * u * ax + 2.0F * u * t * cx + t * t * bx;
            float qy = u * u * ay + 2.0F * u * t * cy + t * t * by;
            float qz = u * u * az + 2.0F * u * t * cz + t * t * bz;
            cable(vertices, pose, px, py, pz, qx, qy, qz, CABLE_RADIUS, color, sides);
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    /** 这一档细节画几边。 */
    public static int detailSides(int detail) {
        return detail >= DETAIL_FULL ? TUBE_SIDES : detail == DETAIL_MID ? 6 : 4;
    }

    /** 这一档细节最少分几段（太少了会看出折线）。 */
    private static int detailMinSteps(int detail) {
        return detail >= DETAIL_FULL ? 3 : 2;
    }

    /** 这一档细节最多分几段。 */
    private static int detailMaxSteps(int detail) {
        return detail >= DETAIL_FULL ? 9 : detail == DETAIL_MID ? 6 : 4;
    }

    /** 一根线分几段画：线越长分段越多（这样弯的地方才是圆的），两头都夹住，别多到浪费。 */
    private static int curveSteps(float length, int min, int max) {
        int steps = 2 + (int) (length * 3.0F);
        return Math.max(min, Math.min(max, steps));
    }

    /**
     * 从一点到另一点画一段圆的铜线（截面 8 边形，顶点法线朝外 → 明暗圆滑过渡）。
     */
    public static void cable(VertexConsumer vertices, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float r, int color) {
        cable(vertices, pose, ax, ay, az, bx, by, bz, r, color, TUBE_SIDES);
    }

    /** 同上，但截面画几边由调用方定（离得远就少画几边）。 */
    public static void cable(VertexConsumer vertices, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float r, int color, int sides) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        // 拿「上」当参考算出截面的两根轴。线基本都是横着拉的，相邻两段用的参考是同一个，
        // 所以每一段的截面都对得上，不会拧成麻花。
        float rx = 0.0F;
        float ry = 1.0F;
        float rz = 0.0F;
        if (Math.abs(dy) > 0.95F) {
            // 线是竖着的：这时「上」和线的方向平行，换一个参考
            rx = 1.0F;
            ry = 0.0F;
        }
        float ux = ry * dz - rz * dy;
        float uy = rz * dx - rx * dz;
        float uz = rx * dy - ry * dx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0E-6F) {
            return;
        }
        ux /= ul;
        uy /= ul;
        uz /= ul;
        // v = 方向 × u：u、v、方向 三个轴互相垂直
        float vx = dy * uz - dz * uy;
        float vy = dz * ux - dx * uz;
        float vz = dx * uy - dy * ux;
        float ca = 1.0F;
        float sa = 0.0F;
        for (int side = 0; side < sides; side++) {
            float angle = (side + 1) * TWO_PI / sides;
            float cb = (float) Math.cos(angle);
            float sb = (float) Math.sin(angle);
            // 截面上第 side 个、第 side+1 个方向，同时也是这两个顶点朝外的法线
            float n0x = ux * ca + vx * sa;
            float n0y = uy * ca + vy * sa;
            float n0z = uz * ca + vz * sa;
            float n1x = ux * cb + vx * sb;
            float n1y = uy * cb + vy * sb;
            float n1z = uz * cb + vz * sb;
            // 一头两个点、另一头两个点，拼成一条管壁
            quadN(vertices, pose,
                    ax + n0x * r, ay + n0y * r, az + n0z * r, n0x, n0y, n0z,
                    ax + n1x * r, ay + n1y * r, az + n1z * r, n1x, n1y, n1z,
                    bx + n1x * r, by + n1y * r, bz + n1z * r, n1x, n1y, n1z,
                    bx + n0x * r, by + n0y * r, bz + n0z * r, n0x, n0y, n0z, color);
            ca = cb;
            sa = sb;
        }
    }

    /** 线头的小铜珠，让线头看得见（圆珠子，比原来的小方块细腻）。 */
    public static void connector(VertexConsumer vertices, PoseStack.Pose pose,
                                 float x, float y, float z, int color) {
        bead(vertices, pose, x, y, z, CONNECTOR_RADIUS, color, BEAD_SIDES);
    }

    /** 同上，但珠子少画几圈（远处的线头不用那么圆）。 */
    public static void connector(VertexConsumer vertices, PoseStack.Pose pose,
                                 float x, float y, float z, int color, int sides) {
        bead(vertices, pose, x, y, z, CONNECTOR_RADIUS, color, sides);
    }

    /**
     * 一颗低面数的小圆珠：上下各一圈纬度线加两个极点。
     *
     * <p>面数不多（8 条经线 × 3 圈），但每个顶点的法线都是「从球心指出去」的方向，
     * 所以看着是圆的，不像方块那样一圈愣角。</p>
     */
    private static void bead(VertexConsumer vertices, PoseStack.Pose pose,
                             float cx, float cy, float cz, float r, int color, int sides) {
        float ringY = r * 0.5F;
        float ringR = r * 0.8660254F;
        // 圈上顶点的法线 = 它相对球心的方向（这两个数正好是 0.866 和 0.5）
        float ny = ringY / r;
        float nr = ringR / r;
        float ca = 1.0F;
        float sa = 0.0F;
        for (int side = 0; side < sides; side++) {
            float angle = (side + 1) * TWO_PI / sides;
            float cb = (float) Math.cos(angle);
            float sb = (float) Math.sin(angle);
            float x0 = cx + ringR * ca;
            float z0 = cz + ringR * sa;
            float x1 = cx + ringR * cb;
            float z1 = cz + ringR * sb;
            // 顶盖：极点 + 上圈两个点
            tri(vertices, pose,
                    cx, cy + r, cz, 0.0F, 1.0F, 0.0F,
                    x1, cy + ringY, z1, nr * cb, ny, nr * sb,
                    x0, cy + ringY, z0, nr * ca, ny, nr * sa, color);
            // 中间那一圈
            quadN(vertices, pose,
                    x0, cy + ringY, z0, nr * ca, ny, nr * sa,
                    x1, cy + ringY, z1, nr * cb, ny, nr * sb,
                    x1, cy - ringY, z1, nr * cb, -ny, nr * sb,
                    x0, cy - ringY, z0, nr * ca, -ny, nr * sa, color);
            // 底盖
            tri(vertices, pose,
                    cx, cy - r, cz, 0.0F, -1.0F, 0.0F,
                    x0, cy - ringY, z0, nr * ca, -ny, nr * sa,
                    x1, cy - ringY, z1, nr * cb, -ny, nr * sb, color);
            ca = cb;
            sa = sb;
        }
    }

    /** 一个实心小方块，六个面都朝外（法线也一起算好）。 */
    public static void cuboid(VertexConsumer vertices, PoseStack.Pose pose,
                              float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        // 下（朝 -Y）
        quad(vertices, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color);
        // 上（朝 +Y）
        quad(vertices, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, color);
        // 北（朝 -Z）
        quad(vertices, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, color);
        // 南（朝 +Z）
        quad(vertices, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, color);
        // 西（朝 -X）
        quad(vertices, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, color);
        // 东（朝 +X）
        quad(vertices, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, color);
    }

    /** 一个四边形：顶点按顺时针绕（从正面看），法线由三个点算出来，UV / 光照 / 覆盖层都填好。 */
    public static void quad(VertexConsumer vertices, PoseStack.Pose pose,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz, int color) {
        float e1x = bx - ax;
        float e1y = by - ay;
        float e1z = bz - az;
        float e2x = cx - ax;
        float e2y = cy - ay;
        float e2z = cz - az;
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        } else {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        quadN(vertices, pose,
                ax, ay, az, nx, ny, nz,
                bx, by, bz, nx, ny, nz,
                cx, cy, cz, nx, ny, nz,
                dx, dy, dz, nx, ny, nz, color);
    }

    /** 一个四边形，但四个顶点各写各的法线：圆管 / 圆珠这种要「圆滑过渡」的地方用它。 */
    public static void quadN(VertexConsumer vertices, PoseStack.Pose pose,
                             float ax, float ay, float az, float nax, float nay, float naz,
                             float bx, float by, float bz, float nbx, float nby, float nbz,
                             float cx, float cy, float cz, float ncx, float ncy, float ncz,
                             float dx, float dy, float dz, float ndx, float ndy, float ndz,
                             int color) {
        vertex(vertices, pose, ax, ay, az, nax, nay, naz, color);
        vertex(vertices, pose, bx, by, bz, nbx, nby, nbz, color);
        vertex(vertices, pose, cx, cy, cz, ncx, ncy, ncz, color);
        vertex(vertices, pose, dx, dy, dz, ndx, ndy, ndz, color);
    }

    /** 一个三角形（圆珠的顶 / 底盖子用）。 */
    private static void tri(VertexConsumer vertices, PoseStack.Pose pose,
                            float ax, float ay, float az, float nax, float nay, float naz,
                            float bx, float by, float bz, float nbx, float nby, float nbz,
                            float cx, float cy, float cz, float ncx, float ncy, float ncz,
                            int color) {
        vertex(vertices, pose, ax, ay, az, nax, nay, naz, color);
        vertex(vertices, pose, bx, by, bz, nbx, nby, nbz, color);
        vertex(vertices, pose, cx, cy, cz, ncx, ncy, ncz, color);
    }

    /** 一个顶点：位置、颜色、贴图坐标、覆盖层、满亮度、法线。缺一样光影下的实体渲染就会画不出来。 */
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float z,
                               float nx, float ny, float nz, int color) {
        vertices.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(UV, UV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
