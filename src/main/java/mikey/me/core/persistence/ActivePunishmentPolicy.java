package mikey.me.core.persistence;

public final class ActivePunishmentPolicy {

    private ActivePunishmentPolicy() {
    }

    public static boolean isActive(boolean active, long durationSeconds, long startMillis, long now) {
        if (!active) {
            return false;
        }
        if (durationSeconds <= 0L) {
            return true;
        }
        // start in the future = clock skew, treat it as active
        if (startMillis > now) {
            return true;
        }
        return (now - startMillis) / 1000L < durationSeconds;
    }
}
