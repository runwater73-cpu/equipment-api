package dev.equipmentstructure.api.client.appearance;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Orthographic GUI picking and drag projection in the captured attachment frame. */
public final class AppearancePlacementGizmo {
    private final Vector3f origin = new Vector3f();
    private final Vector3f[] units = {new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f[] ends = {new Vector3f(), new Vector3f(), new Vector3f()};
    private boolean valid;
    private final Matrix4f inverseFrame = new Matrix4f();

    public void clear() { valid = false; }
    public boolean valid() { return valid; }
    public Vector3f origin() { return new Vector3f(origin); }
    public Vector3f end(int axis) { return new Vector3f(ends[axis]); }
    public boolean visible(int axis) { return valid && units[axis].lengthSquared() >= 0.04F; }

    public void update(Matrix4f frame, float x, float y, float z) {
        frame.transformPosition(x, y, z, origin);
        valid = origin.isFinite();
        frame.invert(inverseFrame);
        valid &= inverseFrame.isFinite();
        for (int axis = 0; axis < 3; axis++) {
            Vector3f unit = units[axis].zero().setComponent(axis, 1);
            frame.transformDirection(unit);
            unit.z = 0;
            if (!unit.isFinite()) { valid = false; unit.zero(); }
            ends[axis].set(origin);
            if (unit.lengthSquared() >= 0.04F) ends[axis].add(new Vector3f(unit).normalize(34));
        }
    }

    public int hit(double x, double y) {
        int best = -1;
        double distance = 36;
        for (int axis = 0; axis < 3; axis++) {
            if (!visible(axis)) continue;
            double dx = ends[axis].x - origin.x, dy = ends[axis].y - origin.y;
            double t = Math.clamp(((x - origin.x) * dx + (y - origin.y) * dy) / (dx * dx + dy * dy), 0.18, 1);
            double ex = x - origin.x - t * dx, ey = y - origin.y - t * dy;
            double squared = ex * ex + ey * ey;
            if (squared < distance) { best = axis; distance = squared; }
        }
        return best;
    }

    public double amount(int axis, double dx, double dy) {
        if (!visible(axis)) return 0;
        Vector3f unit = units[axis];
        return (dx * unit.x + dy * unit.y) / unit.lengthSquared();
    }

    /** Screen-plane translation at constant preview depth, expressed in attachment units. */
    public Vector3f planeDelta(double dx, double dy) {
        if (!valid || !Double.isFinite(dx) || !Double.isFinite(dy)) return new Vector3f();
        Vector3f delta = inverseFrame.transformDirection((float) dx, (float) dy, 0, new Vector3f());
        return delta.isFinite() ? delta : new Vector3f();
    }
}
