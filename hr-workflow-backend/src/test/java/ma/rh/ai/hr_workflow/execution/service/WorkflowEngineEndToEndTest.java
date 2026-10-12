package ma.rh.ai.hr_workflow.execution.service;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.config.ExecutionProperties;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.execution.service.Impl.ExecutionRecovery;
import ma.rh.ai.hr_workflow.execution.service.Impl.ExecutionTransactionHelper;
import ma.rh.ai.hr_workflow.execution.service.Impl.WorkflowExecutionServiceImpl;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Drives real executions through the background engine against PostgreSQL. Uses APPROVAL and
 * local DRIVE (write) steps, which need no external service.
 */
@DisplayName("Workflow engine end-to-end")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineEndToEndTest extends AbstractIntegrationTest {

    @Autowired private IWorkflowExecutionService executionService;
    @Autowired private WorkflowExecutionServiceImpl executionServiceImpl;
    @Autowired private ExecutionTransactionHelper txHelper;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private NodeRepository nodeRepository;
    @Autowired private WorkflowInstancerepository instanceRepository;
    @Autowired private NodeInstanceRepository nodeInstanceRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Value("${drive.storage.path}")
    private String storagePath;

    private User owner;
    private String approverToken;
    private String folder;
    private Workflow workflow;
    private Node driveNode;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE node_instances, workflow_instances, nodes, user_roles, workflows, users RESTART IDENTITY CASCADE");
        owner = createUser("owner@test.com", "pass", RoleName.ROLE_RH);
        createUser("approver@test.com", "pass", RoleName.ROLE_RH);
        approverToken = getAuthToken("approver@test.com", "pass");
        folder = "e2e-" + UUID.randomUUID();

        workflow = new Workflow();
        workflow.setName("Approval then Drive");
        workflow.setStatus(WorkflowStatus.ACTIVE);
        workflow.setVersion(1);
        workflow.setCreatedBy(owner);
        workflow = workflowRepository.save(workflow);

        Node approval = new Node();
        approval.setWorkflow(workflow);
        approval.setType(NodeType.APPROVAL);
        approval.setOrderIndex(1);
        approval.setConfigJson("{\"approverEmail\":\"approver@test.com\",\"instructions\":\"check\"}");
        nodeRepository.save(approval);

        driveNode = new Node();
        driveNode.setWorkflow(workflow);
        driveNode.setType(NodeType.DRIVE);
        driveNode.setOrderIndex(2);
        driveNode.setConfigJson(driveConfig("result.txt"));
        driveNode = nodeRepository.save(driveNode);
    }

    private String driveConfig(String fileName) {
        return "{\"action\":\"write\",\"folderId\":\"" + folder + "\",\"fileName\":\"" + fileName + "\"}";
    }

    private Long trigger(String input) {
        TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
        dto.setWorkflowId(workflow.getId());
        dto.setInputData(input);
        return executionService.triggerWorkflow(dto, owner).getId();
    }

    private WorkflowInstanceStatus statusOf(Long executionId) {
        return instanceRepository.findById(executionId).orElseThrow().getStatus();
    }

    private List<NodeInstance> stepsOf(Long executionId) {
        return nodeInstanceRepository.findByWorkflowInstanceIdOrderByExecutionOrderAsc(executionId);
    }

    private int decide(Long stepId, String decision) throws Exception {
        return mockMvc.perform(post("/api/v1/executions/nodes/" + stepId + "/" + decision)
                        .header("Authorization", approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + decision + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("pauses at approval; a double-clicked approve resumes exactly once with the config frozen at trigger time")
    void approval_doubleApprove_resumesOnce_withFrozenConfig() throws Exception {
        Long executionId = trigger("candidate data");
        await("execution to pause", () -> statusOf(executionId) == WorkflowInstanceStatus.PAUSED);
        NodeInstance approvalStep = stepsOf(executionId).get(0);
        assertThat(approvalStep.getStatus()).isEqualTo(NodeInstanceStatus.WAITING_APPROVAL);

        // Editing the workflow after the trigger must not change what this execution does.
        driveNode.setConfigJson(driveConfig("edited.txt"));
        nodeRepository.save(driveNode);

        ExecutorService clicks = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> click = () -> {
            start.await();
            return decide(approvalStep.getId(), "approve");
        };
        Future<Integer> first = clicks.submit(click);
        Future<Integer> second = clicks.submit(click);
        start.countDown();
        List<Integer> statuses = List.of(first.get(), second.get());
        clicks.shutdown();

        assertThat(statuses).containsOnlyOnce(200);
        assertThat(statuses).anyMatch(status -> status == 400 || status == 409);

        await("execution to complete", () -> statusOf(executionId) == WorkflowInstanceStatus.COMPLETED);
        NodeInstance driveStep = stepsOf(executionId).get(1);
        assertThat(driveStep.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
        assertThat(driveStep.getRetryCount()).isZero();
        assertThat(driveStep.getInputData()).isEqualTo("candidate data");

        Path written = Path.of(storagePath, folder, "result.txt");
        assertThat(Files.readString(written)).isEqualTo("candidate data");
        assertThat(Path.of(storagePath, folder, "edited.txt")).doesNotExist();
    }

    @Test
    @DisplayName("rejecting the approval fails the execution and later steps never run")
    void reject_failsExecution() throws Exception {
        Long executionId = trigger("candidate data");
        await("execution to pause", () -> statusOf(executionId) == WorkflowInstanceStatus.PAUSED);

        assertThat(decide(stepsOf(executionId).get(0).getId(), "reject")).isEqualTo(200);

        WorkflowInstance execution = instanceRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
        assertThat(execution.getErrorMessage()).contains("Rejected").contains("approver@test.com");
        assertThat(stepsOf(executionId).get(1).getStatus()).isEqualTo(NodeInstanceStatus.PENDING);
        assertThat(Path.of(storagePath, folder, "result.txt")).doesNotExist();
    }

    @Test
    @DisplayName("deleting a node keeps the history of executions that used it")
    void deletingNode_keepsHistory() throws Exception {
        Long executionId = trigger("candidate data");
        await("execution to pause", () -> statusOf(executionId) == WorkflowInstanceStatus.PAUSED);

        nodeRepository.deleteById(driveNode.getId());

        NodeInstance orphan = stepsOf(executionId).get(1);
        assertThat(orphan.getNode()).isNull();
        assertThat(orphan.getNodeType()).isEqualTo(NodeType.DRIVE);
        assertThat(orphan.getConfigSnapshot()).contains("result.txt");
    }

    @Test
    @DisplayName("after a restart, an execution that was mid-step is failed (not re-run), and a lost resume is picked up")
    void recovery_failsInterrupted_andSweepsLostResume() throws Exception {
        // Execution A: the server died while its DRIVE step was running.
        Long interrupted = trigger("a");
        await("A to pause", () -> statusOf(interrupted) == WorkflowInstanceStatus.PAUSED);
        jdbcTemplate.update("UPDATE node_instances SET status = 'COMPLETED' WHERE workflow_instance_id = ? AND execution_order = 1", interrupted);
        jdbcTemplate.update("UPDATE node_instances SET status = 'IN_PROGRESS' WHERE workflow_instance_id = ? AND execution_order = 2", interrupted);
        jdbcTemplate.update("UPDATE workflow_instances SET status = 'RUNNING' WHERE id = ?", interrupted);

        // Execution B: approved, but the server died before the resume was queued.
        Long lostResume = trigger("b");
        await("B to pause", () -> statusOf(lostResume) == WorkflowInstanceStatus.PAUSED);
        jdbcTemplate.update("UPDATE node_instances SET status = 'COMPLETED', output_data = 'b' WHERE workflow_instance_id = ? AND execution_order = 1", lostResume);

        ExecutionProperties enabled = new ExecutionProperties(
                new ExecutionProperties.Worker(1, 1, 1, Duration.ofSeconds(1)),
                new ExecutionProperties.Retry(1, Duration.ofMillis(1), 1.0),
                new ExecutionProperties.Sweeper(true, Duration.ofSeconds(30), Duration.ofSeconds(30)));
        new ExecutionRecovery(instanceRepository, txHelper, executionServiceImpl, enabled).recoverAfterRestart();

        WorkflowInstance a = instanceRepository.findById(interrupted).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
        assertThat(a.getErrorMessage()).contains("restart");
        assertThat(stepsOf(interrupted).get(1).getStatus()).isEqualTo(NodeInstanceStatus.FAILED);

        await("B to complete", () -> statusOf(lostResume) == WorkflowInstanceStatus.COMPLETED);
        assertThat(Files.readString(Path.of(storagePath, folder, "result.txt"))).isEqualTo("b");
    }

    @Test
    @DisplayName("an execution left PENDING (never picked up) is started by the sweeper")
    void sweeper_startsStalePending() throws Exception {
        Long executionId = trigger("candidate data");
        await("execution to pause", () -> statusOf(executionId) == WorkflowInstanceStatus.PAUSED);
        jdbcTemplate.update("UPDATE node_instances SET status = 'PENDING', assigned_to_id = NULL, started_at = NULL WHERE workflow_instance_id = ?", executionId);
        jdbcTemplate.update("UPDATE workflow_instances SET status = 'PENDING', created_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(5), executionId);

        ExecutionProperties enabled = new ExecutionProperties(
                new ExecutionProperties.Worker(1, 1, 1, Duration.ofSeconds(1)),
                new ExecutionProperties.Retry(1, Duration.ofMillis(1), 1.0),
                new ExecutionProperties.Sweeper(true, Duration.ofSeconds(30), Duration.ofSeconds(30)));
        new ExecutionRecovery(instanceRepository, txHelper, executionServiceImpl, enabled).sweep();

        await("execution to pause again", () -> statusOf(executionId) == WorkflowInstanceStatus.PAUSED);
        assertThat(stepsOf(executionId).get(0).getStatus()).isEqualTo(NodeInstanceStatus.WAITING_APPROVAL);
    }
}
