package ma.rh.ai.hr_workflow.execution.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.integration.gpt.service.GptService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GptNodeHandler")
class GptNodeHandlerTest {

    @Mock private GptService gptService;

    private ObjectMapper objectMapper;
    private GptNodeHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new GptNodeHandler(gptService, objectMapper);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Node buildNode(String configJson) {
        Node n = new Node();
        n.setId(1L);
        n.setConfigJson(configJson);
        return n;
    }

    private NodeInstance buildNodeInstance(String nodeInputData, String workflowInputData) {
        WorkflowInstance wfInstance = new WorkflowInstance();
        wfInstance.setInputData(workflowInputData);

        NodeInstance ni = new NodeInstance();
        ni.setInputData(nodeInputData);
        ni.setWorkflowInstance(wfInstance);
        return ni;
    }

    // combined drive output JSON
    private String combinedDriveJson(String prompt) {
        return "{\"originalInput\":{\"prompt\":\"" + prompt + "\"},"
                + "\"cvData\":{\"totalCVs\":2,\"folder\":\"cv_uploads\","
                + "\"cvs\":[{\"fileName\":\"alice.pdf\",\"content\":\"Alice 5yr Java\"},"
                + "{\"fileName\":\"bob.pdf\",\"content\":\"Bob 2yr Python\"}]}}";
    }

    // ─── getType ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getType()")
    class GetType {

        @Test
        @DisplayName("returns NodeType.GPT")
        void getType_returnsGpt() {
            assertThat(handler.getType()).isEqualTo(NodeType.GPT);
        }
    }

    // ─── execute — combined DRIVE output path ────────────────────────────────────

    @Nested
    @DisplayName("execute() — combined DRIVE output (isDriveCombinedOutput = true)")
    class ExecuteCombinedPath {

        @Test
        @DisplayName("happy path — nodeInputData is combined JSON → passes it directly to GptService")
        void execute_combinedDriveOutput_passesDirectly() throws Exception {
            // Arrange
            String config      = "{\"provider\":\"ollama\"}";
            String driveOutput = combinedDriveJson("Rank the top 3 candidates");
            Node node = buildNode(config);
            NodeInstance ni = buildNodeInstance(driveOutput, "{\"criteria\":\"Java\"}");
            String gptResult = "{\"analysis\":\"Alice is the best\"}";

            when(gptService.analyze(config, driveOutput)).thenReturn(gptResult);

            // Act
            String result = handler.execute(node, ni);

            // Assert
            assertThat(result).isEqualTo(gptResult);
            verify(gptService).analyze(config, driveOutput);
        }

        @Test
        @DisplayName("happy path — workflowInputData is irrelevant when drive output is combined")
        void execute_combinedDriveOutput_ignoresWorkflowInput() throws Exception {
            // Arrange
            String config      = "{}";
            String driveOutput = combinedDriveJson("Find senior developers");
            Node node = buildNode(config);
            // workflowInputData is present but should be ignored
            NodeInstance ni = buildNodeInstance(driveOutput, "FILTERING_CRITERIA:{\"Profile\":\"Dev\"}");
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert — GptService receives the combined output, NOT the legacy merge
            ArgumentCaptor<String> inputCaptor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(eq(config), inputCaptor.capture());
            assertThat(inputCaptor.getValue()).isEqualTo(driveOutput);
            assertThat(inputCaptor.getValue()).doesNotContain("FILTERING_CRITERIA");
        }
    }

    // ─── execute — legacy merge path ─────────────────────────────────────────────

    @Nested
    @DisplayName("execute() — legacy merge path (isDriveCombinedOutput = false)")
    class ExecuteLegacyPath {

