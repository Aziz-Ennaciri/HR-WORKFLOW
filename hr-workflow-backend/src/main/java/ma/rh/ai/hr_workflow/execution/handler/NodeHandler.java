package ma.rh.ai.hr_workflow.execution.handler;

import ma.rh.ai.hr_workflow.workflow.model.NodeType;

public interface NodeHandler {
    NodeType getType();

    /** Runs one step and returns its output, or {@link ApprovalNodeHandler#APPROVAL_SIGNAL} to pause. */
    String execute(NodeExecutionContext context) throws Exception;
}
