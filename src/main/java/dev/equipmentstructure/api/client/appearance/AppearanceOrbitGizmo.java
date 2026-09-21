package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.appearance.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Project the actual motion path for both drawing and mouse picking. */
public final class AppearanceOrbitGizmo {
    private static final int STEPS = 96;
    private final Matrix4f frame;
    private final AppearanceTransform base;
    private final AppearanceOrbitMotion motion;
    private final List<Vector3f> points = new ArrayList<>();

    public AppearanceOrbitGizmo(Matrix4f frame, AppearanceTransform base, AppearanceOrbitMotion motion) {
        this.frame = new Matrix4f(frame); this.base = base; this.motion = motion;
        for (int i = 0; i <= STEPS; i++) points.add(project(motion.applyAngle(base, i * 360.0 / STEPS).position()));
    }
    public List<Vector3f> points() { return List.copyOf(points); }
    public Vector3f project(AppearanceVector p) {
        return frame.transformPosition((float) p.x(), (float) p.y(), (float) p.z(), new Vector3f());
    }
    public record Hit(double distance, double angle) {}
    public Hit hit(double x, double y) {
        var best = new Hit(Double.POSITIVE_INFINITY, 0);
        for (int i = 1; i < points.size(); i++) {
            var a = points.get(i - 1); var b = points.get(i);
            double vx = b.x - a.x, vy = b.y - a.y, length = vx * vx + vy * vy;
            double t = length < 1.0E-8 ? 0 : Math.clamp(((x - a.x) * vx + (y - a.y) * vy) / length, 0, 1);
            double distance = Math.hypot(x - a.x - t * vx, y - a.y - t * vy);
            if (distance < best.distance) best = new Hit(distance, (i - 1 + t) * 360.0 / STEPS);
        }
        return best;
    }
    public AppearanceVector center() {
        var offset = base.position().add(motion.pivot().negate()); var axis = motion.axis();
        double height = offset.x() * axis.x() + offset.y() * axis.y() + offset.z() * axis.z();
        return motion.pivot().add(new AppearanceVector(axis.x() * height, axis.y() * height, axis.z() * height));
    }
    /** Change radius at the grabbed phase, retaining the host axis and axial height. */
    public AppearanceVector radiusDelta(double angle, double dx, double dy) {
        var center = center();
        var radial = base.position().add(center.negate());
        var screenCenter = project(center);
        var screenPoint = project(motion.applyAngle(base, angle).position());
        double x = screenPoint.x - screenCenter.x, y = screenPoint.y - screenCenter.y;
        double squared = x * x + y * y;
        if (squared < 1.0E-6) return AppearanceVector.ZERO;
        double amount = Math.clamp((dx * x + dy * y) / squared, -0.9, 3);
        return new AppearanceVector(radial.x() * amount, radial.y() * amount, radial.z() * amount);
    }
    /** JOML projects a screen-space trackball axis back to host coordinates. */
    public AppearanceRotation tilt(double dx, double dy) {
        if (Math.hypot(dx, dy) < 1.0E-8) return AppearanceRotation.IDENTITY;
        var axis = new Matrix4f(frame).invert().transformDirection((float) dy, (float) -dx, 0, new Vector3f()).normalize();
        if (!axis.isFinite()) return AppearanceRotation.IDENTITY;
        double half = Math.toRadians(Math.hypot(dx, dy) * 0.65) / 2;
        return new AppearanceRotation(axis.x * Math.sin(half), axis.y * Math.sin(half), axis.z * Math.sin(half), Math.cos(half));
    }
}
