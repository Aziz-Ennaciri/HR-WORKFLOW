package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowInstanceServiceImpl")
class WorkflowInstanceServiceImplTest {

    @Mock
    private WorkflowInstancerepository workflowInstancerepository;

    @Mock
    private WorkflowRepository workflowRepository;

    private WorkflowInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowInstanceServiceImpl(workflowInstancerepository, workflowRepository);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private WorkflowInstance instanceWithStatus(WorkflowInstanceStatus status) {
        WorkflowInstance wi = new WorkflowInstance();
        wi.setStatus(status);
        return wi;
    }

    // ─── createInstance ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("createInstance()")
    class CreateInstance {

        @Test
        @DisplayName("happy path — builds WorkflowInstance with PENDING status and saves it")
        void createInstance_happyPath_savesWithPendingStatus() {
            // Arrange
            Workflow wf = new Workflow();
            wf.setId(10L);
            User user = new User();
            user.setId(1L);
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(10L, "{\"key\":\"val\"}");

            when(workflowRepository.findById(10L)).thenReturn(Optional.of(wf));

            ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
            WorkflowInstance saved = new WorkflowInstance();
            saved.setId(100L);
            when(workflowInstancerepository.save(captor.capture())).thenReturn(saved);

            // Act
            WorkflowInstance result = service.createInstance(dto, user);

            // Assert
            assertThat(result).isEqualTo(saved);
            WorkflowInstance created = captor.getValue();
            assertThat(created.getWorkflow()).isEqualTo(wf);
            assertThat(created.getTriggeredBy()).isEqualTo(user);
            assertThat(created.getStatus()).isEqualTo(WorkflowInstanceStatus.PENDING);
            assertThat(created.getInputData()).isEqualTo("{\"key\":\"val\"}");
            assertThat(created.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("happy path — null inputData is saved as null")
        void createInstance_nullInputData_savedWithNull() {
            // Arrange
            Workflow wf = new Workflow();
            wf.setId(1L);
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(1L, null);
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(wf));
            when(workflowInstancerepository.save(any())).thenReturn(new WorkflowInstance());

            // Act
            service.createInstance(dto, new User());

            // Assert
            ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
            verify(workflowInstancerepository).save(captor.capture());
            assertThat(captor.getValue().getInputData()).isNull();
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void createInstance_workflowNotFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(99L, null);

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.createInstance(dto, new User()));
            assertThat(ex.getMessage()).contains("Workflow not found");
        }
    }

    // ─── startInstance ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("startInstance()")
    class StartInstance {

        @Test
        @DisplayName("happy path — sets status to RUNNING, sets startedAt, does NOT save")
        void startInstance_setsRunningWithoutSave() {
            // Arrange
            WorkflowInstance wi = instanceWithStatus(WorkflowInstanceStatus.PENDING);
            when(workflowInstancerepository.findById(1L)).thenReturn(Optional.of(wi));

            // Act
            WorkflowInstance result = service.startInstance(1L);

            // Assert
            assertThat(result.getStatus()).isEqualTo(WorkflowInstanceStatus.RUNNING);
            assertThat(result.getStartedAt()).isNotNull();
            verify(workflowInstancerepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void startInstance_notFound_throws() {
            // Arrange
            when(workflowInstancerepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.startInstance(99L));
            assertThat(ex.getMessage()).contains("WorkflowInstance not found");
        }
    }

    // ─── completeInstance ────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeInstance()")
    class CompleteInstance {

        @Test
        @DisplayName("happy path — sets COMPLETED, outputData, finishedAt, durationMs; does NOT save")
        void completeInstance_setsCompletedWithoutSave() {
            // Arrange
            WorkflowInstance wi = new WorkflowInstance();
            wi.setStartedAt(LocalDateTime.now().minusSeconds(5));
            when(workflowInstancerepository.findById(1L)).thenReturn(Optional.of(wi));

            // Act
            WorkflowInstance result = service.completeInstance(1L, "{\"result\":\"done\"}");

            // Assert
            assertThat(result.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
            assertThat(result.getOutputData()).isEqualTo("{\"result\":\"done\"}");
            assertThat(result.getFinishedAt()).isNotNull();
            assertThat(result.getDurationMs()).isNotNull().isPositive();
            verify(workflowInstancerepository, never()).save(any());
        }

        @Test
        @DisplayName("happy path — null outputData is stored as null")
        void completeInstance_nullOutput_storedAsNull() {
            // Arrange
            WorkflowInstance wi = new WorkflowInstance();
            when(workflowInstancerepository.findById(2L)).thenReturn(Optional.of(wi));

            // Act
            WorkflowInstance result = service.completeInstance(2L, null);

            // Assert
            assertThat(result.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
            assertThat(result.getOutputData()).isNull();
        }

        @Test
        @DisplayName("edge case — no startedAt means durationMs is null (complete() skips calculation)")
        void completeInstance_noStartedAt_durationMsIsNull() {
            // Arrange
            WorkflowInstance wi = new WorkflowInstance(); // startedAt = null
            when(workflowInstancerepository.findById(3L)).thenReturn(Optional.of(wi));

            // Act
            WorkflowInstance result = service.completeInstance(3L, "out");

            // Assert
            assertThat(result.getDurationMs()).isNull();
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void completeInstance_notFound_throws() {
            // Arrange
            when(workflowInstancerepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.completeInstance(99L, null));
            assertThat(ex.getMessage()).contains("WorkflowInstance not found");
        }
    }

    // ─── failInstance ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("failInstance()")
    class FailInstance {

        @Test
        @DisplayName("happy path — sets FAILED, errorMessage, stackTrace, finishedAt; does NOT save")
        void failInstance_setsFailedWithoutSave() {
            // Arrange
            WorkflowInstance wi = new WorkflowInstance();
            wi.setStartedAt(LocalDateTime.now().minusSeconds(2));
            when(workflowInstancerepository.findById(1L)).thenReturn(Optional.of(wi));

            // Act
            WorkflowInstance result = service.failInstance(1L, "NPE thrown", "java.lang.NullPointerException...");

            // Assert
            assertThat(result.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
            assertThat(result.getErrorMessage()).isEqualTo("NPE thrown");
            assertThat(result.getErrorStackTrace()).isEqualTo("java.lang.NullPointerException...");
            assertThat(result.getFinishedAt()).isNotNull();
            assertThat(result.getDurationMs()).isNotNull().isPositive();
            verify(workflowInstancerepository, never()).save(any());
        }

        @Test
        @DisplayName("happy path — null errorMessage and stackTrace are stored as null")
        void failInstance_nullErrorAndTrace_storedAsNull() {
            // Arrange
            WorkflowInstance wi = new WorkflowInstance();
            when(workflowInstancerepository.findById(1L)).thenReturn(Optional.of(wi));

            // Act
            WorkflowInstance result = service.failInstance(1L, null, null);

            // Assert
            assertThat(result.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
            assertThat(result.getErrorMessage()).isNull();
            assertThat(result.getErrorStackTrace()).isNull();
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void failInstance_notFound_throws() {
            // Arrange
            when(workflowInstancerepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.failInstance(99L, "error", "trace"));
            assertThat(ex.getMessage()).contains("WorkflowInstance not found");
        }
    }
}
