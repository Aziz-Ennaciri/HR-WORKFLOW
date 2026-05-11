package ma.rh.ai.hr_workflow.workflow.controllers;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeOrderDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.service.INodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NodeController")
class NodeControllerTest {

    @Mock
    INodeService nodeService;

    @InjectMocks
    NodeController controller;

    @Test
    @DisplayName("createNode delegates to service and returns 201 CREATED")
    void createNode_returns201() {
        CreateNodeDTO dto = new CreateNodeDTO("EMAIL", 1, "{}");
        NodeResponseDTO response = new NodeResponseDTO();
        when(nodeService.createNode(dto, 10L)).thenReturn(response);

        ResponseEntity<NodeResponseDTO> result = controller.createNode(10L, dto);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    @DisplayName("createNodes delegates to service and returns 201 CREATED")
    void createNodes_returns201() {
        List<CreateNodeDTO> dtos = List.of(new CreateNodeDTO("GPT", 1, "{}"));
        List<NodeResponseDTO> responses = List.of(new NodeResponseDTO());
        when(nodeService.createNodes(dtos, 10L)).thenReturn(responses);

        ResponseEntity<List<NodeResponseDTO>> result = controller.createNodes(dtos, 10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(responses);
    }

    @Test
    @DisplayName("getNodeId delegates to service and returns 200 OK")
    void getNodeId_returns200() {
        NodeResponseDTO response = new NodeResponseDTO();
        when(nodeService.getNodeById(5L)).thenReturn(response);

        ResponseEntity<NodeResponseDTO> result = controller.getNodeId(5L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    @DisplayName("getNodesByWorkflowId delegates to service and returns 200 OK")
    void getNodesByWorkflowId_returns200() {
        List<NodeResponseDTO> responses = List.of(new NodeResponseDTO());
        when(nodeService.getNodesByWorkflowId(10L)).thenReturn(responses);

        ResponseEntity<List<NodeResponseDTO>> result = controller.getNodesByWorkflowId(10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(responses);
    }

    @Test
    @DisplayName("getNodesByWorkflowIdAndType delegates to service and returns 200 OK")
    void getNodesByWorkflowIdAndType_returns200() {
        List<NodeResponseDTO> responses = List.of(new NodeResponseDTO());
        when(nodeService.getNodesByWorkflowIdAndType(10L, "EMAIL")).thenReturn(responses);

        ResponseEntity<List<NodeResponseDTO>> result = controller.getNodesByWorkflowIdAndType(10L, "EMAIL");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(responses);
    }

    @Test
    @DisplayName("updateNode delegates to service and returns 200 OK")
    void updateNode_returns200() {
        UpdateNodeDTO dto = new UpdateNodeDTO("DRIVE", 2, "{}");
        NodeResponseDTO response = new NodeResponseDTO();
        when(nodeService.updateNode(5L, dto)).thenReturn(response);

        ResponseEntity<NodeResponseDTO> result = controller.updateNode(5L, dto);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    @DisplayName("reorderNodes delegates to service and returns 200 OK")
    void reorderNodes_returns200() {
        List<NodeOrderDTO> orders = List.of(new NodeOrderDTO(1L, 0));
        List<NodeResponseDTO> responses = List.of(new NodeResponseDTO());
        when(nodeService.reorderNodes(10L, orders)).thenReturn(responses);

        ResponseEntity<List<NodeResponseDTO>> result = controller.reorderNodes(10L, orders);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(responses);
    }

    @Test
    @DisplayName("deleteNode delegates to service and returns 204 NO CONTENT")
    void deleteNode_returns204() {
        ResponseEntity<Void> result = controller.deleteNode(5L);

        verify(nodeService).deleteNode(5L);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("deleteNodesByWorkflowId delegates to service and returns 204 NO CONTENT")
    void deleteNodesByWorkflowId_returns204() {
        ResponseEntity<Void> result = controller.deleteNodesByWorkflowId(10L);

        verify(nodeService).deleteNodesByWorkflowId(10L);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
