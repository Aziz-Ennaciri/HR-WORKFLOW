package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

@Slf4j
@Component
public class ApprovalNodeHandler implements NodeHandler {

    public static final String APPROVAL_SIGNAL = "__WAITING_APPROVAL__";

    @Override
    public NodeType getType() {
        return NodeType.APPROVAL;
    }

    @Override
    public String execute(Node node, NodeInstance nodeInstance) throws Exception {
        log.info("⏸️  APPROVAL node reached — pausing workflow for human review");

        return APPROVAL_SIGNAL;
    }
}