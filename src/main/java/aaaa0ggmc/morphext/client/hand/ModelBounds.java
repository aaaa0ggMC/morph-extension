package aaaa0ggmc.morphext.client.hand;

/**
 * 实体模型骨骼部件在局部模型坐标系下的 AABB 空间包围盒。
 */
public final class ModelBounds {
    public final float minX;
    public final float maxX;
    public final float minY;
    public final float maxY;
    public final float minZ;
    public final float maxZ;
    public final boolean valid;

    public ModelBounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ, boolean valid) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.valid = valid;
    }

    public float spanX() { return maxX - minX; }
    public float spanY() { return maxY - minY; }
    public float spanZ() { return maxZ - minZ; }
    public float centerX() { return (minX + maxX) * 0.5F; }
    public float centerY() { return (minY + maxY) * 0.5F; }
    public float centerZ() { return (minZ + maxZ) * 0.5F; }
    public float maxSpan() { return Math.max(spanX(), Math.max(spanY(), spanZ())); }
}
