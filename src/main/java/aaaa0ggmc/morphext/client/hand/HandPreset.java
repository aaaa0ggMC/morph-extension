package aaaa0ggmc.morphext.client.hand;

/**
 * 手部骨骼零件与第一人称渲染位姿配置预设。
 */
public class HandPreset {
    public final String rightFieldName;
    public final String leftFieldName;
    public final float extraRotationX;
    public final float extraRotationY;
    public final float extraRotationZ;
    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;
    public final float scale; // 0.0F 表示交由智能启发式自适应缩放

    public HandPreset(String rightFieldName, String leftFieldName) {
        this(rightFieldName, leftFieldName, 0, 0, 0, 0, 0, 0, 0);
    }

    public HandPreset(String rightFieldName, String leftFieldName,
                      float extraRotationX, float extraRotationY, float extraRotationZ,
                      float offsetX, float offsetY, float offsetZ) {
        this(rightFieldName, leftFieldName, extraRotationX, extraRotationY, extraRotationZ, offsetX, offsetY, offsetZ, 0);
    }

    public HandPreset(String rightFieldName, String leftFieldName,
                      float extraRotationX, float extraRotationY, float extraRotationZ,
                      float offsetX, float offsetY, float offsetZ, float scale) {
        this.rightFieldName = rightFieldName;
        this.leftFieldName = leftFieldName;
        this.extraRotationX = extraRotationX;
        this.extraRotationY = extraRotationY;
        this.extraRotationZ = extraRotationZ;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scale = scale;
    }
}
