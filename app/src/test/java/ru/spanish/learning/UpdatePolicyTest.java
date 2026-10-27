package ru.spanish.learning;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UpdatePolicyTest {
    private static final String APP = "ru.spanish.learning";
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);

    @Test public void acceptsSameSignerSetAndNewVersion() {
        UpdatePolicy.validatePackage(APP, APP, 106, 106, 105, List.of(B, A), List.of(A, B));
    }

    @Test public void rejectsUnsignedAndForeignSignatures() {
        assertThrows(IllegalArgumentException.class, () ->
                UpdatePolicy.validatePackage(APP, APP, 106, 106, 105, List.of(), List.of(A)));
        assertThrows(IllegalArgumentException.class, () ->
                UpdatePolicy.validatePackage(APP, APP, 106, 106, 105, List.of(B), List.of(A)));
        assertThrows(IllegalArgumentException.class, () ->
                UpdatePolicy.validatePackage(APP, APP, 106, 106, 105, List.of(A, A), List.of(A)));
    }

    @Test public void rejectsDifferentPackageOrMisreportedVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                UpdatePolicy.validatePackage(APP, "another.app", 106, 106, 105, List.of(A), List.of(A)));
        assertThrows(IllegalArgumentException.class, () ->
                UpdatePolicy.validatePackage(APP, APP, 106, 107, 105, List.of(A), List.of(A)));
        assertThrows(IllegalArgumentException.class, () ->
                UpdatePolicy.validatePackage(APP, APP, 105, 105, 105, List.of(A), List.of(A)));
    }

    @Test public void neverAcceptsRollbackOrOverflow() {
        assertFalse(UpdatePolicy.newer(104, 105));
        assertFalse(UpdatePolicy.newer(105, 105));
        assertFalse(UpdatePolicy.newer(-1, 105));
        assertFalse(UpdatePolicy.newer((long) Integer.MAX_VALUE + 1, 105));
        assertTrue(UpdatePolicy.newer(106, 105));
    }

    @Test public void supportsSignedBundledSpeechModelUpdates() {
        assertTrue("Offline voice builds must be accepted", UpdatePolicy.MAX_APK_BYTES >= 95L * 1024 * 1024);
        assertTrue("APK size remains bounded", UpdatePolicy.MAX_APK_BYTES <= 100L * 1024 * 1024);
    }

    @Test public void requiresACompleteSha256Digest() {
        assertTrue(UpdatePolicy.validDigest(A));
        assertTrue(UpdatePolicy.validDigest(A.toUpperCase(java.util.Locale.ROOT)));
        assertFalse(UpdatePolicy.validDigest(null));
        assertFalse(UpdatePolicy.validDigest("a".repeat(63)));
        assertFalse(UpdatePolicy.validDigest("g".repeat(64)));
    }
}