        @Test
        @DisplayName("happy path — both workflowInput and nodeInput present → FILTERING_CRITERIA + CANDIDATE_DATA")
        void execute_bothInputsPresent_legacyMerge() throws Exception {
            // Arrange
            String config          = "{\"provider\":\"ollama\"}";
            String workflowInput   = "{\"Profile\":\"Java Developer\"}";
            String nodeInput       = "Alice|5yr|Java\nBob|2yr|Python";
            Node node = buildNode(config);
            NodeInstance ni = buildNodeInstance(nodeInput, workflowInput);
            when(gptService.analyze(any(), any())).thenReturn("result");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> inputCaptor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(eq(config), inputCaptor.capture());
            String merged = inputCaptor.getValue();
            assertThat(merged).contains("FILTERING_CRITERIA:" + workflowInput);
            assertThat(merged).contains("CANDIDATE_DATA:" + nodeInput);
        }

        @Test
        @DisplayName("happy path — null nodeInputData → legacy merge with only FILTERING_CRITERIA")
        void execute_nullNodeInput_onlyFilteringCriteria() throws Exception {
            // Arrange
            String config        = "{}";
            String workflowInput = "{\"Profile\":\"Dev\"}";
            Node node = buildNode(config);
            NodeInstance ni = buildNodeInstance(null, workflowInput);
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).contains("FILTERING_CRITERIA:" + workflowInput);
            assertThat(captor.getValue()).doesNotContain("CANDIDATE_DATA");
        }

