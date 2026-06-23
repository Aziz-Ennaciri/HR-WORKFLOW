package ma.rh.ai.hr_workflow.workflow.controllers;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.service.IWorkflowService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowController")
class WorkflowControllerTest {

    @Mock
    IWorkflowService workflowService;

    @InjectMocks
    WorkflowController controller;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContextAfter() {
        SecurityContextHolder.clearContext();
    }


    @Test
    @DisplayName("createWorkflow delegates to service and returns 201 CREATED")
    void createWorkflow_returns201() {
        CreateWorkflowDTO dto = new CreateWorkflowDTO("WF", "desc");
        WorkflowResponseDTO response = new WorkflowResponseDTO();
        when(workflowService.createWorkflow(dto, 1L)).thenReturn(response);

        ResponseEntity<WorkflowResponseDTO> result = controller.createWorkflow(dto, 1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    // ── getWorkflowId ───────────────────────────────

    @Test
    @DisplayName("getWorkflowId delegates to service and returns 200 OK")
    void getWorkflowId_returns200() {
        WorkflowResponseDTO response = new WorkflowResponseDTO();
        when(workflowService.getWorkflowById(1L)).thenReturn(response);

        ResponseEntity<WorkflowResponseDTO> result = controller.getWorkflowId(1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }


    @Test
    @DisplayName("getWorkflows returns 401 when SecurityContext has no authentication")
    void getWorkflows_nullAuth_returns401() {
        ResponseEntity<List<WorkflowResponseDTO>> result = controller.getWorkflows();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getWorkflows returns all workflows for ADMIN user")
    void getWorkflows_adminUser_returnsAllWorkflows() {
        setAuth("admin@test.com", "ROLE_ADMIN");
        List<WorkflowResponseDTO> all = List.of(new WorkflowResponseDTO(), new WorkflowResponseDTO());
        when(workflowService.getAllWorkflows()).thenReturn(all);

        ResponseEntity<List<WorkflowResponseDTO>> result = controller.getWorkflows();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(all);
    }

    @Test
    @DisplayName("getWorkflows returns only own workflows for non-ADMIN user")
    void getWorkflows_nonAdminUser_returnsOwnWorkflows() {
        setAuth("user@test.com", "ROLE_RH");
        List<WorkflowResponseDTO> own = List.of(new WorkflowResponseDTO());
        when(workflowService.getWorkflowsByCreatorEmail("user@test.com")).thenReturn(own);

        ResponseEntity<List<WorkflowResponseDTO>> result = controller.getWorkflows();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(own);
    }


    @Test
    @DisplayName("updateWorkflow delegates to service and returns 200 OK")
    void updateWorkflow_returns200() {
        UpdateWorkflowDTO dto = new UpdateWorkflowDTO("New Name", "desc", null, null, null, null);
        WorkflowResponseDTO response = new WorkflowResponseDTO();
        when(workflowService.updateWorkflow(1L, dto)).thenReturn(response);

        ResponseEntity<WorkflowResponseDTO> result = controller.updateWorkflow(1L, dto);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }


    @Test
    @DisplayName("deleteWorkflow delegates to service and returns 204 NO CONTENT")
    void deleteWorkflow_returns204() {
        ResponseEntity<Void> result = controller.deleteWorkflow(1L);

        verify(workflowService).deleteWorkflow(1L);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }


    @Test
    @DisplayName("duplicateWorkflow returns 401 when SecurityContext has no authentication")
    void duplicateWorkflow_nullAuth_returns401() {
        ResponseEntity<WorkflowResponseDTO> result = controller.duplicateWorkflow(1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("duplicateWorkflow delegates to service using authenticated user email and returns 201")
    void duplicateWorkflow_authenticated_returns201() {
        setAuth("user@test.com", "ROLE_RH");
        WorkflowResponseDTO copy = new WorkflowResponseDTO();
        when(workflowService.duplicateWorkflow(1L, "user@test.com")).thenReturn(copy);

        ResponseEntity<WorkflowResponseDTO> result = controller.duplicateWorkflow(1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(copy);
    }


    private void setAuth(String username, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, authorities));
    }
}
