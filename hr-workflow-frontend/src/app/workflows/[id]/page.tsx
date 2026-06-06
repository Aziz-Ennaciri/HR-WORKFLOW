"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useRouter } from "next/navigation";
import NodeConfigPanel from "@/components/workflow/NodeConfigPanel";
import { nodeTypes } from "@/components/workflow/nodes";
import { DeletableEdge } from "@/components/workflow/edges/DeletableEdge";

const edgeTypes = { deletable: DeletableEdge };
import { useAuthGuard, getUser } from "@/lib/auth";
import api from "@/lib/api";
import ReactFlow, {
  Node,
  Edge,
  Controls,
  Background,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
  NodeChange,
  EdgeChange,
  Connection,
  MarkerType,
} from "reactflow";
import Sidebar from "@/components/layouts/sidebar";
import Button from "@/components/ui/Button";
import { getWorkflowsUrl } from "@/lib/workflows";

export default function WorkflowDesignerPage() {
  const params = useParams();
  const router = useRouter();
  const authReady = useAuthGuard();
  const [workflow, setWorkflow] = useState<any>(null);
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [executing, setExecuting] = useState(false);
  const [duplicating, setDuplicating] = useState(false);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [invalidNodeIds, setInvalidNodeIds] = useState<Set<string>>(new Set());
  const [showEditModal, setShowEditModal] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editSaving, setEditSaving] = useState(false);
  const newNodeOffset = useRef(0);
  const edgesRef = useRef(edges);
  useEffect(() => {
    edgesRef.current = edges;
  }, [edges]);
  const nodesRef = useRef(nodes);
  useEffect(() => {
    nodesRef.current = nodes;
  }, [nodes]);

  useEffect(() => {
    fetchWorkflow();
    fetchWorkflows();
  }, [params.id]);

  const onNodeClick = useCallback((_event: any, node: Node) => {
    setSelectedNode(node);
  }, []);

  const updateNodeConfig = (config: any) => {
    setNodes((nds) =>
      nds.map((node) =>
        node.id === selectedNode?.id
          ? { ...node, data: { ...node.data, config } }
          : node,
      ),
    );
  };

  const handleActivate = async () => {
    console.log("🔵 Activate clicked");
    console.log("📦 Workflow:", workflow);

    if (!workflow) {
      alert("Workflow not loaded");
      return;
    }

    try {
      const requestData = {
        name: workflow.name,
        description: workflow.description,
        version: workflow.version,
        status: "ACTIVE",
        createdById: workflow.createdById,
      };

      const response = await api.put(`/workflows/${params.id}`, requestData);

      alert("Workflow activated successfully!");
      await fetchWorkflow();
    } catch (error: any) {
      alert(error.response?.data?.message || error.message || "Failed");
    }
  };

  const handleDeactivate = async () => {
    try {
      await api.put(`/workflows/${params.id}`, {
        name: workflow.name,
        description: workflow.description,
        version: workflow.version,
        status: "DRAFT",
        createdById: workflow.createdById,
      });

      alert("Workflow deactivated!");
      await fetchWorkflow();
    } catch (error: any) {
      console.error("Failed to deactivate workflow:", error);
      alert(error.response?.data?.message || "Failed to deactivate workflow");
    }
  };

  const fetchWorkflow = async () => {
    try {
      const response = await api.get(`/workflows/${params.id}`);
      setWorkflow(response.data);

      const nodesResponse = await api.get(`/nodes/workflow/${params.id}`);

      if (nodesResponse.data && nodesResponse.data.length > 0) {
        const flowNodes = nodesResponse.data.map((node: any, index: number) => {
          let config = {};
          try {
            config = JSON.parse(node.configJson || "{}");
          } catch (e) {
            console.error("Failed to parse config for node", node.id, e);
          }

          return {
            id: node.id.toString(),
            type: node.type.toLowerCase(),
            position: {
              x: 100 + index * 250,
              y: 100 + Math.floor(index / 3) * 150,
            },
            data: {
              label: node.type,
              config: config,
              order: node.order,
            },
          };
        });

        setNodes(flowNodes);
      }

      if (response.data.edgesJson) {
        try {
          setEdges(JSON.parse(response.data.edgesJson));
        } catch (e) {
          console.error("Failed to parse edgesJson", e);
          setEdges([]);
        }
      } else {
        setEdges([]);
      }
    } catch (error) {
    } finally {
      setLoading(false);
    }
  };

  const fetchWorkflows = async () => {
    try {
      const response = await api.get(getWorkflowsUrl());
      setWorkflows(response.data);
    } catch (error) {
      console.error("Failed to fetch workflows:", error);
    }
  };

  const onNodesChange = useCallback(
    (changes: NodeChange[]) =>
      setNodes((nds) => applyNodeChanges(changes, nds)),
    [],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) =>
      setEdges((eds) => applyEdgeChanges(changes, eds)),
    [],
  );

  const onConnect = useCallback((params: Connection) => {
    setEdges((eds) => addEdge(params, eds));
  }, []);

  const addNode = (type: string) => {
    const idx = newNodeOffset.current;
    newNodeOffset.current += 1;
    const col = idx % 4;
    const row = Math.floor(idx / 4);
    const newNode: Node = {
      id: `${type}-${Date.now()}`,
      type: type.toLowerCase(),
      position: { x: 150 + col * 260, y: 120 + row * 180 },
      data: { label: type, config: {} },
    };
    setNodes((nds) => [...nds, newNode]);
  };

  const deriveOrderFromEdges = (
    allNodes: Node[],
    allEdges: Edge[],
  ): Map<string, number> => {
    const outEdge = new Map<string, string>();
    const incomingCount = new Map<string, number>();
    allNodes.forEach((n) => incomingCount.set(n.id, 0));
    allEdges.forEach((e) => {
      outEdge.set(e.source, e.target);
      incomingCount.set(e.target, (incomingCount.get(e.target) ?? 0) + 1);
    });

    const orderMap = new Map<string, number>();
    const visited = new Set<string>();
    let order = 1;
    let current: string | undefined = allNodes.find(
      (n) => (incomingCount.get(n.id) ?? 0) === 0,
    )?.id;
    while (current && !visited.has(current)) {
      orderMap.set(current, order++);
      visited.add(current);
      current = outEdge.get(current);
    }

    // Disconnected nodes fallback: order by canvas X position
    allNodes
      .filter((n) => !visited.has(n.id))
      .sort((a, b) => a.position.x - b.position.x)
      .forEach((n) => orderMap.set(n.id, order++));

    return orderMap;
  };

  useEffect(() => {
    if (validationErrors.length === 0) return;
    const timer = setTimeout(() => {
      setValidationErrors([]);
      setInvalidNodeIds(new Set());
    }, 5000);
    return () => clearTimeout(timer);
  }, [validationErrors]);

  const validateNodes = (): { errors: string[]; invalidIds: Set<string> } => {
    const errors: string[] = [];
    const invalidIds = new Set<string>();

    for (const node of nodes) {
      const type = (node.data.label as string).toUpperCase();
      const config = node.data.config || {};
      let isInvalid = false;

      if (type === "GPT") {
        if (!config.prompt?.trim()) {
          errors.push(`GPT Node '${node.data.label}' is missing a prompt.`);
          isInvalid = true;
        }
      } else if (type === "EMAIL") {
        if (!config.to?.trim()) {
          errors.push("Email Node is missing a recipient.");
          isInvalid = true;
        }
        if (!config.subject?.trim()) {
          errors.push("Email Node is missing a subject.");
          isInvalid = true;
        }
      } else if (type === "DRIVE") {
        if (!config.action?.trim()) {
          errors.push("Drive Node is missing an action.");
          isInvalid = true;
        }
        if (!config.folderId?.trim() && !config.fileId?.trim()) {
          errors.push("Drive Node is missing a folder ID.");
          isInvalid = true;
        }
      } else if (type === "EXCEL") {
        if (!config.fileName?.trim()) {
          errors.push("Excel Node is missing a file name.");
          isInvalid = true;
        }
      } else if (type === "APPROVAL") {
        if (!config.approverEmail?.trim()) {
          errors.push("Approval Node is missing an approver email.");
          isInvalid = true;
        }
      }

      if (isInvalid) invalidIds.add(node.id);
    }

    return { errors, invalidIds };
  };

  const handleDuplicate = async () => {
    if (duplicating) return;
    setDuplicating(true);
    try {
      const res = await api.post(`/workflows/${params.id}/duplicate`);
      const newId = res.data.id;
      router.push(`/workflows/${newId}`);
    } catch (error: any) {
      alert(
        error.response?.data?.message ||
          error.message ||
          "Failed to duplicate workflow",
      );
    } finally {
      setDuplicating(false);
    }
  };

  const handleExecute = async () => {
    if (executing) return;
    setExecuting(true);
    try {
      const user = getUser();
      await api.post(`/executions/trigger?userId=${user.id}`, {
        workflowId: parseInt(params.id as string),
        inputData: "{}",
      });
      router.push("/dashboard");
    } catch (error: any) {
      alert(error.response?.data?.message || error.message || "Failed to trigger execution");
      setExecuting(false);
    }
  };

  const openEditModal = () => {
    setEditName(workflow?.name || "");
    setEditDescription(workflow?.description || "");
    setShowEditModal(true);
  };

  const handleEditSave = async () => {
    if (!editName.trim()) return;
    setEditSaving(true);
    try {
      const res = await api.patch(`/workflows/${params.id}`, {
        name: editName.trim(),
        description: editDescription,
      });
      setWorkflow((w: any) => ({ ...w, name: res.data.name, description: res.data.description }));
      setShowEditModal(false);
    } catch (error: any) {
      alert(error.response?.data?.message || "Failed to update workflow");
    } finally {
      setEditSaving(false);
    }
  };

  const handleSave = async () => {
    await new Promise((resolve) => setTimeout(resolve, 50));

    const { errors, invalidIds } = validateNodes();
    if (errors.length > 0) {
      setValidationErrors(errors);
      setInvalidNodeIds(invalidIds);
      return;
    }
    setValidationErrors([]);
    setInvalidNodeIds(new Set());

    try {
      const wasActive = workflow?.status === "ACTIVE";

      // Step 1: Deactivate if currently active so we can edit
      if (wasActive) {
        await api.put(`/workflows/${params.id}`, {
          name: workflow.name,
          description: workflow.description,
          version: workflow.version,
          status: "DRAFT",
          createdById: workflow.createdById,
          edgesJson: JSON.stringify(edgesRef.current),
        });
        setWorkflow((w: any) => ({ ...w, status: "DRAFT" }));
      }

      // Step 2: Sync nodes and build old-id → new-backend-id map
      const existingNodesResponse = await api.get(`/nodes/workflow/${params.id}`);
      const existingNodes = existingNodesResponse.data;
      const existingNodeIds = new Set(existingNodes.map((n: any) => n.id.toString()));
      const currentNodeIds = new Set(nodesRef.current.map((n) => n.id));

      const nodesToDelete = existingNodes.filter(
        (n: any) => !currentNodeIds.has(n.id.toString()),
      );
      for (const node of nodesToDelete) {
        try {
          await api.delete(`/nodes/${node.id}`);
        } catch {
          console.warn("⚠️ Failed to delete node", node.id, "- might have executions");
        }
      }

      const orderMap = deriveOrderFromEdges(nodesRef.current, edgesRef.current);
      const idRemap = new Map<string, string>();

      for (const node of nodesRef.current) {
        const order = orderMap.get(node.id) ?? 1;
        const isExisting = existingNodeIds.has(node.id);

        if (isExisting) {
          await api.put(`/nodes/${node.id}`, {
            type: node.data.label,
            order,
            configJson: JSON.stringify(node.data.config || {}),
          });
        } else {
          const oldId = node.id;
          const response = await api.post(`/nodes/workflow/${params.id}`, {
            type: node.data.label,
            order,
            configJson: JSON.stringify(node.data.config || {}),
          });
          const newId = response.data.id.toString();
          idRemap.set(oldId, newId);
          node.id = newId;
        }
      }

      // Step 3: Remap edge source/target to backend IDs (fixes the disappearing-edges bug)
      let finalEdges = edgesRef.current;
      if (idRemap.size > 0) {
        finalEdges = edgesRef.current.map((edge) => ({
          ...edge,
          source: idRemap.get(edge.source) ?? edge.source,
          target: idRemap.get(edge.target) ?? edge.target,
        }));
        setEdges(finalEdges);
      }

      // Step 4: Persist workflow with correct edgesJson and final status
      const finalStatus = wasActive ? "ACTIVE" : (workflow.status ?? "DRAFT");
      await api.put(`/workflows/${params.id}`, {
        name: workflow.name,
        description: workflow.description,
        version: workflow.version,
        status: finalStatus,
        createdById: workflow.createdById,
        edgesJson: JSON.stringify(finalEdges),
      });
      if (wasActive) setWorkflow((w: any) => ({ ...w, status: "ACTIVE" }));

      alert("✅ Workflow saved successfully!");
      await fetchWorkflow();
    } catch (error: any) {
      alert(
        error.response?.data?.message ||
          error.message ||
          "Failed to save workflow",
      );
      await fetchWorkflow();
    }
  };
  if (!authReady) return null;

  if (loading) {
    return (
      <div className="flex h-screen">
        <Sidebar
          workflows={workflows}
          onCreateWorkflow={() => router.push("/dashboard")}
          onWorkflowDeleted={() =>
            api.get(getWorkflowsUrl()).then((r) => setWorkflows(r.data))
          }
        />
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto"></div>
            <p className="mt-4 text-gray-600">Loading workflow...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
        onWorkflowDeleted={() =>
          api.get(getWorkflowsUrl()).then((r) => setWorkflows(r.data))
        }
      />

      <div className="flex-1 flex flex-col">
        {/* Top Bar */}
        <div className="bg-white border-b px-6 py-4 flex items-center justify-between">
          <div className="flex items-center space-x-4">
            <button
              onClick={() => router.push("/dashboard")}
              className="text-gray-600 hover:text-gray-900"
            >
              <svg
                className="w-6 h-6"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M10 19l-7-7m0 0l7-7m-7 7h18"
                />
              </svg>
            </button>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold text-gray-900">{workflow?.name}</h1>
                {workflow?.status === "DRAFT" ? (
                  <button
                    onClick={openEditModal}
                    title="Edit name & description"
                    className="text-gray-400 hover:text-blue-600 transition-colors"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                    </svg>
                  </button>
                ) : (
                  <button
                    disabled
                    title="Set workflow to DRAFT to edit name/description"
                    className="text-gray-200 cursor-not-allowed"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                    </svg>
                  </button>
                )}
              </div>
              <p className="text-sm text-gray-500">{workflow?.description}</p>
            </div>
          </div>

          <div className="flex items-center space-x-3">
            <span
              className={`px-3 py-1 rounded-full text-xs font-medium ${
                workflow?.status === "ACTIVE"
                  ? "bg-green-100 text-green-800"
                  : "bg-yellow-100 text-yellow-800"
              }`}
            >
              {workflow?.status}
            </span>

            {workflow?.status === "DRAFT" ? (
              <Button
                onClick={handleActivate}
                variant="primary"
                className="px-4 py-2"
              >
                Activate
              </Button>
            ) : (
              <Button
                onClick={handleDeactivate}
                variant="secondary"
                className="px-4 py-2"
              >
                Deactivate
              </Button>
            )}

            <Button
              onClick={handleSave}
              variant="outline"
              className="px-6 py-2"
            >
              Save
            </Button>

            <button
              onClick={handleDuplicate}
              disabled={duplicating}
              title="Duplicate workflow"
              className="px-4 py-2 bg-gray-100 text-gray-700 rounded hover:bg-gray-200 border border-gray-300 flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {duplicating ? (
                <span className="w-4 h-4 border-2 border-gray-500 border-t-transparent rounded-full animate-spin" />
              ) : (
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"
                  />
                </svg>
              )}
              {duplicating ? "Duplicating…" : "Duplicate"}
            </button>

            <Button
              onClick={handleExecute}
              disabled={workflow?.status !== "ACTIVE" || executing}
              variant="primary"
              className="px-6 py-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {executing ? "Starting…" : "Execute"}
            </Button>
          </div>
        </div>

        {/* Validation Error Banner */}
        {validationErrors.length > 0 && (
          <div className="mx-6 mt-3 bg-red-50 border border-red-200 rounded-lg px-4 py-3 flex items-start justify-between">
            <div>
              <p className="text-red-700 text-sm font-semibold mb-1">
                Cannot save — fix the following issues:
              </p>
              <ul className="text-red-600 text-sm list-disc list-inside space-y-0.5">
                {validationErrors.map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
              </ul>
            </div>
            <button
              onClick={() => {
                setValidationErrors([]);
                setInvalidNodeIds(new Set());
              }}
              className="text-red-400 hover:text-red-600 ml-4 flex-shrink-0"
            >
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </button>
          </div>
        )}

        {/* Node Palette */}
        <div className="bg-gray-50 border-b px-6 py-3">
          <div className="flex items-center space-x-2">
            <span className="text-sm font-medium text-gray-700">Add Node:</span>
            <Button
              onClick={() => addNode("EMAIL")}
              variant="outline"
              className="px-4 py-2 text-sm flex items-center space-x-2"
            >
              <span>📧</span>
              <span>Email</span>
            </Button>
            <Button
              onClick={() => addNode("GPT")}
              variant="outline"
              className="px-4 py-2 text-sm flex items-center space-x-2"
            >
              <span>🤖</span>
              <span>GPT</span>
            </Button>
            <Button
              onClick={() => addNode("DRIVE")}
              variant="outline"
              className="px-4 py-2 text-sm flex items-center space-x-2"
            >
              <span>💾</span>
              <span>Drive</span>
            </Button>
            <Button
              onClick={() => addNode("EXCEL")}
              variant="outline"
              className="px-4 py-2 text-sm flex items-center space-x-2"
            >
              <span>📊</span>
              <span>Excel</span>
            </Button>
            <Button
              onClick={() => addNode("APPROVAL")}
              variant="outline"
              className="px-4 py-2 text-sm flex items-center space-x-2"
            >
              <span>✋</span>
              <span>Approval</span>
            </Button>
          </div>
        </div>

        {/* React Flow Canvas */}
        <div className="relative flex-1">
          <div className="absolute inset-0 flex">
            <div className="flex-1">
              <ReactFlow
                nodes={nodes.map((n) => ({
                  ...n,
                  style: {
                    ...n.style,
                    ...(invalidNodeIds.has(n.id)
                      ? { outline: "2px solid #ef4444", borderRadius: "8px" }
                      : {}),
                  },
                }))}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onNodeClick={onNodeClick}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                defaultEdgeOptions={{
                  type: "deletable",
                  animated: false,
                  style: { stroke: "#94a3b8", strokeWidth: 2 },
                  markerEnd: {
                    type: MarkerType.ArrowClosed,
                    color: "#94a3b8",
                  },
                }}
                fitView
                deleteKeyCode="Delete"
              >
                <Background
                  variant={"dots" as any}
                  gap={20}
                  size={1}
                  color="#d1d5db"
                />
                <Controls />
              </ReactFlow>
            </div>

            {/* Config Panel */}
            {selectedNode && (
              <NodeConfigPanel
                node={selectedNode}
                onUpdate={updateNodeConfig}
                onClose={() => setSelectedNode(null)}
              />
            )}
          </div>
        </div>
      </div>

      {/* Edit Name/Description Modal */}
      {showEditModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="fixed inset-0 bg-gray-900/40 backdrop-blur-sm" onClick={() => setShowEditModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-xl w-full max-w-md border border-gray-200 p-6">
            <div className="flex items-center justify-between mb-5">
              <h3 className="text-lg font-semibold text-gray-900">Edit Workflow</h3>
              <button onClick={() => setShowEditModal(false)} className="text-gray-400 hover:text-gray-600">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Name</label>
                <input
                  type="text"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm text-gray-900 focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400 focus:bg-white outline-none transition-all"
                  placeholder="Workflow name"
                  autoFocus
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Description</label>
                <textarea
                  value={editDescription}
                  onChange={(e) => setEditDescription(e.target.value)}
                  rows={3}
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm text-gray-900 focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400 focus:bg-white outline-none transition-all resize-none"
                  placeholder="Describe this workflow…"
                />
              </div>
              <div className="flex gap-3 pt-1">
                <button
                  onClick={() => setShowEditModal(false)}
                  className="flex-1 py-2.5 bg-gray-50 hover:bg-gray-100 text-gray-700 text-sm font-medium rounded-xl border border-gray-200 transition-all"
                >
                  Cancel
                </button>
                <button
                  onClick={handleEditSave}
                  disabled={editSaving || !editName.trim()}
                  className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white text-sm font-semibold rounded-xl transition-all shadow-sm flex items-center justify-center gap-2"
                >
                  {editSaving ? (
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  ) : "Save changes"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
