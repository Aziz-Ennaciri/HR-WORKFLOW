package ma.rh.ai.hr_workflow.execution.service.Impl;

import org.springframework.stereotype.Service;

import ma.rh.ai.hr_workflow.execution.DTOs.ApproveNodeDTO;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.service.INodeInstanceService;

@Service
public class NodeInstanceServiceImpl implements INodeInstanceService{

    @Override
    public NodeInstance startNode(NodeInstance nodeInstance) {
        throw new UnsupportedOperationException("Unimplemented method 'startNode'");
    }

    @Override
    public NodeInstance approveNode(Long nodeInstanceId, ApproveNodeDTO dto) {
        throw new UnsupportedOperationException("Unimplemented method 'approveNode'");
    }

    @Override
    public NodeInstance rejectNode(Long nodeInstanceId, ApproveNodeDTO dto) {
        throw new UnsupportedOperationException("Unimplemented method 'rejectNode'");
    }

    @Override
    public NodeInstance failNode(Long nodeInstanceId, String errorMessage) {
        throw new UnsupportedOperationException("Unimplemented method 'failNode'");
    }

    @Override
    public NodeInstance retryNode(Long nodeInstanceId) {
        throw new UnsupportedOperationException("Unimplemented method 'retryNode'");
    }
    
}
