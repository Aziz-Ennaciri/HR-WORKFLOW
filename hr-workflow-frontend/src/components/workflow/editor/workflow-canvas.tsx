"use client";

import { useCallback } from "react";
import ReactFlow, { Background, BackgroundVariant, Controls, MiniMap, useReactFlow, type Connection, type Edge, type EdgeChange, type NodeChange } from "reactflow";
import { NODE_META } from "@/lib/node-meta";
import type { NodeType } from "@/types";
import { DeletableEdge } from "./deletable-edge";
import { DRAG_MIME } from "./node-palette";
import { EDGE_DEFAULTS, type EditorNode } from "./use-workflow-editor";
import { WorkflowNode } from "./workflow-node";

// Defined at module level so React Flow doesn't re-create node/edge renderers on every render
const nodeTypes = { workflow: WorkflowNode };
const edgeTypes = { deletable: DeletableEdge };

interface WorkflowCanvasProps {
  nodes: EditorNode[];
  edges: Edge[];
  onNodesChange: (changes: NodeChange[]) => void;
  onEdgesChange: (changes: EdgeChange[]) => void;
  onConnect: (connection: Connection) => void;
  isValidConnection: (connection: Connection) => boolean;
  onSelect: (id: string | null) => void;
  onDropNode: (type: NodeType, position: { x: number; y: number }) => void;
}

export function WorkflowCanvas({ nodes, edges, onNodesChange, onEdgesChange, onConnect, isValidConnection, onSelect, onDropNode }: WorkflowCanvasProps) {
  const { screenToFlowPosition } = useReactFlow();

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      const type = event.dataTransfer.getData(DRAG_MIME) as NodeType;
      if (!type) return;
      event.preventDefault();
      // Center the 220px-wide node on the cursor
      const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
      onDropNode(type, { x: position.x - 110, y: position.y - 30 });
    },
    [onDropNode, screenToFlowPosition],
  );

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      defaultEdgeOptions={EDGE_DEFAULTS}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}
      isValidConnection={isValidConnection}
      onNodeClick={(_, node) => onSelect(node.id)}
      onPaneClick={() => onSelect(null)}
      onDragOver={(event) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
      }}
      onDrop={onDrop}
      deleteKeyCode={["Backspace", "Delete"]}
      snapToGrid
      snapGrid={[20, 20]}
      fitView
      fitViewOptions={{ maxZoom: 1, padding: 0.3 }}
      proOptions={{ hideAttribution: true }}
      className="bg-muted/30"
    >
      <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="hsl(var(--border))" />
      <Controls className="!border-border !bg-card !shadow-sm [&>button]:!border-border [&>button]:!bg-card [&>button]:!fill-foreground" />
      <MiniMap
        pannable
        zoomable
        className="!hidden !border !bg-card md:!block"
        maskColor="hsl(var(--muted) / 0.6)"
        nodeColor={(node) => NODE_META[(node.data as EditorNode["data"]).type]?.accent ?? "#94a3b8"}
      />
    </ReactFlow>
  );
}
