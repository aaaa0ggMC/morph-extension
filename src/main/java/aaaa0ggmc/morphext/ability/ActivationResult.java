package aaaa0ggmc.morphext.ability;

/** 执行确认；SUCCESS 表示执行器接受并返回，不保证第三方 void API 已产生世界效果。 */
public final class ActivationResult {
    public enum Status { SUCCESS, LOCKED, COOLDOWN, UNAVAILABLE, FAILED }

    public final Status status;
    public final int cooldownTicks;

    public ActivationResult(Status status, int cooldownTicks) {
        this.status = status;
        this.cooldownTicks = Math.max(0, cooldownTicks);
    }

    public static ActivationResult success(int cooldownTicks) {
        return new ActivationResult(Status.SUCCESS, cooldownTicks);
    }

    public static ActivationResult unavailable() {
        return new ActivationResult(Status.UNAVAILABLE, 0);
    }
}
