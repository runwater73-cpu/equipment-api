package dev.equipmentstructure.api;

import dev.equipmentstructure.api.appearance.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentFeatureRulesTest {
    private static final AppearancePose MOVED = new AppearancePose(2, -1, 3, AppearanceRotation.IDENTITY, 1.5);
    private static final AppearancePartPresentation SHOWN = new AppearancePartPresentation(MOVED, true);
    private static final AppearancePartPresentation HIDDEN = new AppearancePartPresentation(MOVED, false);

    @Test void disabledPoseEditingStillPermitsVisibilityOnlyChanges() {
        var rules = new EquipmentFeatureConfig.Rules(false, true, true);
        assertTrue(rules.allowsPart(SHOWN, HIDDEN));
        assertFalse(rules.allowsPart(SHOWN, new AppearancePartPresentation(AppearancePose.IDENTITY, true)));
    }
    @Test void disabledHidingKeepsExistingStateAndAlwaysAllowsShowing() {
        var rules = new EquipmentFeatureConfig.Rules(true, false, false);
        assertFalse(rules.allowsOriginal(true, false));
        assertTrue(rules.allowsOriginal(false, false));
        assertTrue(rules.allowsOriginal(false, true));
        assertFalse(rules.allowsPart(SHOWN, HIDDEN));
        assertTrue(rules.allowsPart(HIDDEN, HIDDEN));
        assertTrue(rules.allowsPart(HIDDEN, SHOWN));
        assertTrue(rules.allowsPart(HIDDEN, new AppearancePartPresentation(AppearancePose.IDENTITY, false)));
    }
    @Test void originalAndAttachmentPermissionsAreIndependent() {
        var rules = new EquipmentFeatureConfig.Rules(true, false, true);
        assertFalse(rules.allowsOriginal(true, false));
        assertTrue(rules.allowsPart(SHOWN, HIDDEN));
        rules = new EquipmentFeatureConfig.Rules(true, true, false);
        assertTrue(rules.allowsOriginal(true, false));
        assertFalse(rules.allowsPart(SHOWN, HIDDEN));
    }
}
