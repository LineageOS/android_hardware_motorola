package com.motorola.internal.enterprise;

/**
 * Placeholder for Motorola's enterprise manager. MotoFaceUnlockArcSoft casts the result of
 * getSystemService() to this type and only calls hasUserRestriction(); stock framework.jar does not
 * define the class, so without this the cast throws NoClassDefFoundError (the APK only catches
 * SecurityException). On Lineage getSystemService() returns null and the APK logs
 * "MotoDevicePolicyManager not found", so this never runs.
 */
public class MotoDevicePolicyManager {
    public boolean hasUserRestriction(String restriction) {
        return false;
    }
}
