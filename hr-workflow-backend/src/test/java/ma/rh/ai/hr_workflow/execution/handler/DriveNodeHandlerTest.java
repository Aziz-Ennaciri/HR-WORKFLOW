package ma.rh.ai.hr_workflow.execution.handler;

import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.drive.service.DriveService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DriveNodeHandler")
class DriveNodeHandlerTest {

    @Mock
    private DriveService driveService;

    private DriveNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DriveNodeHandler(driveService);
    }

    @Test
    @DisplayName("getType() returns NodeType.DRIVE")
    void getType_returnsDrive() {
        assertThat(handler.getType()).isEqualTo(NodeType.DRIVE);
    }

    @Test
    @DisplayName("execute() delegates to driveService.saveFile with configJson and inputData")
    void execute_delegatesCorrectArguments() throws Exception {
        Node node = new Node();
        node.setConfigJson("{\"action\":\"write\",\"folderId\":\"cv_uploads\"}");

        NodeInstance ni = new NodeInstance();
        ni.setInputData("CV content here");

        when(driveService.saveFile(any(), any())).thenReturn("{\"fileId\":\"abc\"}");

        String result = handler.execute(node, ni);

        assertThat(result).isEqualTo("{\"fileId\":\"abc\"}");
        verify(driveService).saveFile(
                "{\"action\":\"write\",\"folderId\":\"cv_uploads\"}",
                "CV content here");
    }
}
