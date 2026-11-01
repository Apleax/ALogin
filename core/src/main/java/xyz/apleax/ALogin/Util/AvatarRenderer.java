package xyz.apleax.ALogin.Util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 玩家头像渲染：把 64×64 皮肤规范化成 16×8 头像贴图，再按 MCCAG-v3 的 head 几何渲染成任意边长的 PNG。
 * <p>
 * 贴图布局（{@code skin.avatar} 里存的就是它，逐像素原样搬运、不做合成）：
 * <ul>
 *     <li>{@code (0,0)-(8,8)}：皮肤 {@code (8,8)-(16,16)} 的头部正面（内层）</li>
 *     <li>{@code (8,0)-(16,8)}：皮肤 {@code (40,8)-(48,16)} 的帽子层正面（外层）</li>
 * </ul>
 * <p>
 * 渲染几何照抄 MCCAG-v3（{@code Modules/Renderers/Data.js} 的 {@code minimalOperationData.head} +
 * {@code Modules/Renderers/Minimal.js}）：它在 1000×1000 画布上把 128 空间的 16×16 贴图按
 * <b>内层 ×37.5 @ (200,200) = 600×600</b>、<b>外层 ×41 @ (175,175) = 656×656</b> 逐层绘制（外层后画、盖在上面），
 * 每个 {@code drawImage} 都带 {@code shadowColor=rgba(0,0,0,0.2)}、{@code shadowBlur=15}、偏移 0 的柔边阴影。
 * 这两个裁剪框折算到 64 皮肤坐标正好是上面那两块贴图，所以贴图格式无需改动。
 * <p>
 * 本类的映射：<b>外层框铺满输出</b>（{@code size×size}），内层按 {@code 600/656} 缩放、左上内缩 {@code 25/656}——
 * 即 {@code size=656} 时与 MCCAG 的矩形完全一致（外层 {@code (0,0,656,656)}、内层 {@code (25,25,600,600)}）。
 * 与 MCCAG 一样，内层不是精确居中（左/上 25px、右/下 31px），此处原样保留。
 * <p>
 * 阴影：Java2D 没有 canvas 那种 {@code shadowBlur}，这里对每层的 alpha 做三次可分离盒式模糊作近似，
 * 半径 {@code size×15/656/2}，再以 alpha×0.2 的纯黑画在层下面——是"同尺度的柔边光晕"，不是逐位复刻。
 * 因为外层铺满画布，外层阴影在画布边界处会被裁掉。
 * <p>
 * 放缩约定：头像与占位图是像素画，一律**最近邻**整数映射（结果确定、可断言）；
 * 只有配置的默认头像走平滑缩放（那是任意图片，不是像素画）。
 * 纯 JDK（{@code javax.imageio}）实现，不引入图像库依赖。
 *
 * @author Apleax
 */
@Slf4j
public final class AvatarRenderer {
    /**
     * 规范化贴图宽度：左 8×8 面部 + 右 8×8 帽子层
     */
    public static final int SHEET_WIDTH = 16;
    /**
     * 规范化贴图高度
     */
    public static final int SHEET_HEIGHT = 8;
    /**
     * 单块贴图边长
     */
    public static final int TILE = 8;
    /**
     * 64×64 皮肤里头部正面左上角
     */
    public static final int FACE_X = 8;
    public static final int FACE_Y = 8;
    /**
     * 64×64 皮肤里头部帽子层正面左上角
     */
    public static final int HAT_X = 40;
    public static final int HAT_Y = 8;
    /**
     * 上传接口只放行 64×64（见 UploadSkinServiceImpl），其它尺寸一律按"无可用皮肤"处理
     */
    public static final int SKIN_WIDTH = 64;
    public static final int SKIN_HEIGHT = 64;
    /**
     * 输出边长取值范围与默认值（Crafatar 头像参数 size 为 1-512）
     */
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 512;
    public static final int DEFAULT_SIZE = 8;

    /**
     * MCCAG-v3 的基准：外层 16×41 = 656、内层 16×37.5 = 600、内层相对外层左上内缩 200-175 = 25
     */
    private static final double MCCAG_OUTER_SPAN = 656.0;
    private static final double MCCAG_BASE_SPAN = 600.0;
    private static final double MCCAG_BASE_INSET = 25.0;
    /**
     * canvas 的 {@code shadowBlur=15} 与 {@code rgba(0,0,0,0.2)}
     */
    private static final double MCCAG_SHADOW_BLUR = 15.0;
    private static final float MCCAG_SHADOW_ALPHA = 0.2f;
    /**
     * 内层边长占输出边长的比例（600/656 ≈ 0.9146）
     */
    public static final double BASE_SIZE_RATIO = MCCAG_BASE_SPAN / MCCAG_OUTER_SPAN;
    /**
     * 内层相对外层框的左上内缩比例（25/656 ≈ 0.0381）
     */
    public static final double BASE_INSET_RATIO = MCCAG_BASE_INSET / MCCAG_OUTER_SPAN;

