/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Copies an existing LibreLogin (or LibrePremium) data folder into the JTLogin data folder.
 * <p>
 * Design rules:
 * <ul>
 *     <li><b>Copy, never move.</b> The legacy folder is left untouched, so rolling back is just "put the old
 *     jar back".</li>
 *     <li><b>Never overwrite.</b> A file that already exists in the JTLogin folder is kept; if it differs from
 *     the legacy copy, the difference is reported as a conflict.</li>
 *     <li><b>Verified.</b> Every copied file is written to a temporary name, compared by SHA-256 with its
 *     source and only then moved into place.</li>
 *     <li><b>All or nothing per run.</b> If anything fails, files created by this run are removed again, no
 *     marker is written, and the caller is expected to refuse to start instead of creating a new empty
 *     database next to an existing one.</li>
 *     <li><b>Repeatable.</b> After a successful run a marker file is written; later starts do nothing.
 *     Running again without the marker only fills in missing files.</li>
 * </ul>
 * The class only depends on the JDK so that it can be unit-tested without any server API.
 */
public final class LegacyDataMigrator {

    public static final String MARKER_FILE = ".migrated-from-legacy";

    /**
     * Top-level entries that are never copied: the library cache is re-downloaded for the new relocation
     * prefix, the license is rewritten on every start, and the marker is ours.
     */
    private static final Set<String> SKIPPED_TOP_LEVEL = Set.of("lib", "LICENSE.txt", MARKER_FILE);

    public enum Outcome {
        /** No legacy data folder with content was found; nothing to do. */
        NO_LEGACY_DATA,
        /** A previous run already completed (marker present); nothing was touched. */
        ALREADY_MIGRATED,
        /** Data was copied (or was already complete) and the marker was written. */
        MIGRATED,
        /** Something went wrong; files created by this run were removed again. */
        FAILED
    }

    public record Result(
            Outcome outcome,
            Path source,
            Path target,
            int copied,
            int identical,
            List<String> conflicts,
            String error
    ) {
    }

    private final Path pluginsDirectory;
    private final String targetFolderName;
    private final Consumer<String> info;
    private final Consumer<String> warn;

    public LegacyDataMigrator(Path pluginsDirectory, String targetFolderName, Consumer<String> info, Consumer<String> warn) {
        this.pluginsDirectory = pluginsDirectory;
        this.targetFolderName = targetFolderName;
        this.info = info;
        this.warn = warn;
    }

    public Result migrate() {
        var target = pluginsDirectory.resolve(targetFolderName);

        if (Files.exists(target.resolve(MARKER_FILE))) {
            return new Result(Outcome.ALREADY_MIGRATED, null, target, 0, 0, List.of(), null);
        }

        var source = findLegacyFolder(target);
        if (source == null) {
            return new Result(Outcome.NO_LEGACY_DATA, null, target, 0, 0, List.of(), null);
        }

        info.accept("Found existing data in '" + source.getFileName() + "', copying it to '" + target.getFileName() + "' (the old folder is left untouched)...");

        var created = new ArrayList<Path>();
        var conflicts = new ArrayList<String>();
        var counters = new int[2]; // copied, identical

        try {
            Files.createDirectories(target);

            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(source)) {
                        var rel = source.relativize(dir);
                        if (SKIPPED_TOP_LEVEL.contains(rel.getName(0).toString())) return FileVisitResult.SKIP_SUBTREE;
                        Files.createDirectories(target.resolve(rel.toString()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    var rel = source.relativize(file);
                    if (SKIPPED_TOP_LEVEL.contains(rel.getName(0).toString())) return FileVisitResult.CONTINUE;
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE; // never follow links or copy specials

                    var destination = target.resolve(rel.toString());
                    if (Files.exists(destination)) {
                        if (sameContent(file, destination)) {
                            counters[1]++;
                        } else {
                            conflicts.add(rel.toString());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    copyVerified(file, destination);
                    created.add(destination);
                    counters[0]++;
                    return FileVisitResult.CONTINUE;
                }
            });

            for (var conflict : conflicts) {
                warn.accept("'" + conflict + "' exists in both folders with different content. The JTLogin copy was kept, the legacy copy stays in '" + source.getFileName() + "'.");
            }

            writeMarker(target, source, counters[0], conflicts);
        } catch (IOException | RuntimeException e) {
            rollback(created);
            return new Result(Outcome.FAILED, source, target, 0, 0, List.copyOf(conflicts), e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        info.accept("Migrated " + counters[0] + " file(s) from '" + source.getFileName() + "' (" + counters[1] + " already present and identical).");
        return new Result(Outcome.MIGRATED, source, target, counters[0], counters[1], List.copyOf(conflicts), null);
    }

    private Path findLegacyFolder(Path target) {
        for (var name : LegacyNames.legacyFolderNames(targetFolderName)) {
            var candidate = pluginsDirectory.resolve(name);
            if (!Files.isDirectory(candidate)) continue;
            try {
                if (Files.isSameFile(candidate, target)) continue; // case-insensitive file systems
            } catch (IOException ignored) {
                // target does not exist yet, so it cannot be the same file
            }
            if (hasMigratableContent(candidate)) return candidate;
        }
        return null;
    }

    private static boolean hasMigratableContent(Path folder) {
        try (var entries = Files.list(folder)) {
            return entries.anyMatch(p -> !SKIPPED_TOP_LEVEL.contains(p.getFileName().toString()));
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyVerified(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        var temporary = destination.resolveSibling(destination.getFileName() + ".jtlogin-tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (!sameContent(source, temporary)) {
                throw new IOException("Verification failed for '" + source.getFileName() + "' (the source may be in use by another process; stop the server running LibreLogin first)");
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void rollback(List<Path> created) {
        for (var path : created) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // best effort, the caller aborts startup anyway
            }
        }
    }

    private static void writeMarker(Path target, Path source, int copied, List<String> conflicts) throws IOException {
        var content = new StringBuilder()
                .append("source=").append(source.getFileName()).append('\n')
                .append("migratedAt=").append(Instant.now()).append('\n')
                .append("filesCopied=").append(copied).append('\n');
        for (var conflict : conflicts) content.append("conflict=").append(conflict).append('\n');
        Files.writeString(target.resolve(MARKER_FILE), content.toString());
    }

    static boolean sameContent(Path a, Path b) throws IOException {
        if (Files.size(a) != Files.size(b)) return false;
        return sha256(a).equals(sha256(b));
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
