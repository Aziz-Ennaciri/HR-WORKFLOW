package ma.rh.ai.hr_workflow.integration.controllers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/drive")
@RequiredArgsConstructor
public class DriveController {

    @Value("${drive.storage.path:/tmp/hr-workflow-files}")
    private String storagePath;

    @GetMapping("/download/{folder}/{fileName}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String folder,
            @PathVariable String fileName) {

        try {
            Path filePath = resolveInsideStorage(folder, fileName);

            if (filePath == null) {
                return ResponseEntity.badRequest().build();
            }
            if (!Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to download file", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/list/{folder}")
    public ResponseEntity<?> listFiles(@PathVariable String folder) {
        try {
            Path folderPath = resolveInsideStorage(folder);

            if (folderPath == null) {
                return ResponseEntity.badRequest().build();
            }
            if (!Files.isDirectory(folderPath)) {
                return ResponseEntity.notFound().build();
            }

            try (var paths = Files.list(folderPath)) {
                var files = paths
                        .map(path -> {
                            var file = path.toFile();
                            return new FileInfo(
                                    file.getName(),
                                    file.length(),
                                    Files.isDirectory(path)
                            );
                        })
                        .toList();
                return ResponseEntity.ok(files);
            }

        } catch (Exception e) {
            log.error("Failed to list files", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Resolves the path under the storage root, or returns null if the segments try to
     * escape it (e.g. "../../etc/passwd").
     */
    private Path resolveInsideStorage(String... segments) {
        Path root = Paths.get(storagePath).toAbsolutePath().normalize();
        Path resolved = Paths.get(root.toString(), segments).normalize();
        return resolved.startsWith(root) && !resolved.equals(root) ? resolved : null;
    }

    record FileInfo(String name, long size, boolean isDirectory) {}
}