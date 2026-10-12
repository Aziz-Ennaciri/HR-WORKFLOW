package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.common.PageResponse;
import ma.rh.ai.hr_workflow.config.SecurityUtils;
import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import ma.rh.ai.hr_workflow.execution.DTOs.ExecutionStatsDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.NodeInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.PendingApprovalDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceDetailDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.handler.ApprovalNodeHandler;
import ma.rh.ai.hr_workflow.execution.mappers.NodeInstanceMapper;
import ma.rh.ai.hr_workflow.execution.mappers.WorkflowInstanceMapper;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;
import ma.rh.ai.hr_workflow.execution.service.IExecutionQueryService;
import ma.rh.ai.hr_workflow.user.model.User;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionQueryServiceImpl implements IExecutionQueryService {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final NodeInstanceRepository nodeInstanceRepository;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final NodeInstanceMapper nodeInstanceMapper;
    private final ApprovalNodeHandler approvalNodeHandler;

    @Override
    public WorkflowInstanceResponseDTO getExecution(Long id) {
        return workflowInstanceMapper.toResponseDTO(findExecution(id));
    }

    @Override
    public WorkflowInstanceDetailDTO getExecutionDetail(Long id) {
        WorkflowInstanceDetailDTO detail = new WorkflowInstanceDetailDTO();
        detail.setWorkflowInstance(workflowInstanceMapper.toResponseDTO(findExecution(id)));
        detail.setNodeInstances(nodeInstanceMapper.toResponseDTO(
                nodeInstanceRepository.findWithDetailsByWorkflowInstanceIdOrderByExecutionOrderAsc(id)));
        return detail;
    }

    @Override
    public List<WorkflowInstanceResponseDTO> getExecutionsForWorkflow(Long workflowId) {
        return workflowInstanceMapper.toResponseDTO(workflowInstanceRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId));
    }

    @Override
    public PageResponse<WorkflowInstanceResponseDTO> searchExecutions(WorkflowInstanceStatus status, Long workflowId,
                                                                      Pageable pageable) {
        Specification<WorkflowInstance> spec = (root, query, cb) -> cb.conjunction();
        if (!SecurityUtils.isAdmin()) {
            String email = SecurityUtils.currentUserEmail().toLowerCase();
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("workflow").get("createdBy").get("email")), email));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (workflowId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("workflow").get("id"), workflowId));
        }
        return PageResponse.of(workflowInstanceRepository.findAll(spec, pageable), workflowInstanceMapper::toResponseDTO);
    }

    @Override
    public ExecutionStatsDTO getStats() {
        boolean admin = SecurityUtils.isAdmin();
        String email = admin ? null : SecurityUtils.currentUserEmail();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (WorkflowInstanceStatus status : WorkflowInstanceStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        (admin ? workflowInstanceRepository.countByStatus() : workflowInstanceRepository.countByStatusForOwner(email))
                .forEach(row -> byStatus.put(row.getStatus().name(), row.getTotal()));

        List<ExecutionStatsDTO.WorkflowExecutionCount> byWorkflow =
                (admin ? workflowInstanceRepository.countByWorkflow() : workflowInstanceRepository.countByWorkflowForOwner(email))
                        .stream()
                        .map(row -> new ExecutionStatsDTO.WorkflowExecutionCount(row.getWorkflowId(), row.getWorkflowName(), row.getTotal()))
                        .toList();

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new ExecutionStatsDTO(total, byStatus, byWorkflow);
    }

    @Override
    public NodeInstanceResponseDTO getNodeInstance(Long id) {
        return nodeInstanceMapper.toResponseDTO(nodeInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Node instance not found")));
    }

    @Override
    public List<NodeInstanceResponseDTO> getNodeInstances(Long executionId) {
        return nodeInstanceMapper.toResponseDTO(
                nodeInstanceRepository.findWithDetailsByWorkflowInstanceIdOrderByExecutionOrderAsc(executionId));
    }

    @Override
    public List<PendingApprovalDTO> getMyApprovals() {
        return nodeInstanceRepository
                .findByAssignedToEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        SecurityUtils.currentUserEmail(), NodeInstanceStatus.WAITING_APPROVAL)
                .stream()
                .map(this::toPendingApproval)
                .toList();
    }

    @Override
    public long countMyApprovals() {
        return nodeInstanceRepository.countByAssignedToEmailIgnoreCaseAndStatus(
                SecurityUtils.currentUserEmail(), NodeInstanceStatus.WAITING_APPROVAL);
    }

    @Override
    @Transactional
    public void deleteExecution(Long id) {
        workflowInstanceRepository.delete(findExecution(id));
    }

    private WorkflowInstance findExecution(Long id) {
        return workflowInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
    }

    private PendingApprovalDTO toPendingApproval(NodeInstance step) {
        WorkflowInstance execution = step.getWorkflowInstance();
        User triggeredBy = execution.getTriggeredBy();

        PendingApprovalDTO dto = new PendingApprovalDTO();
        dto.setNodeInstanceId(step.getId());
        dto.setStatus(step.getStatus().name());
        dto.setInputData(step.getInputData());
        dto.setCreatedAt(step.getCreatedAt());
        dto.setInstructions(approvalNodeHandler.instructions(step.getConfigSnapshot()).orElse(null));
        dto.setWorkflowInstanceId(execution.getId());
        dto.setWorkflowInstanceStatus(execution.getStatus().name());
        dto.setWorkflowId(execution.getWorkflow().getId());
        dto.setWorkflowName(execution.getWorkflow().getName());
        dto.setTriggeredById(triggeredBy.getId());
        dto.setTriggeredByEmail(triggeredBy.getEmail());
        dto.setTriggeredByName(triggeredBy.getFirstName() + " " + triggeredBy.getLastName());
        return dto;
    }
}
