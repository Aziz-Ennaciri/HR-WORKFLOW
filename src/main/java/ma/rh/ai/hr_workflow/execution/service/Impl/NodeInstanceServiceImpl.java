package ma.rh.ai.hr_workflow.execution.service.Impl;

import org.springframework.stereotype.Service;

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
    public NodeInstance approveNode(Long nodeInstanceId, ApproveNodeDTO dto) {
        NodeInstance nodeInstance = nodeInstanceRepository.findById(nodeInstanceId).orElseThrow(()->new RuntimeException("Node Instance not found"));
        if (nodeInstance.getStatus() != NodeInstanceStatus.PENDING && nodeInstance.getStatus() != NodeInstanceStatus.IN_PROGRESS) {
            throw new RuntimeException("can only approve pending and in progress nodes");
        }
        nodeInstance.markCompleted(null);
        nodeInstance.setActor(dto.getActor());
        nodeInstance.setComment(dto.getComment());
        return nodeInstance;
    }

    @Override
    public NodeInstance rejectNode(Long nodeInstanceId, ApproveNodeDTO dto) {
        NodeInstance nodeInstance = nodeInstanceRepository.findById(nodeInstanceId).orElseThrow(()->new RuntimeException("Node Instance not found"));
        if (nodeInstance.getStatus() != NodeInstanceStatus.PENDING && nodeInstance.getStatus() != NodeInstanceStatus.IN_PROGRESS) {
            throw new RuntimeException("can only approve pending and in progress nodes");
        }
        nodeInstance.markRejected(dto.getActor(),dto.getComment());
        return nodeInstance;
    }

    @Override
    public NodeInstance failNode(Long nodeInstanceId, String errorMessage) {
        NodeInstance nodeInstance = nodeInstanceRepository.findById(nodeInstanceId).orElseThrow(()->new RuntimeException("Node Instance not found"));
        if (nodeInstance.getStatus() != NodeInstanceStatus.PENDING && nodeInstance.getStatus() != NodeInstanceStatus.IN_PROGRESS) {
            throw new RuntimeException("can only approve pending and in progress nodes");
        }
        nodeInstance.markFailed(errorMessage);
        return nodeInstance;
    }

    @Override
    public NodeInstance retryNode(Long nodeInstanceId) {
        NodeInstance nodeInstance = nodeInstanceRepository.findById(nodeInstanceId).orElseThrow(()->new RuntimeException("Node Instance not found"));
        if (nodeInstance.getStatus() != NodeInstanceStatus.FAILED) {
            throw new RuntimeException("can only retry failed nodes");
        }
        NodeInstance retryNodeInstance = new NodeInstance();
        retryNodeInstance.setWorkflowInstance(nodeInstance.getWorkflowInstance());
        retryNodeInstance.setNode(nodeInstance.getNode());
        retryNodeInstance.setExecutionOrder(nodeInstance.getExecutionOrder());
        retryNodeInstance.setStatus(NodeInstanceStatus.RETRYING);
        retryNodeInstance.setInputData(nodeInstance.getInputData());
        retryNodeInstance.setRetryCount(nodeInstance.getRetryCount() + 1);

        NodeInstance savedNodeInstance = nodeInstanceRepository.save(retryNodeInstance);
        return savedNodeInstance;
    }
    
}
