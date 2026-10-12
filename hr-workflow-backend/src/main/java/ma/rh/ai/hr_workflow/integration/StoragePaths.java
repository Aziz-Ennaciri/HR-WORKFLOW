package ma.rh.ai.hr_workflow.integration;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves user-supplied folder/file names (from node config) strictly inside a storage root. */
public final class StoragePaths {

    private StoragePaths() {
    }

    /**
     * Joins {@code parts} onto {@code root} and rejects any result that escapes it
     * (e.g. {@code "../../etc"} or an absolute path).
     */
    public static Path resolveInside(String root, String... parts) {
        Path base = Paths.get(root).toAbsolutePath().normalize();
        Path resolved = base;
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                resolved = resolved.resolve(part);
            }
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Path escapes the storage directory: " + String.join("/", parts));
        }
        return resolved;
    }
}
