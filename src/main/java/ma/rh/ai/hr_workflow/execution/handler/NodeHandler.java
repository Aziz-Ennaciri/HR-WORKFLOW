package ma.rh.ai.hr_workflow.execution.handler;

import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

public interface NodeHandler {
    NodeType getType();
    String execute(Node node, NodeInstance nodeInstance) throws Exception;
}
