package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;
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
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    private WorkflowInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowInstanceServiceImpl(workflowInstanceRepository, workflowRepository);
    }


    private WorkflowInstance instanceWithStatus(WorkflowInstanceStatus status) {
        WorkflowInstance wi = new WorkflowInstance();
        wi.setStatus(status);
        return wi;
    }


    @Nested
    @DisplayName("createInstance()")
    class CreateInstance {

        @Test
        @DisplayName("happy path — builds WorkflowInstance with PENDING status and saves it")
        void createInstance_happyPath_savesWithPendingStatus() {
            Workflow wf = new Workflow();
            wf.setId(10L);
            User user = new User();
            user.setId(1L);
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(10L, "{\"key\":\"val\"}");

            when(workflowRepository.findById(10L)).thenReturn(Optional.of(wf));

            ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
            WorkflowInstance saved = new WorkflowInstance();
            saved.setId(100L);
            when(workflowInstanceRepository.save(captor.capture())).thenReturn(saved);

            WorkflowInstance result = service.createInstance(dto, user);

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
            Workflow wf = new Workflow();
            wf.setId(1L);
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(1L, null);
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(wf));
            when(workflowInstanceRepository.save(any())).thenReturn(new WorkflowInstance());

            service.createInstance(dto, new User());

            ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
            verify(workflowInstanceRepository).save(captor.capture());
            assertThat(captor.getValue().getInputData()).isNull();
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void createInstance_workflowNotFound_throws() {
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(99L, null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.createInstance(dto, new User()));
            assertThat(ex.getMessage()).contains("Workflow not found");
        }
    }
}
