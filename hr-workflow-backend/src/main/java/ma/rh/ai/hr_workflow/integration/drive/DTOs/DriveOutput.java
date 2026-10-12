package ma.rh.ai.hr_workflow.integration.drive.DTOs;

/**
 * JSON field names of the combined payload a DRIVE "read" node hands to the next node:
 * <pre>{ "originalInput": {...}, "fileData": { "totalFiles": N, "folder": "...", "files": [{ "fileName", "content" }] } }</pre>
 * Producers and consumers must use these constants so the contract cannot drift.
 */
public final class DriveOutput {

    public static final String ORIGINAL_INPUT = "originalInput";
    public static final String FILE_DATA = "fileData";
    public static final String TOTAL_FILES = "totalFiles";
    public static final String FOLDER = "folder";
    public static final String FILES = "files";
    public static final String FILE_NAME = "fileName";
    public static final String CONTENT = "content";

    private DriveOutput() {
    }
}
