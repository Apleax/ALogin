package xyz.apleax.ALogin.Util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 头像渲染契约测试：钉死两件事——
 * <ul>
 *     <li>皮肤区块：64×64 皮肤的 {@code (8,8)} 面部与 {@code (40,8)} 帽子层分别进 16×8 贴图的左右两半；</li>
 *     <li>渲染几何：照抄 MCCAG-v3 的 head 布局——外层（帽子）铺满输出、内层按 600/656 内缩 25/656，
 *         {@code size=656} 时即 外层 {@code (0,0,656,656)}、内层 {@code (25,25,600,600)}，
 *         并且每层带一层柔边阴影。</li>
 * </ul>
 * 合成皮肤：面部整块不透明填充（{@link #FACE_FILL}）+ 三个角标色；帽子层只有左上 2×2 不透明/半透明，其余全透明。
 *
 * @author Apleax
 */
class AvatarRendererTest {
    /**
     * 内层标识色：贴图 (0,0) / (1,1) / (7,7)
     */
    private static final int FACE_A = 0xFFFF0000;
    private static final int FACE_C = 0xFF0000FF;
    private static final int FACE_B = 0xFF00FF00;
    /**
     * 内层其余像素的填充色（保证内层整体不透明）
     */
    private static final int FACE_FILL = 0xFFE0AC69;
    /**
     * 外层：不透明块与半透明块
     */
    private static final int HAT_OPAQUE = 0xFF00FFFF;
    private static final int HAT_HALF = 0x80000000;
    private static final int TRANSPARENT = 0x00000000;
    /**
     * MCCAG 的基准边长：外层 16×41 = 656、内层 16×37.5 = 600、内缩 200-175 = 25
     */
    private static final int MCCAG_BASELINE = 656;
    /**
     * 几何断言用的边长：外层 88/8 = 11px 一块、内层 round(88×600/656) = 80、80/8 = 10px 一块（都是整数块，便于精确断言）
     */
    private static final int CLEAN_SIZE = 88;
    private static final int CLEAN_OUTER_BLOCK = 11;
    private static final int CLEAN_BASE_INSET = 3;
    private static final int CLEAN_BASE_BLOCK = 10;
    /**
     * 阴影 alpha 上限 = 255 × 0.2
     */
    private static final int SHADOW_MAX_ALPHA = 51;

    @Test
    void sheetTakesFaceFrom8_8AndHatFrom40_8() throws Exception {
        byte[] sheet = AvatarRenderer.buildSheet(syntheticSkin());
        assertNotNull(sheet);
        BufferedImage image = decode(sheet);
        assertEquals(AvatarRenderer.SHEET_WIDTH, image.getWidth());
        assertEquals(AvatarRenderer.SHEET_HEIGHT, image.getHeight());

        // 左 8×8 = 皮肤 (8,8)-(16,16) 面部
        assertEquals(FACE_A, image.getRGB(0, 0));
        assertEquals(FACE_C, image.getRGB(1, 1));
        assertEquals(FACE_B, image.getRGB(7, 7));
        assertEquals(FACE_FILL, image.getRGB(0, 7));
        // 右 8×8 = 皮肤 (40,8)-(48,16) 帽子层，alpha 原样保留
        assertEquals(HAT_OPAQUE, image.getRGB(8, 0));
        assertTrue(Math.abs(((image.getRGB(9, 1) >>> 24) & 0xFF) - 0x80) <= 1, "半透明帽子像素的 alpha 应保留");
        assertEquals(TRANSPARENT, image.getRGB(15, 7));
    }

    @Test
    void mccagRatiosReproduceReferenceNumbers() {
        // 比例来自 MCCAG-v3 Modules/Renderers/{Data,Minimal}.js：600/656 与 25/656
        assertEquals(600, (int) Math.round(MCCAG_BASELINE * AvatarRenderer.BASE_SIZE_RATIO));
        assertEquals(25, (int) Math.round(MCCAG_BASELINE * AvatarRenderer.BASE_INSET_RATIO));
        assertEquals(600.0 / 656.0, AvatarRenderer.BASE_SIZE_RATIO, 1e-12);
        assertEquals(25.0 / 656.0, AvatarRenderer.BASE_INSET_RATIO, 1e-12);
        // 断言用尺寸上的取整结果：round(88×25/656)=3、round(88×600/656)=80
        assertEquals(CLEAN_BASE_INSET, AvatarRenderer.baseInset(CLEAN_SIZE));
        assertEquals(80, AvatarRenderer.baseSide(CLEAN_SIZE));
    }

    @Test
    void overlayDrawsOuterLayerFillingCanvasAndBaseInsetPerMcCag() throws Exception {
        byte[] sheet = AvatarRenderer.buildSheet(syntheticSkin());
        BufferedImage head = decode(AvatarRenderer.render(sheet, CLEAN_SIZE, true));
        assertEquals(CLEAN_SIZE, head.getWidth());
        assertEquals(CLEAN_SIZE, head.getHeight());

        // 外层铺满画布：0..10 是外层不透明块
        assertEquals(HAT_OPAQUE, head.getRGB(0, 0));
        assertEquals(HAT_OPAQUE, head.getRGB(10, 10));
        assertEquals(HAT_OPAQUE, head.getRGB(CLEAN_BASE_INSET, CLEAN_BASE_INSET), "内层左上角应被外层不透明块盖住");
        // 半透明外层像素与内层叠加：alpha 仍为 255，但颜色两边都不是
        int blended = head.getRGB(CLEAN_OUTER_BLOCK, CLEAN_OUTER_BLOCK);
        assertEquals(255, (blended >>> 24) & 0xFF);
        assertNotEquals(HAT_HALF, blended);
        assertNotEquals(FACE_A, blended);
        // 外层透明处露出内层（远端远离外层不透明块，不受阴影影响）
        assertEquals(FACE_FILL, head.getRGB(43, 43));
        assertEquals(FACE_B, head.getRGB(75, 75));
    }

    @Test
    void overlayFalseKeepsSameGeometryAndOnlyDropsOuterLayer() throws Exception {
        byte[] sheet = AvatarRenderer.buildSheet(syntheticSkin());
        BufferedImage head = decode(AvatarRenderer.render(sheet, CLEAN_SIZE, false));

        // 与 overlay=true 同位置同尺寸：内层 (3,3,80,80)，每块 10px
        assertEquals(FACE_A, head.getRGB(CLEAN_BASE_INSET, CLEAN_BASE_INSET));
        assertEquals(FACE_A, head.getRGB(CLEAN_BASE_INSET + CLEAN_BASE_BLOCK - 1, CLEAN_BASE_INSET + CLEAN_BASE_BLOCK - 1));
        assertEquals(FACE_C, head.getRGB(CLEAN_BASE_INSET + CLEAN_BASE_BLOCK, CLEAN_BASE_INSET + CLEAN_BASE_BLOCK));
        assertEquals(FACE_B, head.getRGB(75, 75));
        // 外层位置不再有头发色（只剩柔边阴影）
        assertNotEquals(HAT_OPAQUE, head.getRGB(0, 0));
        assertTrue(((head.getRGB(0, 0) >>> 24) & 0xFF) < 255, "外层位置应为半透明阴影或透明");
    }

    @Test
    void nearestNeighbourScalingIsDeterministicAtAnySize() throws Exception {
        byte[] sheet = AvatarRenderer.buildSheet(syntheticSkin());
        assertEquals(1, decode(AvatarRenderer.render(sheet, 1, false)).getWidth());
        assertEquals(AvatarRenderer.MAX_SIZE,
                decode(AvatarRenderer.render(sheet, AvatarRenderer.MAX_SIZE, false)).getWidth());
        assertEquals(AvatarRenderer.MAX_SIZE,
                decode(AvatarRenderer.render(sheet, AvatarRenderer.MAX_SIZE, true)).getWidth());

        // 小尺寸下内层仍然是最近邻整数块：size=1 时内层只剩贴图 (0,0)
        BufferedImage single = decode(AvatarRenderer.render(sheet, 1, false));
        assertEquals(FACE_A, single.getRGB(0, 0));
        BufferedImage oneWithOverlay = decode(AvatarRenderer.render(sheet, 1, true));
        assertEquals(HAT_OPAQUE, oneWithOverlay.getRGB(0, 0), "外层铺满时 size=1 应取外层 (0,0)");
    }

    @Test
    void sizeIsClampedToRange() throws Exception {
        byte[] sheet = AvatarRenderer.buildSheet(syntheticSkin());
        assertEquals(1, decode(AvatarRenderer.render(sheet, 0, false)).getWidth());
        assertEquals(1, decode(AvatarRenderer.render(sheet, -5, false)).getWidth());
        assertEquals(AvatarRenderer.MAX_SIZE, decode(AvatarRenderer.render(sheet, 9999, false)).getWidth());
        assertEquals(1, AvatarRenderer.clampSize(0));
        assertEquals(AvatarRenderer.MAX_SIZE, AvatarRenderer.clampSize(9999));
        assertEquals(64, AvatarRenderer.clampSize(64));
        assertEquals(0, AvatarRenderer.baseInset(0));
        assertEquals(1, AvatarRenderer.baseSide(0));
    }

    @Test
    void shadowHaloIsDrawnAroundLayers() throws Exception {
        byte[] sheet = AvatarRenderer.buildSheet(syntheticSkin());
        int size = AvatarRenderer.MAX_SIZE;
        BufferedImage head = decode(AvatarRenderer.render(sheet, size, false));
        int inset = AvatarRenderer.baseInset(size);
        int span = AvatarRenderer.baseSide(size);

        // 内层左上角外侧 1px：纯黑阴影（rgba(0,0,0,0.2) → alpha ≤ 51）
        int nearShadow = head.getRGB(inset - 1, inset - 1);
        assertEquals(0, nearShadow & 0xFFFFFF, "阴影是纯黑");
        int nearAlpha = (nearShadow >>> 24) & 0xFF;
        assertTrue(nearAlpha > 0 && nearAlpha <= SHADOW_MAX_ALPHA, "阴影 alpha 应在 (0,51]：" + nearAlpha);

        // 模糊递减：离内层更远处的阴影更淡
        int farAlpha = (head.getRGB(inset - 10, inset - 10) >>> 24) & 0xFF;
        assertTrue(nearAlpha > farAlpha, "阴影应随距离衰减：" + nearAlpha + " vs " + farAlpha);

        // 远超模糊半径处没有阴影（半径 = round(512×15/656/2) = 6，三次窗口共 18px；此处距内层右下角 24px）
        int cornerAlpha = (head.getRGB(inset + span + 23, inset + span + 23) >>> 24) & 0xFF;
        assertEquals(0, cornerAlpha);
    }

    @Test
    void invalidInputsReturnNullInsteadOfThrowing() throws Exception {
        assertNull(AvatarRenderer.buildSheet(null));
        assertNull(AvatarRenderer.buildSheet(new byte[]{1, 2, 3}));
        // 非 64×64
        assertNull(AvatarRenderer.buildSheet(png(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB))));
        assertNull(AvatarRenderer.render(null, 8, false));
        // 尺寸不是 16×8 的贴图
        assertNull(AvatarRenderer.render(png(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)), 8, false));
        assertNull(AvatarRenderer.decode(new byte[]{9, 9, 9}));
    }

    @Test
    void placeholderAlwaysProducesUsablePng() throws Exception {
        for (int size : new int[]{1, 8, 64, AvatarRenderer.MAX_SIZE}) {
            byte[] png = AvatarRenderer.placeholder(size);
            assertNotNull(png);
            BufferedImage image = decode(png);
            assertEquals(size, image.getWidth());
            assertEquals(size, image.getHeight());
            assertEquals(255, (image.getRGB(0, 0) >>> 24) & 0xFF);
        }
        // 越界同样被裁剪，而不是失败
        assertEquals(1, decode(AvatarRenderer.placeholder(0)).getWidth());
    }

    /**
     * 合成一张 64×64 皮肤：面部整块不透明填充 + 三个标记色；帽子层只留左上 2×2，其余透明
     */
    private static byte[] syntheticSkin() throws Exception {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < AvatarRenderer.TILE; y++)
            for (int x = 0; x < AvatarRenderer.TILE; x++)
                skin.setRGB(AvatarRenderer.FACE_X + x, AvatarRenderer.FACE_Y + y, FACE_FILL);
        skin.setRGB(AvatarRenderer.FACE_X, AvatarRenderer.FACE_Y, FACE_A);
        skin.setRGB(AvatarRenderer.FACE_X + 1, AvatarRenderer.FACE_Y + 1, FACE_C);
        skin.setRGB(AvatarRenderer.FACE_X + 7, AvatarRenderer.FACE_Y + 7, FACE_B);
        skin.setRGB(AvatarRenderer.HAT_X, AvatarRenderer.HAT_Y, HAT_OPAQUE);
        skin.setRGB(AvatarRenderer.HAT_X + 1, AvatarRenderer.HAT_Y + 1, HAT_HALF);
        return png(skin);
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image, "输出必须是可解码的 PNG");
        // 统一成 ARGB，避免 getRGB 受源图类型影响
        BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = argb.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return argb;
    }
}
