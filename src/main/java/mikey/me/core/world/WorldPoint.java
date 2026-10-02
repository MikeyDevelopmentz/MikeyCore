package mikey.me.core.world;

public record WorldPoint(String world, double x, double y, double z) {

    public WorldPoint {
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
    }

    public double distanceSquared(String otherWorld, double ox, double oy, double oz) {
        if (otherWorld == null || !otherWorld.equals(world)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - ox;
        double dy = y - oy;
        double dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }

    // stay visible until hide so it doesnt flicker on the edge
    public boolean within(String otherWorld, double ox, double oy, double oz,
                          double show, double hide, boolean alreadyVisible) {
        // NaN sneaks past every comparison and made points permanently invisible;
        // an infinite hide is fine, its a legit "always visible" setting
        if (Double.isNaN(show) || Double.isNaN(hide) || show <= 0.0 || hide < show) {
            throw new IllegalArgumentException("hide must be >= show and show must be positive");
        }
        // infinite distance <= infinite hide, so bail here or an unbounded hide leaks the point into every world
        if (otherWorld == null || !otherWorld.equals(world)) {
            return false;
        }
        double limit = alreadyVisible ? hide : show;
        double distance = distanceSquared(otherWorld, ox, oy, oz);
        return distance <= limit * limit;
    }
}
