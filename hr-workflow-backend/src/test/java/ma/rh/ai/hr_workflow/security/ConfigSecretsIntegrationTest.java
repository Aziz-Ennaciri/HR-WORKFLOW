package ma.rh.ai.hr_workflow.security;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GPT API keys in node configs")
class ConfigSecretsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private NodeRepository nodeRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String token;
    private Long workflowId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("secrets@test.com", "password1", RoleName.ROLE_RH);
        token = getAuthToken("secrets@test.com", "password1");
        String created = mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secrets\"}"))
                .andReturn().getResponse().getContentAsString();
        workflowId = objectMapper.readTree(created).get("id").asLong();
    }

    private void saveGptNode(String id, String apiKey) throws Exception {
        String config = "{\\\"provider\\\":\\\"openai\\\",\\\"apiKey\\\":\\\"" + apiKey + "\\\",\\\"prompt\\\":\\\"rank\\\"}";
        mockMvc.perform(put("/api/v1/workflows/" + workflowId + "/graph")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodes\":[{\"id\":\"" + id + "\",\"type\":\"GPT\",\"configJson\":\"" + config + "\"}],\"edges\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the key is encrypted in the database, masked in the API, and kept when the mask is saved back")
    void apiKey_encrypted_masked_andKept() throws Exception {
        saveGptNode("new-gpt", "sk-secret-123");
        Node node = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId).get(0);

        String stored = jdbcTemplate.queryForObject("SELECT config_json FROM nodes WHERE id = ?", String.class, node.getId());
        assertThat(stored).doesNotContain("sk-secret-123").contains("enc:v1:").contains("\"prompt\":\"rank\"");

        mockMvc.perform(get("/api/v1/nodes/workflow/" + workflowId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configJson").value(org.hamcrest.Matchers.containsString("\"apiKey\":\"********\"")))
                .andExpect(jsonPath("$[0].configJson").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-secret"))));

        // The editor sends back what it received; the stored key must survive.
        saveGptNode(node.getId().toString(), "********");
        assertThat(nodeRepository.findById(node.getId()).orElseThrow().getConfigJson()).contains("\"apiKey\":\"sk-secret-123\"");

        // A new value replaces it.
        saveGptNode(node.getId().toString(), "sk-rotated-456");
        assertThat(nodeRepository.findById(node.getId()).orElseThrow().getConfigJson()).contains("\"apiKey\":\"sk-rotated-456\"");
    }

    @Test
    @DisplayName("a key stored before encryption existed is still readable")
    void legacyPlaintextKey_readable() throws Exception {
        saveGptNode("new-gpt", "placeholder");
        Long nodeId = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId).get(0).getId();
        jdbcTemplate.update("UPDATE nodes SET config_json = ? WHERE id = ?",
                "{\"provider\":\"openai\",\"apiKey\":\"sk-legacy\"}", nodeId);
        entityManager.clear();

        assertThat(nodeRepository.findById(nodeId).orElseThrow().getConfigJson()).contains("\"apiKey\":\"sk-legacy\"");
    }
}
