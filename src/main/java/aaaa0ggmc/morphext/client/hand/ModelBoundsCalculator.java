package aaaa0ggmc.morphext.client.hand;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;

public final class ModelBoundsCalculator {

    public static ModelBounds computeRootBounds(ModelRenderer part) {
        if (part == null || part.cubeList == null || part.cubeList.isEmpty()) {
            return computeTotalBounds(part);
        }
        float[] mm = initMinMax();
        addCubes(part.cubeList, mm, 0, 0, 0);
        if (Float.isInfinite(mm[0])) {
            return new ModelBounds(0, 0, 0, 0, 0, 0, false);
        }
        return new ModelBounds(mm[0], mm[1], mm[2], mm[3], mm[4], mm[5], true);
    }

    public static ModelBounds computeTotalBounds(ModelRenderer part) {
        if (part == null) return new ModelBounds(0, 0, 0, 0, 0, 0, false);
        float[] mm = initMinMax();
        collectRecursive(part, mm, 0, 0, 0);
        if (Float.isInfinite(mm[0])) {
            return new ModelBounds(0, 0, 0, 0, 0, 0, false);
        }
        return new ModelBounds(mm[0], mm[1], mm[2], mm[3], mm[4], mm[5], true);
    }

    private static float[] initMinMax() {
        return new float[]{
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
    }

    private static void collectRecursive(ModelRenderer part, float[] mm, float offX, float offY, float offZ) {
        if (part == null) return;
        if (part.cubeList != null) {
            addCubes(part.cubeList, mm, offX, offY, offZ);
        }
        if (part.childModels != null) {
            for (ModelRenderer child : part.childModels) {
                if (child != null) {
                    collectRecursive(child, mm, offX + child.rotationPointX, offY + child.rotationPointY, offZ + child.rotationPointZ);
                }
            }
        }
    }

    private static void addCubes(java.util.List<ModelBox> cubes, float[] mm, float offX, float offY, float offZ) {
        for (ModelBox box : cubes) {
            if (box == null) continue;
            float x1 = Math.min(box.posX1, box.posX2) + offX;
            float x2 = Math.max(box.posX1, box.posX2) + offX;
            float y1 = Math.min(box.posY1, box.posY2) + offY;
            float y2 = Math.max(box.posY1, box.posY2) + offY;
            float z1 = Math.min(box.posZ1, box.posZ2) + offZ;
            float z2 = Math.max(box.posZ1, box.posZ2) + offZ;

            if (x1 < mm[0]) mm[0] = x1;
            if (x2 > mm[1]) mm[1] = x2;
            if (y1 < mm[2]) mm[2] = y1;
            if (y2 > mm[3]) mm[3] = y2;
            if (z1 < mm[4]) mm[4] = z1;
            if (z2 > mm[5]) mm[5] = z2;
        }
    }

    private ModelBoundsCalculator() {}
}
