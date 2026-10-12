package ma.rh.ai.hr_workflow.workflow.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NodeConfigValidator")
class NodeConfigValidatorTest {

    private final UserRepository users = mock(UserRepository.class);
    private final NodeConfigValidator validator = new NodeConfigValidator(new ObjectMapper(), users);

    private static Node node(NodeType type, String config) {
        Node node = new Node();
        node.setType(type);
        node.setOrderIndex(2);
        node.setConfigJson(config);
        return node;
    }

    @Test
    @DisplayName("complete configs pass")
    void validConfigs() {
        User approver = new User();
        approver.setEnabled(true);
        when(users.findByEmail("boss@test.com")).thenReturn(Optional.of(approver));

        assertThat(validator.validate(node(NodeType.EMAIL, "{\"to\":\"a@b.c\",\"subject\":\"Hi\"}"))).isEmpty();
        assertThat(validator.validate(node(NodeType.GPT, "{\"prompt\":\"Rank\",\"provider\":\"ollama\"}"))).isEmpty();
        assertThat(validator.validate(node(NodeType.GPT, "{\"prompt\":\"Rank\",\"provider\":\"openai\",\"apiKey\":\"sk\"}"))).isEmpty();
        assertThat(validator.validate(node(NodeType.DRIVE, "{\"action\":\"read\",\"folderId\":\"cvs\"}"))).isEmpty();
        assertThat(validator.validate(node(NodeType.EXCEL, "{\"fileName\":\"report\"}"))).isEmpty();
        assertThat(validator.validate(node(NodeType.APPROVAL, "{\"approverEmail\":\"boss@test.com\"}"))).isEmpty();
    }

    @Test
    @DisplayName("missing or invalid settings are reported per field with the step number")
    void invalidConfigs() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThat(validator.validate(node(NodeType.EMAIL, "{}")))
                .containsExactly("Step 2 (EMAIL): recipient is required", "Step 2 (EMAIL): subject is required");
        assertThat(validator.validate(node(NodeType.GPT, "{\"prompt\":\"  \",\"provider\":\"anthropic\"}")))
                .containsExactly("Step 2 (GPT): prompt is required", "Step 2 (GPT): an API key is required for anthropic");
        assertThat(validator.validate(node(NodeType.DRIVE, "{\"action\":\"delete\"}")))
                .containsExactly("Step 2 (DRIVE): action must be read or write");
        assertThat(validator.validate(node(NodeType.EXCEL, null)))
                .containsExactly("Step 2 (EXCEL): file name is required");
        assertThat(validator.validate(node(NodeType.APPROVAL, "{\"approverEmail\":\"ghost@test.com\"}")))
                .containsExactly("Step 2 (APPROVAL): approver ghost@test.com is not an active user");
        assertThat(validator.validate(node(NodeType.EMAIL, "{not json")))
                .containsExactly("Step 2 (EMAIL): settings are not valid JSON");
    }
}