        @Test
        @DisplayName("happy path — blank nodeInputData → legacy merge with only FILTERING_CRITERIA")
        void execute_blankNodeInput_onlyFilteringCriteria() throws Exception {
            // Arrange
            String config        = "{}";
            String workflowInput = "{\"Profile\":\"Dev\"}";
            Node node = buildNode(config);
            NodeInstance ni = buildNodeInstance("   ", workflowInput);
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).contains("FILTERING_CRITERIA");
            assertThat(captor.getValue()).doesNotContain("CANDIDATE_DATA");
        }

        @Test
        @DisplayName("happy path — null workflowInput → legacy merge with only CANDIDATE_DATA")
        void execute_nullWorkflowInput_onlyCandidateData() throws Exception {
            // Arrange
            String config    = "{}";
            String nodeInput = "Alice|5yr|Java";
            Node node = buildNode(config);
            NodeInstance ni = buildNodeInstance(nodeInput, null);
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).contains("CANDIDATE_DATA:" + nodeInput);
            assertThat(captor.getValue()).doesNotContain("FILTERING_CRITERIA");
        }

        @Test
        @DisplayName("happy path — blank workflowInput → legacy merge with only CANDIDATE_DATA")
        void execute_blankWorkflowInput_onlyCandidateData() throws Exception {
            // Arrange
            String config    = "{}";
            String nodeInput = "Carol|3yr|Python";
            Node node = buildNode(config);
            NodeInstance ni = buildNodeInstance(nodeInput, "   ");
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).contains("CANDIDATE_DATA:" + nodeInput);
            assertThat(captor.getValue()).doesNotContain("FILTERING_CRITERIA");
        }

        @Test
        @DisplayName("edge case — both inputs null/blank → empty string sent to GptService")
        void execute_bothInputsNullOrBlank_sendsEmptyString() throws Exception {
            // Arrange
            Node node = buildNode("{}");
            NodeInstance ni = buildNodeInstance(null, null);
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("edge case — nodeInputData is plain text (not JSON) → goes to legacy merge")
        void execute_plainTextNodeInput_legacyMerge() throws Exception {
            // Arrange
            String config    = "{}";
            String nodeInput = "plain text not starting with {";
            Node node = buildNode(config);
            NodeInstance ni = buildNodeInstance(nodeInput, null);
            when(gptService.analyze(any(), any())).thenReturn("result");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).contains("CANDIDATE_DATA:" + nodeInput);
        }

        @Test
        @DisplayName("edge case — nodeInputData starts with { but is malformed JSON → falls to legacy merge")
        void execute_malformedJsonNodeInput_legacyMerge() throws Exception {
            // Arrange
            String nodeInput = "{broken json not parseable";
            Node node = buildNode("{}");
            NodeInstance ni = buildNodeInstance(nodeInput, null);
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert — isDriveCombinedOutput returns false → legacy merge
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).contains("CANDIDATE_DATA");
        }

        @Test
        @DisplayName("edge case — nodeInputData is JSON but missing cvData → falls to legacy merge")
        void execute_jsonMissingCvData_legacyMerge() throws Exception {
            // Arrange
            String nodeInput = "{\"originalInput\":{\"prompt\":\"hello\"}}"; // missing cvData
            Node node = buildNode("{}");
            NodeInstance ni = buildNodeInstance(nodeInput, null);
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).contains("CANDIDATE_DATA");
        }

        @Test
        @DisplayName("edge case — nodeInputData is JSON but missing originalInput → falls to legacy merge")
        void execute_jsonMissingOriginalInput_legacyMerge() throws Exception {
            // Arrange
            String nodeInput = "{\"cvData\":{\"cvs\":[]}}"; // missing originalInput
            Node node = buildNode("{}");
            NodeInstance ni = buildNodeInstance(nodeInput, null);
            when(gptService.analyze(any(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(any(), captor.capture());
            assertThat(captor.getValue()).contains("CANDIDATE_DATA");
        }
    }

    // ─── execute — GptService exceptions propagate ────────────────────────────────

    @Nested
    @DisplayName("execute() — GptService exception propagation")
    class ExecuteExceptionPropagation {

        @Test
        @DisplayName("GptService.analyze throws — exception propagates from execute()")
        void execute_gptServiceThrows_propagates() throws Exception {
            // Arrange
            Node node = buildNode("{}");
            NodeInstance ni = buildNodeInstance("some input", null);
            when(gptService.analyze(any(), any()))
                    .thenThrow(new RuntimeException("Ollama unavailable"));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> handler.execute(node, ni));
            assertThat(ex.getMessage()).contains("Ollama unavailable");
        }

        @Test
        @DisplayName("GptService.analyze throws checked Exception — propagates from execute()")
        void execute_gptServiceThrowsCheckedException_propagates() throws Exception {
            // Arrange
            Node node = buildNode("{}");
            NodeInstance ni = buildNodeInstance("data", null);
            when(gptService.analyze(any(), any()))
                    .thenThrow(new Exception("Network timeout"));

            // Act & Assert
            Exception ex = assertThrows(Exception.class,
                    () -> handler.execute(node, ni));
            assertThat(ex.getMessage()).contains("Network timeout");
        }
    }

    // ─── execute — configJson forwarded to GptService ────────────────────────────

    @Nested
    @DisplayName("execute() — configJson is forwarded as-is to GptService")
    class ConfigJsonForwarding {

        @Test
        @DisplayName("node configJson is passed unchanged as first arg to analyze()")
        void execute_configJsonForwardedToGpt() throws Exception {
            // Arrange
            String config = "{\"provider\":\"openai\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4o\"}";
            Node node = buildNode(config);
            NodeInstance ni = buildNodeInstance("data", null);
            when(gptService.analyze(any(), any())).thenReturn("result");

            // Act
            handler.execute(node, ni);

            // Assert
            ArgumentCaptor<String> configCaptor = ArgumentCaptor.forClass(String.class);
            verify(gptService).analyze(configCaptor.capture(), any());
            assertThat(configCaptor.getValue()).isEqualTo(config);
        }

        @Test
        @DisplayName("null node configJson — null is passed as first arg to analyze()")
        void execute_nullConfig_nullForwardedToGpt() throws Exception {
            // Arrange
            Node node = buildNode(null);
            NodeInstance ni = buildNodeInstance("some data", null);
            when(gptService.analyze(isNull(), any())).thenReturn("ok");

            // Act
            handler.execute(node, ni);

            // Assert
            verify(gptService).analyze(isNull(), any());
        }
    }
}
