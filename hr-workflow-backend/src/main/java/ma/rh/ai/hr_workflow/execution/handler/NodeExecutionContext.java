package ma.rh.ai.hr_workflow.execution.handler;

import ma.rh.ai.hr_workflow.workflow.model.NodeType;

/**
 * Everything a {@link NodeHandler} needs to run one step, captured inside a short transaction.
 * Handlers run outside any transaction (they call GPT, SMTP, Drive...), so they get plain values
 * rather than lazily-loaded entities.
 *
 * @param configJson        the node config frozen when the execution was triggered
 * @param inputData         output of the previous step, or the execution input for the first step
 * @param workflowInputData the input the execution was triggered with
 */
public record NodeExecutionContext(
        Long executionId,
        Long nodeInstanceId,
        NodeType nodeType,
        String configJson,
        String inputData,
        String workflowInputData,
        String workflowName) {
}
