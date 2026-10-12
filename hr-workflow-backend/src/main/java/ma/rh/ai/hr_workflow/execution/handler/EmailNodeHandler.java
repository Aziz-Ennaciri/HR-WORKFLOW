package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.integration.email.service.EmailService;
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
    public String execute(NodeExecutionContext context) throws Exception {
        String workflowName = context.workflowName();
        String workflowKey = workflowName.toLowerCase().replaceAll("\\s+", "-");
        return emailService.sendEmail(context.configJson(), context.inputData(), workflowName, workflowKey);
    }
}
