package ma.rh.ai.hr_workflow.execution.handler;

import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.workflow.model.Node;

/** Builds the context the engine would pass a handler, from test entities. */
final class HandlerTestSupport {

    private HandlerTestSupport() {
    }

    static NodeExecutionContext context(Node node, NodeInstance step) {
        WorkflowInstance execution = step.getWorkflowInstance();
        return new NodeExecutionContext(
                1L,
                1L,
                node.getType(),
                node.getConfigJson(),
                step.getInputData(),
                execution == null ? null : execution.getInputData(),
                execution == null || execution.getWorkflow() == null ? null : execution.getWorkflow().getName());
    }
}
