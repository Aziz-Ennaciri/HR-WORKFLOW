package ma.rh.ai.hr_workflow.integration.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ma.rh.ai.hr_workflow.integration.drive.service.Impl.LocalDriveServiceImpl;
import ma.rh.ai.hr_workflow.integration.gpt.service.Impl.RealGptServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Feeds the real DRIVE "read" output into the GPT service, so the two sides cannot drift apart again. */
@DisplayName("DRIVE → GPT payload contract")
class DriveToGptContractTest {

    @TempDir
    Path storage;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("GPT builds its prompt from the user prompt plus every file DRIVE read")
    void gptPromptContainsDriveFiles() throws Exception {
        Files.createDirectories(storage.resolve("cvs"));
        Files.writeString(storage.resolve("cvs/alice.txt"), "Alice has 5 years of Java");
        Files.writeString(storage.resolve("cvs/bob.txt"), "Bob has 2 years of Python");

        LocalDriveServiceImpl drive = new LocalDriveServiceImpl(objectMapper);
        ReflectionTestUtils.setField(drive, "storagePath", storage.toString());
        String driveOutput = drive.saveFile("{\"action\":\"read\",\"folderId\":\"cvs\"}",
                "{\"prompt\":\"Rank the candidates\"}");

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                        .thenReturn("{\"response\":\"[]\",\"eval_count\":1}"))) {

            RealGptServiceImpl gpt = new RealGptServiceImpl(objectMapper);
            ReflectionTestUtils.setField(gpt, "ollamaUrl", "http://localhost:11434/api/generate");
            ReflectionTestUtils.setField(gpt, "defaultOllamaModel", "llama3.2:3b");
            gpt.analyze("{\"provider\":\"ollama\"}", driveOutput);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<HttpEntity<String>> request = ArgumentCaptor.forClass(HttpEntity.class);
            verify(mocked.constructed().get(0)).postForObject(anyString(), request.capture(), eq(String.class));

            JsonNode body = objectMapper.readTree(request.getValue().getBody());
            assertThat(body.get("prompt").asText())
                    .startsWith("Rank the candidates")
                    .contains("--- alice.txt ---", "Alice has 5 years of Java")
                    .contains("--- bob.txt ---", "Bob has 2 years of Python");
        }
    }

    @Test
    @DisplayName("the GPT node's own prompt is the instruction, even when the run was triggered with empty input")
    void gptNodePromptIsUsed() throws Exception {
        Files.createDirectories(storage.resolve("cvs"));
        Files.writeString(storage.resolve("cvs/alice.txt"), "Alice has 5 years of Java");

        LocalDriveServiceImpl drive = new LocalDriveServiceImpl(objectMapper);
        ReflectionTestUtils.setField(drive, "storagePath", storage.toString());
        // The editor's "Execute" button triggers with "{}"
        String driveOutput = drive.saveFile("{\"action\":\"read\",\"folderId\":\"cvs\"}", "{}");

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                        .thenReturn("{\"response\":\"[]\",\"eval_count\":1}"))) {

            RealGptServiceImpl gpt = new RealGptServiceImpl(objectMapper);
            ReflectionTestUtils.setField(gpt, "ollamaUrl", "http://localhost:11434/api/generate");
            ReflectionTestUtils.setField(gpt, "defaultOllamaModel", "llama3.2:3b");
            gpt.analyze("{\"provider\":\"ollama\",\"prompt\":\"Rank Java developers\"}", driveOutput);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<HttpEntity<String>> request = ArgumentCaptor.forClass(HttpEntity.class);
            verify(mocked.constructed().get(0)).postForObject(anyString(), request.capture(), eq(String.class));

            assertThat(objectMapper.readTree(request.getValue().getBody()).get("prompt").asText())
                    .startsWith("Rank Java developers")
                    .contains("--- alice.txt ---", "Alice has 5 years of Java");
        }
    }
}
