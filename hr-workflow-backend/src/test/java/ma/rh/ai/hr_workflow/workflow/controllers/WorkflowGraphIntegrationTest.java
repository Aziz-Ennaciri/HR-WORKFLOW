package ma.rh.ai.hr_workflow.workflow.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PUT /api/v1/workflows/{id}/graph")
class WorkflowGraphIntegrationTest extends AbstractIntegrationTest {

    @Autowired private NodeRepository nodeRepository;
    @Autowired private WorkflowRepository workflowRepository;

    private String token;
    private Long workflowId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("graph@test.com", "pass", RoleName.ROLE_RH);
        token = getAuthToken("graph@test.com", "pass");
        String created = mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Graph WF\",\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        workflowId = objectMapper.readTree(created).get("id").asLong();
    }

    private ResultActions saveGraph(String body) throws Exception {
        return mockMvc.perform(put("/api/v1/workflows/" + workflowId + "/graph")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("creates new nodes in chain order and rewrites edges/layout to their ids")
    void saveGraph_createsNodesInChainOrder() throws Exception {
        // Listed out of order on purpose: the order comes from the edges.
        saveGraph("""
                {"nodes":[
                   {"id":"gpt-1","type":"GPT","configJson":"{\\"provider\\":\\"ollama\\"}"},
                   {"id":"drive-1","type":"DRIVE","configJson":"{\\"folderId\\":\\"cvs\\"}"}],
                 "edges":[{"id":"tmp","source":"drive-1","target":"gpt-1","type":"deletable"}],
                 "layout":{"drive-1":{"x":100,"y":150},"gpt-1":{"x":350,"y":150},"stale":{"x":0,"y":0}}}
                """).andExpect(status().isOk());

        List<Node> nodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        assertThat(nodes).extracting(Node::getType).containsExactly(NodeType.DRIVE, NodeType.GPT);
        assertThat(nodes).extracting(Node::getOrderIndex).containsExactly(1, 2);
        String driveId = nodes.get(0).getId().toString();
        String gptId = nodes.get(1).getId().toString();

        Workflow workflow = workflowRepository.findById(workflowId).orElseThrow();
        JsonNode edges = objectMapper.readTree(workflow.getEdgesJson());
        assertThat(edges.get(0).get("source").asText()).isEqualTo(driveId);
        assertThat(edges.get(0).get("target").asText()).isEqualTo(gptId);
        assertThat(edges.get(0).get("type").asText()).isEqualTo("deletable");
        JsonNode layout = objectMapper.readTree(workflow.getLayoutJson());
        assertThat(layout.get(driveId).get("x").asInt()).isEqualTo(100);
        assertThat(layout.has("stale")).isFalse();
    }

    @Test
    @DisplayName("updates kept nodes, adds new ones and deletes removed ones in one save")
    void saveGraph_upsertsAndDeletes() throws Exception {
        saveGraph("""
                {"nodes":[{"id":"a","type":"DRIVE"},{"id":"b","type":"EMAIL"}],
                 "edges":[{"source":"a","target":"b"}]}
                """).andExpect(status().isOk());
        List<Node> first = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        String driveId = first.get(0).getId().toString();

        saveGraph("""
                {"nodes":[{"id":"%s","type":"DRIVE","configJson":"{\\"folderId\\":\\"new\\"}"},{"id":"c","type":"GPT"}],
                 "edges":[{"source":"%s","target":"c"}]}
                """.formatted(driveId, driveId)).andExpect(status().isOk());

        List<Node> second = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        assertThat(second).extracting(Node::getType).containsExactly(NodeType.DRIVE, NodeType.GPT);
        assertThat(second.get(0).getId().toString()).isEqualTo(driveId);
        assertThat(second.get(0).getConfigJson()).isEqualTo("{\"folderId\":\"new\"}");
        assertThat(nodeRepository.findById(first.get(1).getId())).isEmpty();
    }

    private static final String VALID_CHAIN = """
            {"nodes":[{"id":"a","type":"DRIVE","configJson":"{\\"action\\":\\"read\\",\\"folderId\\":\\"cvs\\"}"},
                      {"id":"b","type":"GPT","configJson":"{\\"prompt\\":\\"Rank\\"}"}],
             "edges":[{"source":"a","target":"b"}]}
            """;

    private void activate() throws Exception {
        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Graph WF\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a draft can be saved half-built: unconnected steps are ordered left to right")
    void saveGraph_incompleteDraft_savedInCanvasOrder() throws Exception {
        saveGraph("""
                {"nodes":[{"id":"right","type":"EMAIL"},{"id":"left","type":"DRIVE"}],
                 "edges":[],
                 "layout":{"left":{"x":10,"y":0},"right":{"x":400,"y":0}}}
                """).andExpect(status().isOk());

        assertThat(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId))
                .extracting(Node::getType).containsExactly(NodeType.DRIVE, NodeType.EMAIL);
    }

    @Test
    @DisplayName("an ACTIVE workflow must stay a single chain: a branching save is rejected and nothing changes")
    void saveGraph_activeBranching_rejectedAtomically() throws Exception {
        saveGraph(VALID_CHAIN).andExpect(status().isOk());
        activate();
        List<Node> before = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        String a = before.get(0).getId().toString();
        String b = before.get(1).getId().toString();

        saveGraph("""
                {"nodes":[{"id":"%s","type":"DRIVE"},{"id":"%s","type":"GPT"},{"id":"c","type":"EMAIL"}],
                 "edges":[{"source":"%s","target":"%s"},{"source":"%s","target":"c"}]}
                """.formatted(a, b, a, b, a))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("branching")));

        assertThat(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId)).hasSize(2);
    }

    @Test
    @DisplayName("activation requires several steps to be connected")
    void activate_unconnectedSteps_rejected() throws Exception {
        saveGraph("""
                {"nodes":[{"id":"a","type":"DRIVE","configJson":"{}"},{"id":"b","type":"GPT","configJson":"{\\"prompt\\":\\"Rank\\"}"}],
                 "edges":[]}
                """).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Graph WF\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("single chain")));
    }

    @Test
    @DisplayName("a connection to an unknown node is rejected even in a draft")
    void saveGraph_unknownEndpoint_rejected() throws Exception {
        saveGraph("""
                {"nodes":[{"id":"a","type":"DRIVE"}],"edges":[{"source":"a","target":"ghost"}]}
                """).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a node id from another workflow is rejected")
    void saveGraph_foreignNodeId_rejected() throws Exception {
        Node foreign = new Node();
        Workflow other = new Workflow();
        other.setName("Other");
        other.setStatus(ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus.DRAFT);
        other.setVersion(1);
        other.setCreatedBy(userRepository.findByEmail("graph@test.com").orElseThrow());
        foreign.setWorkflow(workflowRepository.save(other));
        foreign.setType(NodeType.EMAIL);
        foreign.setOrderIndex(1);
        Long foreignId = nodeRepository.save(foreign).getId();

        saveGraph("{\"nodes\":[{\"id\":\"" + foreignId + "\",\"type\":\"EMAIL\"}],\"edges\":[]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unknown node type is rejected")
    void saveGraph_unknownType_rejected() throws Exception {
        saveGraph("{\"nodes\":[{\"id\":\"a\",\"type\":\"FAX\"}],\"edges\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid node type: FAX"));
    }

    @Test
    @DisplayName("activation is refused when stored connections do not form a chain")
    void activate_withBranchingEdges_rejected() throws Exception {
        saveGraph("""
                {"nodes":[{"id":"a","type":"DRIVE"},{"id":"b","type":"GPT"}],
                 "edges":[{"source":"a","target":"b"}]}
                """).andExpect(status().isOk());
        List<Node> nodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        String a = nodes.get(0).getId().toString();
        String b = nodes.get(1).getId().toString();
        String loop = "[{\\\"source\\\":\\\"" + a + "\\\",\\\"target\\\":\\\"" + b + "\\\"},"
                + "{\\\"source\\\":\\\"" + b + "\\\",\\\"target\\\":\\\"" + a + "\\\"}]";

        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Graph WF\",\"status\":\"ACTIVE\",\"edgesJson\":\"" + loop + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("loop")));
    }

    @Test
    @DisplayName("another user's workflow cannot be edited")
    void saveGraph_otherUser_forbidden() throws Exception {
        createUser("intruder@test.com", "pass", RoleName.ROLE_RH);
        String intruder = getAuthToken("intruder@test.com", "pass");

        mockMvc.perform(put("/api/v1/workflows/" + workflowId + "/graph")
                        .header("Authorization", intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodes\":[],\"edges\":[]}"))
                .andExpect(status().isForbidden());
    }
}
