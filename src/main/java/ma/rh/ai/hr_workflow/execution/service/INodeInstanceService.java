package ma.rh.ai.hr_workflow.execution.service;

import ma.rh.ai.hr_workflow.execution.DTOs.ApproveNodeDTO;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;

public interface INodeInstanceService {
    NodeInstance startNode(NodeInstance nodeInstance);

    NodeInstance approveNode(Long nodeInstanceId, ApproveNodeDTO dto);

    NodeInstance rejectNode(Long nodeInstanceId, ApproveNodeDTO dto);

    NodeInstance failNode(Long nodeInstanceId, String errorMessage);

    NodeInstance retryNode(Long nodeInstanceId);
}
