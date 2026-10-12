package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.DTOs.ApproveNodeDTO;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.service.INodeInstanceService;

@Service
@RequiredArgsConstructor
public class NodeInstanceServiceImpl implements INodeInstanceService{
    private final NodeInstanceRepository nodeInstanceRepository;

    @Override
    public NodeInstance startNode(NodeInstance nodeInstance) {
        nodeInstance.start();
        NodeInstance savedNodeInstance = nodeInstanceRepository.save(nodeInstance);
        return savedNodeInstance;
    }

    @Override
    @Transactional
    public NodeInstance approveNode(Long nodeInstanceId, ApproveNodeDTO dto) {
        NodeInstance nodeInstance = findAwaitingDecision(nodeInstanceId);
        nodeInstance.markCompleted(null);
        nodeInstance.setActor(dto.getActor());
        nodeInstance.setComment(dto.getComment());
        return nodeInstanceRepository.save(nodeInstance);
    }

    @Override
    @Transactional
    public NodeInstance rejectNode(Long nodeInstanceId, ApproveNodeDTO dto) {
        NodeInstance nodeInstance = findAwaitingDecision(nodeInstanceId);
        nodeInstance.markRejected(dto.getActor(), dto.getComment());
        return nodeInstanceRepository.save(nodeInstance);
    }

    @Override
    @Transactional
    public NodeInstance failNode(Long nodeInstanceId, String errorMessage) {
        NodeInstance nodeInstance = nodeInstanceRepository.findById(nodeInstanceId).orElseThrow(()->new ResourceNotFoundException("Node Instance not found"));
        if (nodeInstance.getStatus() != NodeInstanceStatus.PENDING && nodeInstance.getStatus() != NodeInstanceStatus.IN_PROGRESS) {
            throw new IllegalStateException("can only fail pending and in progress nodes");
        }
        nodeInstance.markFailed(errorMessage);
        return nodeInstanceRepository.save(nodeInstance);
    }

    /** Only a node paused at an APPROVAL step can be approved or rejected. */
    private NodeInstance findAwaitingDecision(Long nodeInstanceId) {
        NodeInstance nodeInstance = nodeInstanceRepository.findById(nodeInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Node Instance not found"));
        if (nodeInstance.getStatus() != NodeInstanceStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("Only nodes waiting for approval can be approved or rejected");
        }
        return nodeInstance;
    }
}
