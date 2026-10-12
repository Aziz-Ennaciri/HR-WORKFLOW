package ma.rh.ai.hr_workflow.workflow.service.Impl;

import java.util.HashMap;
import java.util.Map;

import ma.rh.ai.hr_workflow.workflow.graph.WorkflowGraph;
import ma.rh.ai.hr_workflow.workflow.validation.NodeConfigValidator;

import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.PatchWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.mappers.WorkflowMapper;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;
import ma.rh.ai.hr_workflow.workflow.service.IWorkflowService;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowServiceImpl implements IWorkflowService {

    private final WorkflowRepository workflowRepository;
    private final NodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final WorkflowMapper workflowMapper;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final NodeConfigValidator nodeConfigValidator;

    @Transactional
    @Override
    public WorkflowResponseDTO createWorkflow(CreateWorkflowDTO dto, String creatorEmail) {
        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return create(dto, creator);
    }

    private WorkflowResponseDTO create(CreateWorkflowDTO dto, User creator) {
        Workflow workflow = workflowMapper.toEntity(dto, creator);
        Workflow savedWorkflow = workflowRepository.save(workflow);
        return workflowMapper.toResponseDTO(savedWorkflow);
    }

    @Override
    public WorkflowResponseDTO getWorkflowById(Long id) {
        Workflow workflow = findWorkflow(id);
        return workflowMapper.toResponseDTO(workflow);
    }

    @Override
    public List<WorkflowResponseDTO> getAllWorkflows() {
        List<Workflow> workflows = workflowRepository.findByDeletedFalseOrderByCreatedAtDesc();
        return workflows.stream()
                .map(workflowMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowResponseDTO> getWorkflowsByCreatorEmail(String email) {
        List<Workflow> workflows = workflowRepository.findByCreatedByEmailIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(email);
        return workflows.stream()
                .map(workflowMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public WorkflowResponseDTO updateWorkflow(Long id, UpdateWorkflowDTO dto) {
        Workflow workflow = findWorkflow(id);

        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());

        if (dto.getVersion() != null) {
            workflow.setVersion(dto.getVersion());
        }

        if (dto.getEdgesJson() != null) {
            workflow.setEdgesJson(dto.getEdgesJson());
        }

        if (dto.getLayoutJson() != null) {
            workflow.setLayoutJson(dto.getLayoutJson());
        }

        if (dto.getStatus() != null) {
            if (dto.getStatus() == WorkflowStatus.ACTIVE && workflow.getStatus() != WorkflowStatus.ACTIVE) {
                validateRunnable(workflow);
            }
            workflow.setStatus(dto.getStatus());
        }

        Workflow updated = workflowRepository.save(workflow);
        return workflowMapper.toResponseDTO(updated);
    }

    @Transactional
    @Override
    public WorkflowResponseDTO patchWorkflow(Long id, PatchWorkflowDTO dto) {
        Workflow workflow = findWorkflow(id);
        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT workflows can be renamed or described");
        }
        workflow.setName(dto.getName());
        if (dto.getDescription() != null) {
            workflow.setDescription(dto.getDescription());
        }
        return workflowMapper.toResponseDTO(workflowRepository.save(workflow));
    }

    @Transactional
    @Override
    public WorkflowResponseDTO activateWorkflow(Long id) {
        Workflow workflow = findWorkflow(id);
        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT workflows can be activated");
        }
        validateRunnable(workflow);
        workflow.setStatus(WorkflowStatus.ACTIVE);
        return workflowMapper.toResponseDTO(workflow);
    }

    @Transactional
    @Override
    public void deleteWorkflow(Long id) {
        Workflow workflow = findWorkflow(id);

        workflowInstanceRepository
                .findByWorkflowIdAndStatusIn(id, List.of(
                        WorkflowInstanceStatus.RUNNING, WorkflowInstanceStatus.PENDING, WorkflowInstanceStatus.PAUSED))
                .forEach(instance -> {
                    instance.setStatus(WorkflowInstanceStatus.CANCELLED);
                    workflowInstanceRepository.save(instance);
                });

        workflow.setDeleted(true);
        workflowRepository.save(workflow);
    }

    @Transactional
    @Override
    public WorkflowResponseDTO duplicateWorkflow(Long workflowId, String currentUserEmail) {
        Workflow original = findWorkflow(workflowId);

        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Workflow copy = new Workflow();
        copy.setName("Copy of " + original.getName());
        copy.setDescription(original.getDescription());
        copy.setVersion(1);
        copy.setStatus(WorkflowStatus.DRAFT);
        copy.setCreatedBy(user);

        Workflow savedCopy = workflowRepository.save(copy);

        // Edges and layout reference node ids, so they are rewritten to point at the copied nodes.
        Map<String, String> idMap = new HashMap<>();
        List<Node> originalNodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        for (Node originalNode : originalNodes) {
            Node newNode = new Node();
            newNode.setWorkflow(savedCopy);
            newNode.setType(originalNode.getType());
            newNode.setOrderIndex(originalNode.getOrderIndex());
            newNode.setConfigJson(originalNode.getConfigJson());
            Node savedNode = nodeRepository.save(newNode);
            if (originalNode.getId() != null && savedNode != null && savedNode.getId() != null) {
                idMap.put(originalNode.getId().toString(), savedNode.getId().toString());
            }
        }
        copy.setEdgesJson(WorkflowGraph.remapIds(original.getEdgesJson(), idMap));
        copy.setLayoutJson(WorkflowGraph.remapIds(original.getLayoutJson(), idMap));

        return workflowMapper.toResponseDTO(savedCopy);
    }

    @Transactional
    @Override
    public WorkflowResponseDTO archiveWorkflow(Long id) {
        Workflow workflow = findWorkflow(id);

        if (workflow.getStatus() == WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("the workflow still activated, you should deactivate if you wanna archive it");
        }

        workflow.setStatus(WorkflowStatus.ARCHIVED);
        workflow.setUpdatedAt(LocalDateTime.now());
        return workflowMapper.toResponseDTO(workflow);
    }

    /** A workflow can only be activated if it has nodes, its connections form a single chain, and every node is configured. */
    private void validateRunnable(Workflow workflow) {
        List<Node> nodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflow.getId());
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Workflow must have at least one node");
        }
        List<WorkflowGraph.Edge> edges = WorkflowGraph.parseEdges(workflow.getEdgesJson());
        if (!edges.isEmpty()) {
            WorkflowGraph.executionOrder(nodes.stream().map(node -> node.getId().toString()).toList(), edges);
        }
        List<String> configErrors = nodes.stream().flatMap(node -> nodeConfigValidator.validate(node).stream()).toList();
        if (!configErrors.isEmpty()) {
            throw new IllegalArgumentException("The workflow cannot be activated: " + String.join("; ", configErrors));
        }
    }

    /** Deleted workflows are soft-deleted: they stay in the database for execution history but are gone for the API. */
    private Workflow findWorkflow(Long id) {
        return workflowRepository.findById(id)
                .filter(workflow -> !workflow.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
    }
}
