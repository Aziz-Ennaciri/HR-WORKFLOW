package ma.rh.ai.hr_workflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("StoragePaths")
class StoragePathsTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("resolves folder and file inside the root, skipping blank parts")
    void resolvesInsideRoot() {
        assertThat(StoragePaths.resolveInside(root.toString(), "cvs", "alice.pdf"))
                .isEqualTo(root.resolve("cvs/alice.pdf").toAbsolutePath().normalize());
        assertThat(StoragePaths.resolveInside(root.toString(), "", "report.xlsx"))
                .isEqualTo(root.resolve("report.xlsx").toAbsolutePath().normalize());
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../outside", "cvs/../../outside", "/etc"})
    @DisplayName("rejects paths that escape the root")
    void rejectsTraversal(String folder) {
        assertThrows(IllegalArgumentException.class,
                () -> StoragePaths.resolveInside(root.toString(), folder, "passwd"));
    }
}
