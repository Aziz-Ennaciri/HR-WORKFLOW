package ma.rh.ai.hr_workflow.execution.controllers;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceDetailDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.mappers.NodeInstanceMapper;
import ma.rh.ai.hr_workflow.execution.mappers.WorkflowInstanceMapper;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowExecutionService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowExecutionController")
class WorkflowExecutionControllerTest {

    @Mock IWorkflowExecutionService executionService;
    @Mock WorkflowInstancerepository instanceRepository;
    @Mock UserRepository userRepository;
    @Mock WorkflowInstanceMapper instanceMapper;
    @Mock NodeInstanceMapper nodeMapper;

    @InjectMocks
    WorkflowExecutionController controller;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContextAfter() {
        SecurityContextHolder.clearContext();
    }

    // ── trigger ─────────────────────────────────────

    @Test
    @DisplayName("trigger returns 201 CREATED with mapped response when user is found")
    void trigger_userFound_returns201() {
        TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(1L, "{}");
        User user = new User();
        WorkflowInstance instance = new WorkflowInstance();
        WorkflowInstanceResponseDTO responseDTO = new WorkflowInstanceResponseDTO();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(executionService.triggerWorkflow(dto, user)).thenReturn(instance);
        when(instanceMapper.toResponseDTO(instance)).thenReturn(responseDTO);

        ResponseEntity<WorkflowInstanceResponseDTO> result = controller.trigger(dto, 1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(responseDTO);
    }

    @Test
    @DisplayName("trigger throws RuntimeException when user is not found")
    void trigger_userNotFound_throwsRuntimeException() {
        TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(1L, "{}");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.trigger(dto, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }

    // ── getInstance ─────────────────────────────────

    @Test
    @DisplayName("getInstance returns 200 OK with mapped response when instance is found")
    void getInstance_found_returns200() {
        WorkflowInstance instance = new WorkflowInstance();
        WorkflowInstanceResponseDTO responseDTO = new WorkflowInstanceResponseDTO();

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        when(instanceMapper.toResponseDTO(instance)).thenReturn(responseDTO);

        ResponseEntity<WorkflowInstanceResponseDTO> result = controller.getInstance(1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(responseDTO);
    }

    @Test
    @DisplayName("getInstance throws RuntimeException when instance is not found")
    void getInstance_notFound_throwsRuntimeException() {
        when(instanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getInstance(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Not found");
    }

    // ── getDetail ───────────────────────────────────

    @Test
    @DisplayName("getDetail returns 200 OK with detail when instance is found")
    void getDetail_found_returnsOk() {
        WorkflowInstance instance = new WorkflowInstance();
        WorkflowInstanceResponseDTO responseDTO = new WorkflowInstanceResponseDTO();

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        when(instanceMapper.toResponseDTO(instance)).thenReturn(responseDTO);
        when(nodeMapper.toResponseDTO(anyList())).thenReturn(List.of());

        ResponseEntity<?> result = controller.getDetail(1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOf(WorkflowInstanceDetailDTO.class);
    }

    @Test
    @DisplayName("getDetail returns 404 NOT FOUND when instance does not exist")
    void getDetail_notFound_returns404() {
        when(instanceRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<?> result = controller.getDetail(99L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── getByWorkflow ────────────────────────────────

    @Test
    @DisplayName("getByWorkflow returns 200 OK with mapped list")
    void getByWorkflow_returns200() {
        List<WorkflowInstance> instances = List.of(new WorkflowInstance());
        List<WorkflowInstanceResponseDTO> dtos = List.of(new WorkflowInstanceResponseDTO());

        when(instanceRepository.findByWorkflowIdOrderByCreatedAtDesc(10L)).thenReturn(instances);
        when(instanceMapper.toResponseDTO(instances)).thenReturn(dtos);

        ResponseEntity<List<WorkflowInstanceResponseDTO>> result = controller.getByWorkflow(10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dtos);
    }

    // ── getAllExecutions (auth branches) ─────────────

    @Test
    @DisplayName("getAllExecutions returns 401 when SecurityContext has no authentication")
    void getAllExecutions_nullAuth_returns401() {
        ResponseEntity<List<WorkflowInstanceResponseDTO>> result = controller.getAllExecutions();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getAllExecutions returns all executions for ADMIN user")
    void getAllExecutions_adminUser_returnsAll() {
        setAuth("admin@test.com", "ROLE_ADMIN");
        List<WorkflowInstance> all = List.of(new WorkflowInstance());
        List<WorkflowInstanceResponseDTO> dtos = List.of(new WorkflowInstanceResponseDTO());

        when(instanceRepository.findAll()).thenReturn(all);
        when(instanceMapper.toResponseDTO(all)).thenReturn(dtos);

        ResponseEntity<List<WorkflowInstanceResponseDTO>> result = controller.getAllExecutions();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dtos);
    }

    @Test
    @DisplayName("getAllExecutions returns only own executions for non-ADMIN user")
    void getAllExecutions_nonAdminUser_returnsOwnExecutions() {
        setAuth("user@test.com", "ROLE_RH");
        List<WorkflowInstance> own = List.of(new WorkflowInstance());
        List<WorkflowInstanceResponseDTO> dtos = List.of(new WorkflowInstanceResponseDTO());

        when(instanceRepository.findByWorkflow_CreatedBy_Email("user@test.com")).thenReturn(own);
        when(instanceMapper.toResponseDTO(own)).thenReturn(dtos);

        ResponseEntity<List<WorkflowInstanceResponseDTO>> result = controller.getAllExecutions();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dtos);
    }

    // ── continueExecution ───────────────────────────

    @Test
    @DisplayName("continueExecution delegates to service and returns 200 OK")
    void continueExecution_returns200() {
        ResponseEntity<Void> result = controller.continueExecution(1L);

        verify(executionService).continueExecution(1L);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── retryFromNode ────────────────────────────────

    @Test
    @DisplayName("retryFromNode delegates to service and returns 200 OK")
    void retryFromNode_returns200() {
        ResponseEntity<Void> result = controller.retryFromNode(1L, 5L);

        verify(executionService).retryFromNode(1L, 5L);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── deleteExecution ──────────────────────────────

    @Test
    @DisplayName("deleteExecution deletes found instance and returns 204 NO CONTENT")
    void deleteExecution_found_returns204() {
        WorkflowInstance instance = new WorkflowInstance();
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(instance));

        ResponseEntity<Void> result = controller.deleteExecution(1L);

        verify(instanceRepository).delete(instance);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("deleteExecution throws RuntimeException when instance is not found")
    void deleteExecution_notFound_throwsRuntimeException() {
        when(instanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.deleteExecution(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Execution not found");
    }

    // ── helpers ─────────────────────────────────────

    private void setAuth(String username, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, authorities));
    }
}
