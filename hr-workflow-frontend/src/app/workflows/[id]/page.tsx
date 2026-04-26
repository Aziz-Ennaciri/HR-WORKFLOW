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
  const newNodeOffset = useRef(0);

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

  const onConnect = useCallback((connection: Connection) => {
    console.log("🔗 Node connected:", connection);
    setEdges((eds) => addEdge(connection, eds));
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

  const handleSave = async () => {
    try {
      const wasActive = workflow?.status === "ACTIVE";
      const edgesJson = JSON.stringify(edges);

      // Persist workflow (deactivate first if active, always carry edgesJson)
      await api.put(`/workflows/${params.id}`, {
        name: workflow.name,
        description: workflow.description,
        workflowKey: workflow.workflowKey,
        version: workflow.version,
        status: wasActive ? "DRAFT" : workflow.status,
        createdById: workflow.createdById,
        edgesJson,
      });
      if (wasActive) setWorkflow({ ...workflow, status: "DRAFT" });

      const existingNodesResponse = await api.get(`/nodes/workflow/${params.id}`);
      const existingNodes = existingNodesResponse.data;
      const existingNodeIds = new Set(existingNodes.map((n: any) => n.id.toString()));
      const currentNodeIds = new Set(nodes.map((n) => n.id));

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

      const orderMap = deriveOrderFromEdges(nodes, edges);

      for (const node of nodes) {
        const order = orderMap.get(node.id) ?? 1;
        const isExisting = existingNodeIds.has(node.id);

        if (isExisting) {
          await api.put(`/nodes/${node.id}`, {
            type: node.data.label,
            order,
            configJson: JSON.stringify(node.data.config || {}),
          });
        } else {
          const response = await api.post(`/nodes/workflow/${params.id}`, {
            type: node.data.label,
            order,
            configJson: JSON.stringify(node.data.config || {}),
          });
          node.id = response.data.id.toString();
        }
      }

      if (wasActive) {
        await api.put(`/workflows/${params.id}`, {
          name: workflow.name,
          description: workflow.description,
          workflowKey: workflow.workflowKey,
          version: workflow.version,
          status: "ACTIVE",
          createdById: workflow.createdById,
          edgesJson,
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
              onClick={handleExecute}
              disabled={workflow?.status !== "ACTIVE" || executing}
              variant="primary"
              className="px-6 py-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {executing ? "Starting…" : "Execute"}
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
    </div>
  );
}
