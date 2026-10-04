package com.example.data.firebase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseCrashlyticsManagerTest {
    @Test
    fun principalReleaseEnablesCrashlytics() {
        assertTrue(CrashlyticsBuildPolicy.isCollectionEnabled("com.aistudio.omnistudio.wkspea", false))
    }

    @Test
    fun betaTestVariantsKeepExistingCrashlyticsBehavior() {
        assertTrue(CrashlyticsBuildPolicy.isCollectionEnabled("com.aistudio.omnistudio.wkspea.test", false))
        assertTrue(CrashlyticsBuildPolicy.isCollectionEnabled("com.aistudio.omnistudio.wkspea.test", true))
    }

    @Test
    fun principalDebugAndUnknownPackagesDoNotEnableCollection() {
        assertFalse(CrashlyticsBuildPolicy.isCollectionEnabled("com.aistudio.omnistudio.wkspea", true))
        assertFalse(CrashlyticsBuildPolicy.isCollectionEnabled("com.example.other", false))
    }
}