    /**
     * 占位头像配色
     */
    private static final int PLACEHOLDER_BASE = 0xFF9E9E9E;
    private static final int PLACEHOLDER_DARK = 0xFF616161;

    static {
        // 图片编解码不落磁盘缓存，避免临时文件
        ImageIO.setUseCache(false);
    }

    private AvatarRenderer() {
    }

    /**
     * 输出边长裁剪到 [1,512]
     */
    public static int clampSize(int size) {
        if (size < MIN_SIZE) return MIN_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    /**
     * 内层（头部正面）在输出画布上的边长；{@code size=656} 时为 600
     */
    public static int baseSide(int size) {
        return (int) Math.round(clampSize(size) * BASE_SIZE_RATIO);
    }

    /**
     * 内层左上角相对画布原点的内缩；{@code size=656} 时为 25
     */
    public static int baseInset(int size) {
        return (int) Math.round(clampSize(size) * BASE_INSET_RATIO);
    }

    /**
     * 由 64×64 皮肤生成 16×8 规范化贴图；皮肤非法/解码失败返回 {@code null}。
     */
    public static byte[] buildSheet(byte[] skinPng) {
        BufferedImage skin = decode(skinPng);
        if (skin == null) return null;
        if (skin.getWidth() != SKIN_WIDTH || skin.getHeight() != SKIN_HEIGHT) {
            log.warn("皮肤尺寸非 64×64，无法生成头像贴图：{}×{}", skin.getWidth(), skin.getHeight());
            return null;
        }
        BufferedImage sheet = new BufferedImage(SHEET_WIDTH, SHEET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        copyTile(skin, sheet, FACE_X, FACE_Y, 0, 0);
        copyTile(skin, sheet, HAT_X, HAT_Y, TILE, 0);
        return encode(sheet);
    }

    /**
     * 由 16×8 规范化贴图渲染头像；贴图非法或解码失败返回 {@code null}。
     * <p>
     * 外层（帽子层）铺满输出，内层按 MCCAG 比例内缩；{@code overlay=false} 只画内层，**位置与尺寸不变**。
     *
     * @param overlay 是否绘制外层（帽子/头发层）
     */
    public static byte[] render(byte[] sheetPng, int size, boolean overlay) {
        BufferedImage sheet = decode(sheetPng);
        if (sheet == null) return null;
        if (sheet.getWidth() != SHEET_WIDTH || sheet.getHeight() != SHEET_HEIGHT) {
            log.warn("头像贴图尺寸非 16×8：{}×{}", sheet.getWidth(), sheet.getHeight());
            return null;
        }
        int side = clampSize(size);
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        try {
            // MCCAG 的顺序：先内层，再外层（外层盖在内层之上）
            int inset = baseInset(side);
            int baseSpan = baseSide(side);
            drawLayer(graphics, sheet.getSubimage(0, 0, TILE, TILE), inset, inset, baseSpan, baseSpan, side);
            if (overlay) drawLayer(graphics, sheet.getSubimage(TILE, 0, TILE, TILE), 0, 0, side, side, side);
        } finally {
            graphics.dispose();
        }
        return encode(out);
    }

    /**
     * 内置占位头像：8×8 中性灰头部（描边 + 双眼 + 嘴），铺满输出（不套 MCCAG 的内缩与阴影）。
     */
    public static byte[] placeholder(int size) {
        int side = clampSize(size);
        BufferedImage tile = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++)
                tile.setRGB(x, y, isPlaceholderDark(x, y) ? PLACEHOLDER_DARK : PLACEHOLDER_BASE);
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        drawTileNearest(out, tile);
        return encode(out);
    }

    private static boolean isPlaceholderDark(int x, int y) {
        // 外描边
        if (x == 0 || y == 0 || x == TILE - 1 || y == TILE - 1) return true;
        // 双眼
        if (y == 3 && (x == 2 || x == 5)) return true;
        // 嘴
        return y == 5 && (x == 3 || x == 4);
    }

    /**
     * 平滑（双线性）缩放到指定边长；用于配置的默认头像（任意图片，不是像素画）。失败返回 {@code null}。
     */
    public static byte[] resizeSmooth(BufferedImage image, int size) {
        if (image == null) return null;
        int side = clampSize(size);
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, side, side, null);
        } finally {
            graphics.dispose();
        }
        return encode(out);
    }

    /**
     * 解码为 {@code TYPE_INT_ARGB}（统一带 alpha，避免无 alpha 通道的源图把帽子层读成不透明）；失败返回 {@code null}。
     */
    public static BufferedImage decode(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return null;
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (decoded == null) return null;
            BufferedImage argb = new BufferedImage(decoded.getWidth(), decoded.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = argb.createGraphics();
            try {
                graphics.drawImage(decoded, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return argb;
        } catch (Exception e) {
            log.warn("图片解码失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 画一层：先画它的柔边阴影，再画它本身（与 canvas 每次 {@code drawImage} 都带阴影的行为一致）。
     */
    private static void drawLayer(Graphics2D target, BufferedImage tile, int x, int y, int width, int height,
                                  int canvasSide) {
        if (width <= 0 || height <= 0) return;
        BufferedImage layer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        drawTileNearest(layer, tile);
        int blurRadius = (int) Math.round(canvasSide * MCCAG_SHADOW_BLUR / MCCAG_OUTER_SPAN / 2.0);
        if (blurRadius >= 1) {
            // 阴影图比层本身四周各多 radius*3：三次盒式模糊的总扩散量，这样阴影才能像 canvas 一样溢出到层之外
            int pad = blurRadius * 3 + 1;
            target.drawImage(shadowOf(layer, blurRadius, pad), x - pad, y - pad, null);
        }
        target.drawImage(layer, x, y, null);
    }

    /**
     * 把 8×8 贴图按最近邻整数映射铺满 {@code dst}（目标像素取 {@code x*srcW/dstW}）
     */
    private static void drawTileNearest(BufferedImage dst, BufferedImage tile) {
        int dstW = dst.getWidth();
        int dstH = dst.getHeight();
        int srcW = tile.getWidth();
        int srcH = tile.getHeight();
        for (int y = 0; y < dstH; y++) {
            int srcY = (int) ((long) y * srcH / dstH);
            for (int x = 0; x < dstW; x++)
                dst.setRGB(x, y, tile.getRGB((int) ((long) x * srcW / dstW), srcY));
        }
    }

    /**
     * 生成一层的阴影：把层的 alpha 放到四周各留 {@code pad} 的画布中央，做三次盒式模糊近似高斯，
     * 再压成 {@code rgba(0,0,0,0.2)}。留白是为了让阴影能扩散到层之外（canvas 的 shadow 就是这样）。
     */
    private static BufferedImage shadowOf(BufferedImage layer, int radius, int pad) {
        int width = layer.getWidth();
        int height = layer.getHeight();
        int shadowWidth = width + pad * 2;
        int shadowHeight = height + pad * 2;
        int[] alpha = new int[shadowWidth * shadowHeight];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                alpha[(y + pad) * shadowWidth + (x + pad)] = (layer.getRGB(x, y) >>> 24) & 0xFF;
        for (int pass = 0; pass < 3; pass++) boxBlur(alpha, shadowWidth, shadowHeight, radius);

        int maxAlpha = Math.round(255 * MCCAG_SHADOW_ALPHA);
        BufferedImage shadow = new BufferedImage(shadowWidth, shadowHeight, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < alpha.length; i++) {
            int a = alpha[i] * maxAlpha / 255;
            if (a > 0) shadow.setRGB(i % shadowWidth, i / shadowWidth, a << 24);
        }
        return shadow;
    }

    /**
     * 可分离盒式模糊（滑动窗口，边界按最近像素延展），就地写回 {@code pixels}
     */
    private static void boxBlur(int[] pixels, int width, int height, int radius) {
        int window = radius * 2 + 1;
        int[] tmp = new int[pixels.length];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int sum = 0;
            for (int i = -radius; i <= radius; i++) sum += pixels[row + clampIndex(i, width)];
            for (int x = 0; x < width; x++) {
                tmp[row + x] = sum / window;
                sum -= pixels[row + clampIndex(x - radius, width)];
                sum += pixels[row + clampIndex(x + radius + 1, width)];
            }
        }
        for (int x = 0; x < width; x++) {
            int sum = 0;
            for (int i = -radius; i <= radius; i++) sum += tmp[clampIndex(i, height) * width + x];
            for (int y = 0; y < height; y++) {
                pixels[y * width + x] = sum / window;
                sum -= tmp[clampIndex(y - radius, height) * width + x];
                sum += tmp[clampIndex(y + radius + 1, height) * width + x];
            }
        }
    }

    private static int clampIndex(int value, int length) {
        if (value < 0) return 0;
        return value >= length ? length - 1 : value;
    }

    /**
     * 把 {@code src} 的 8×8 块按坐标搬到 {@code dst}（逐像素，alpha 原样保留）。
     */
    private static void copyTile(BufferedImage src, BufferedImage dst, int srcX, int srcY, int dstX, int dstY) {
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++)
                dst.setRGB(dstX + x, dstY + y, src.getRGB(srcX + x, srcY + y));
    }

    private static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                log.warn("PNG 编码失败：无可用 writer");
                return null;
            }
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("PNG 编码异常：{}", e.getMessage());
            return null;
        }
    }
}
