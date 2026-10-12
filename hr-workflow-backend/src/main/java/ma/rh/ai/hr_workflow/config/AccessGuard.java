package ma.rh.ai.hr_workflow.config;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ownership rules used from {@code @PreAuthorize("@access.canAccess...(#id)")}.
 *
 * <ul>
 *   <li>Admins can access everything.</li>
 *   <li>A workflow (and its nodes) belongs to its creator.</li>
 *   <li>An execution is visible to the workflow owner, the user who triggered it,
 *       and any user assigned to approve one of its steps.</li>
 * </ul>
 *
 * When the target does not exist the check passes, so the endpoint itself answers 404.
 */
@Component("access")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessGuard {

    private final WorkflowRepository workflowRepository;
    private final NodeRepository nodeRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final NodeInstanceRepository nodeInstanceRepository;
    private final UserRepository userRepository;

    public boolean canAccessWorkflow(Long workflowId) {
        if (SecurityUtils.isAdmin()) return true;
        return workflowRepository.findById(workflowId)
                .map(workflow -> isCurrentUser(workflow.getCreatedBy()))
                .orElse(true);
    }

    public boolean canAccessNode(Long nodeId) {
        if (SecurityUtils.isAdmin()) return true;
        return nodeRepository.findById(nodeId)
                .map(node -> canAccessWorkflow(node.getWorkflow().getId()))
                .orElse(true);
    }

    public boolean canAccessExecution(Long workflowInstanceId) {
        if (SecurityUtils.isAdmin()) return true;
        return workflowInstanceRepository.findById(workflowInstanceId)
                .map(this::canAccessExecution)
                .orElse(true);
    }

    public boolean canAccessNodeInstance(Long nodeInstanceId) {
        if (SecurityUtils.isAdmin()) return true;
        return nodeInstanceRepository.findById(nodeInstanceId)
                .map(nodeInstance -> canAccessExecution(nodeInstance.getWorkflowInstance()))
                .orElse(true);
    }

    public boolean isCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .map(this::isCurrentUser)
                .orElse(false);
    }

    /**
     * Who may approve/reject a step: the assigned approver; if nobody is assigned,
     * an admin or the owner of the workflow.
     */
    public boolean canDecide(Long nodeInstanceId) {
        return nodeInstanceRepository.findById(nodeInstanceId).map(this::canDecide).orElse(true);
    }

    private boolean canDecide(NodeInstance nodeInstance) {
        if (nodeInstance.getAssignedTo() != null) {
            return isCurrentUser(nodeInstance.getAssignedTo());
        }
        return SecurityUtils.isAdmin()
                || isCurrentUser(nodeInstance.getWorkflowInstance().getWorkflow().getCreatedBy());
    }

    private boolean canAccessExecution(WorkflowInstance instance) {
        return isCurrentUser(instance.getWorkflow().getCreatedBy())
                || isCurrentUser(instance.getTriggeredBy())
                || nodeInstanceRepository.existsByWorkflowInstanceIdAndAssignedToEmailIgnoreCase(
                        instance.getId(), SecurityUtils.currentUserEmail());
    }

    private boolean isCurrentUser(User user) {
        return user != null && user.getEmail().equalsIgnoreCase(SecurityUtils.currentUserEmail());
    }
}
