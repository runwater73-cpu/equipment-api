package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import java.util.Objects;

/** Visual edits for one installed part; never carries an item identity or author data. */
public record AppearancePartPresentation(AppearancePose pose, boolean visible, AppearanceMotionSettings motion) {
    public AppearancePartPresentation {
        Objects.requireNonNull(pose, "pose"); Objects.requireNonNull(motion, "motion");
    }
    public AppearancePartPresentation(AppearancePose pose, boolean visible) {
        this(pose, visible, AppearanceMotionSettings.DEFAULT);
    }
    public AppearancePartPresentation withPose(AppearancePose next) { return new AppearancePartPresentation(next, visible, motion); }
    public AppearancePartPresentation withVisible(boolean next) { return new AppearancePartPresentation(pose, next, motion); }
    public AppearancePartPresentation withMotion(AppearanceMotionSettings next) { return new AppearancePartPresentation(pose, visible, next); }

    public static AppearancePartPresentation read(EquipmentComponentInstance part) {
        return new AppearancePartPresentation(AppearancePoseStorage.read(part).orElse(AppearancePose.IDENTITY),
                AppearanceVisibilityStorage.componentVisible(part), AppearanceMotionSettings.read(part));
    }

    public EquipmentComponentInstance apply(EquipmentComponentInstance part) {
        // A visibility-only edit must not normalize or rewrite an author's existing pose data.
        if (!pose.equals(AppearancePoseStorage.read(part).orElse(AppearancePose.IDENTITY))) {
            part = AppearancePoseStorage.with(part, pose);
        }
        return motion.apply(AppearanceVisibilityStorage.withComponentVisible(part, visible));
    }
}
