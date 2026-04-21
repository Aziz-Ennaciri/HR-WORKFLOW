"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import NodeConfigPanel from "@/components/workflow/NodeConfigPanel";
import { nodeTypes } from "@/components/workflow/nodes";
import { useAuthGuard } from "@/lib/auth";
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
        workflowKey: workflow.workflowKey,
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
        workflowKey: workflow.workflowKey,
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

      const edgesKey = `workflow-${params.id}-edges`;
      const savedEdges = localStorage.getItem(edgesKey);
      if (savedEdges) {
        const parsedEdges = JSON.parse(savedEdges);
        setEdges(parsedEdges);
      }
    } catch (error) {
    } finally {
      setLoading(false);
    }
  };

  const fetchWorkflows = async () => {
    try {
      const response = await api.get("/workflows");
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

  const onConnect = useCallback((connection: Connection) => {
    console.log("🔗 Node connected:", connection);
    setEdges((eds) => addEdge(connection, eds));
  }, []);

  const addNode = (type: string) => {
    const newNode: Node = {
      id: `${type}-${Date.now()}`,
      type: type.toLowerCase(),
      position: { x: 250, y: 100 + nodes.length * 100 },
      data: {
        label: type,
        config: {},
        order: nodes.length + 1,
      },
    };
    setNodes((nds) => [...nds, newNode]);
  };

  const handleSave = async () => {
    try {
      const wasActive = workflow?.status === "ACTIVE";

      if (wasActive) {
        await api.put(`/workflows/${params.id}`, {
          name: workflow.name,
          description: workflow.description,
          workflowKey: workflow.workflowKey,
          version: workflow.version,
          status: "DRAFT",
          createdById: workflow.createdById,
        });
        setWorkflow({ ...workflow, status: "DRAFT" });
      }

      const existingNodesResponse = await api.get(
        `/nodes/workflow/${params.id}`,
      );
      const existingNodes = existingNodesResponse.data;
      const existingNodeIds = new Set(
        existingNodes.map((n: any) => n.id.toString()),
      );
      const currentNodeIds = new Set(nodes.map((n) => n.id));
      console.log(currentNodeIds);

      const nodesToDelete = existingNodes.filter(
        (n: any) => !currentNodeIds.has(n.id.toString()),
      );
      console.log("🗑️ Deleting", nodesToDelete.length, "removed nodes...");
      for (const node of nodesToDelete) {
        try {
          await api.delete(`/nodes/${node.id}`);
        } catch (error: any) {
          console.warn(
            "⚠️ Failed to delete node",
            node.id,
            "- might have executions",
          );
        }
      }

      console.log("💾 Saving", nodes.length, "nodes...");
      for (let i = 0; i < nodes.length; i++) {
        const node = nodes[i];
        const isExisting = existingNodeIds.has(node.id);

        if (isExisting) {
          await api.put(`/nodes/${node.id}`, {
            type: node.data.label,
            order: i + 1,
            configJson: JSON.stringify(node.data.config || {}),
          });
        } else {
          const response = await api.post(`/nodes/workflow/${params.id}`, {
            type: node.data.label,
            order: i + 1,
            configJson: JSON.stringify(node.data.config || {}),
          });
          node.id = response.data.id.toString();
        }
      }
      const edgesKey = `workflow-${params.id}-edges`;
      localStorage.setItem(edgesKey, JSON.stringify(edges));

      if (wasActive) {
        console.log("🟢 Reactivating workflow...");
        await api.put(`/workflows/${params.id}`, {
          name: workflow.name,
          description: workflow.description,
          workflowKey: workflow.workflowKey,
          version: workflow.version,
          status: "ACTIVE",
          createdById: workflow.createdById,
        });
        setWorkflow({ ...workflow, status: "ACTIVE" });
      }

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
            api.get("/workflows").then((r) => setWorkflows(r.data))
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
          api.get("/workflows").then((r) => setWorkflows(r.data))
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
              <h1 className="text-xl font-bold text-gray-900">
                {workflow?.name}
              </h1>
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

            <Button
              onClick={() => router.push(`/workflows/${params.id}/execute`)}
              disabled={workflow?.status !== "ACTIVE"}
              variant="primary"
              className="px-6 py-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Execute
            </Button>
          </div>
        </div>

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
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onNodeClick={onNodeClick}
                nodeTypes={nodeTypes}
                defaultEdgeOptions={{
                  type: "smoothstep",
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
    </div>
  );
}
