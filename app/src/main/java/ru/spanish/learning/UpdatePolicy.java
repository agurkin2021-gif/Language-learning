package ru.spanish.learning;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validation shared by downloaded and already staged updates. No Android dependencies. */
final class UpdatePolicy {
    static final int MAX_METADATA_BYTES = 16 * 1024;
    static final long MAX_APK_BYTES = 32L * 1024 * 1024;

    private UpdatePolicy() {}

    static boolean validDigest(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    static boolean newer(long candidate, long installed) {
        return candidate > installed && candidate <= Integer.MAX_VALUE;
    }

    static void validatePackage(String expectedPackage, String actualPackage,
                                long expectedVersion, long actualVersion, long installedVersion,
                                List<String> candidateSigners, List<String> installedSigners) {
        if (!expectedPackage.equals(actualPackage)) throw new IllegalArgumentException("Wrong package");
        if (!newer(expectedVersion, installedVersion) || actualVersion != expectedVersion)
            throw new IllegalArgumentException("Wrong version");
        Set<String> candidate = new HashSet<>(candidateSigners);
        Set<String> installed = new HashSet<>(installedSigners);
        if (candidate.isEmpty() || installed.isEmpty()
                || candidate.size() != candidateSigners.size()
                || installed.size() != installedSigners.size()
                || !candidate.equals(installed))
            throw new IllegalArgumentException("Wrong signing certificate");
    }
}
