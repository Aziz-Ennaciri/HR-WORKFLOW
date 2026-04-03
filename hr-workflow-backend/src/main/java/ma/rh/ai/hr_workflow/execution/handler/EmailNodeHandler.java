package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.email.service.EmailService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

@Component
@RequiredArgsConstructor
public class EmailNodeHandler implements NodeHandler{
    private final EmailService emailService;

    @Override
    public NodeType getType() {
        return NodeType.EMAIL;
    }

    @Override
    public String execute(Node node, NodeInstance nodeInstance) throws Exception {
        
        String configJson = node.getConfigJson();
        String inputData = nodeInstance.getInputData();
        
        // Get workflow information
        String workflowName = nodeInstance.getWorkflowInstance().getWorkflow().getName();
        String workflowKey = nodeInstance.getWorkflowInstance().getWorkflow().getWorkflowKey();
        
        String result = emailService.sendEmail(configJson, inputData, workflowName, workflowKey);
        
        return result;
    }
}
