"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  addEdge,
  applyEdgeChanges,
  applyNodeChanges,
  MarkerType,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
  type XYPosition,
} from "reactflow";
import { queryKeys } from "@/hooks/queries";
import { workflowsApi } from "@/services/workflows";
import type { EditorNodeData, NodeType, Workflow, WorkflowNode } from "@/types";
import { validateGraph, validateNodeConfig } from "./validation";

export type EditorNode = Node<EditorNodeData>;

export const EDGE_DEFAULTS: Partial<Edge> = {
  type: "deletable",
  style: { stroke: "hsl(var(--muted-foreground))", strokeWidth: 2 },
  markerEnd: { type: MarkerType.ArrowClosed, color: "hsl(var(--muted-foreground))" },
};

interface Snapshot {
  nodes: EditorNode[];
  edges: Edge[];
}

function parseJson<T>(text: string | null | undefined, fallback: T): T {
  if (!text) return fallback;
  try {
    return JSON.parse(text) as T;
  } catch {
    return fallback;
  }
}

function toEditorState(workflow: Workflow, serverNodes: WorkflowNode[]): Snapshot {
  const layout = parseJson<Record<string, XYPosition>>(workflow.layoutJson, {});
  const nodes: EditorNode[] = [...serverNodes]
    .sort((a, b) => a.order - b.order)
    .map((node, index) => ({
      id: String(node.id),
      type: "workflow",
      position: layout[String(node.id)] ?? { x: 80 + index * 280, y: 160 },
      data: { type: node.type, config: parseJson(node.configJson, {}) } as EditorNodeData,
    }));
  const known = new Set(nodes.map((n) => n.id));
  const edges = parseJson<Edge[]>(workflow.edgesJson, [])
    .filter((e) => known.has(e.source) && known.has(e.target))
    .map((e) => ({ ...e, ...EDGE_DEFAULTS }));
  return { nodes, edges };
}

/** What gets saved; used to tell whether the canvas has unsaved changes. */
function fingerprint({ nodes, edges }: Snapshot): string {
  return JSON.stringify({
    nodes: nodes
      .map((n) => [n.id, n.data.type, n.data.config, Math.round(n.position.x), Math.round(n.position.y)])
      .sort(),
    edges: edges.map((e) => [e.source, e.target]).sort(),
  });
}

const HISTORY_LIMIT = 50;

export function useWorkflowEditor(workflowId: number, workflow?: Workflow, serverNodes?: WorkflowNode[]) {
  const queryClient = useQueryClient();
  const [nodes, setNodes] = useState<EditorNode[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [savedFingerprint, setSavedFingerprint] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const past = useRef<Snapshot[]>([]);
  const future = useRef<Snapshot[]>([]);
  const lastConfigEdit = useRef<{ id: string; at: number } | null>(null);
  const [, forceRender] = useState(0);
  const nextNodeOffset = useRef(0);

  // Load the server state once per workflow (and again after each save, see below)
  const loadedFor = useRef<number | null>(null);
  useEffect(() => {
    if (!workflow || !serverNodes || loadedFor.current === workflowId) return;
    loadedFor.current = workflowId;
    const state = toEditorState(workflow, serverNodes);
    setNodes(state.nodes);
    setEdges(state.edges);
    setSavedFingerprint(fingerprint(state));
    setSelectedId(null);
    past.current = [];
    future.current = [];
    nextNodeOffset.current = state.nodes.length;
  }, [workflowId, workflow, serverNodes]);

  const current = useCallback((): Snapshot => ({ nodes, edges }), [nodes, edges]);

  const remember = useCallback(() => {
    past.current = [...past.current.slice(-(HISTORY_LIMIT - 1)), current()];
    future.current = [];
    forceRender((n) => n + 1);
  }, [current]);

  const undo = useCallback(() => {
    const previous = past.current.pop();
    if (!previous) return;
    future.current.push(current());
    setNodes(previous.nodes);
    setEdges(previous.edges);
    lastConfigEdit.current = null;
    forceRender((n) => n + 1);
  }, [current]);

  const redo = useCallback(() => {
    const next = future.current.pop();
    if (!next) return;
    past.current.push(current());
    setNodes(next.nodes);
    setEdges(next.edges);
    lastConfigEdit.current = null;
    forceRender((n) => n + 1);
  }, [current]);

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      // Moves are recorded once, when the drag starts; removals before they happen
      if (changes.some((c) => c.type === "remove" || (c.type === "position" && c.dragging && !nodes.find((n) => n.id === c.id)?.dragging))) {
        remember();
      }
      setNodes((current) => applyNodeChanges(changes, current) as EditorNode[]);
      const removed = changes.filter((c) => c.type === "remove").map((c) => (c as { id: string }).id);
      if (removed.length) {
        setEdges((current) => current.filter((e) => !removed.includes(e.source) && !removed.includes(e.target)));
        if (selectedId && removed.includes(selectedId)) setSelectedId(null);
      }
    },
    [nodes, remember, selectedId],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      if (changes.some((c) => c.type === "remove")) remember();
      setEdges((current) => applyEdgeChanges(changes, current));
    },
    [remember],
  );

  const onConnect = useCallback(
    (connection: Connection) => {
      remember();
      setEdges((current) => addEdge({ ...connection, ...EDGE_DEFAULTS }, current));
    },
    [remember],
  );

  /** Blocks connections the engine cannot run: self-loops, duplicates and a second outgoing/incoming link. */
  const isValidConnection = useCallback(
    (connection: Connection) =>
      !!connection.source &&
      !!connection.target &&
      connection.source !== connection.target &&
      !edges.some((e) => e.source === connection.source || e.target === connection.target),
    [edges],
  );

  const addNode = useCallback(
    (type: NodeType, position?: XYPosition) => {
      remember();
      const index = nextNodeOffset.current++;
      const node: EditorNode = {
        id: `new-${type.toLowerCase()}-${Date.now()}`,
        type: "workflow",
        position: position ?? { x: 80 + (index % 4) * 280, y: 160 + Math.floor(index / 4) * 160 },
        data: { type, config: type === "DRIVE" ? { action: "read" } : type === "EXCEL" ? { operation: "WRITE" } : {} } as EditorNodeData,
      };
      setNodes((current) => [...current, node]);
      setSelectedId(node.id);
    },
    [remember],
  );

  const updateConfig = useCallback(
    (id: string, config: EditorNodeData["config"]) => {
      // Typing in one step's settings is one undo entry, not one per keystroke
      const now = Date.now();
      if (!lastConfigEdit.current || lastConfigEdit.current.id !== id || now - lastConfigEdit.current.at > 1500) remember();
      lastConfigEdit.current = { id, at: now };
      setNodes((current) =>
        current.map((n) => (n.id === id ? { ...n, data: { ...n.data, config } as EditorNodeData } : n)),
      );
    },
    [remember],
  );

  const deleteNode = useCallback(
    (id: string) => {
      remember();
      setNodes((current) => current.filter((n) => n.id !== id));
      setEdges((current) => current.filter((e) => e.source !== id && e.target !== id));
      setSelectedId((selected) => (selected === id ? null : selected));
    },
    [remember],
  );

  // ── Validation (live) ──────────────────────────────────────────
  const nodeErrors = useMemo(() => new Map(nodes.map((n) => [n.id, validateNodeConfig(n.data)])), [nodes]);
  const graphErrors = useMemo(() => validateGraph(nodes, edges), [nodes, edges]);
  const renderedNodes = useMemo(
    () => nodes.map((n) => ((nodeErrors.get(n.id)?.length ?? 0) > 0 !== !!n.data.invalid ? { ...n, data: { ...n.data, invalid: !n.data.invalid } } : n)),
    [nodes, nodeErrors],
  );
  const incompleteSteps = [...nodeErrors.values()].filter((errors) => errors.length > 0).length;

  const dirty = savedFingerprint !== null && fingerprint({ nodes, edges }) !== savedFingerprint;

  // Warn before closing or reloading the tab with unsaved changes
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  /** Saves the canvas in one request, then reloads it so new steps get their server ids. */
  const save = useCallback(async () => {
    setSaving(true);
    try {
      await workflowsApi.saveGraph(workflowId, {
        nodes: nodes.map((n) => ({ id: n.id, type: n.data.type, configJson: JSON.stringify(n.data.config ?? {}) })),
        edges: edges.map(({ id, source, target, type }) => ({ id, source, target, type })),
        layout: Object.fromEntries(nodes.map((n) => [n.id, { x: Math.round(n.position.x), y: Math.round(n.position.y) }])),
      });
      const [freshWorkflow, freshNodes] = await Promise.all([workflowsApi.get(workflowId), workflowsApi.nodes(workflowId)]);
      queryClient.setQueryData(queryKeys.workflow(workflowId), freshWorkflow);
      queryClient.setQueryData(queryKeys.workflowNodes(workflowId), freshNodes);
      const state = toEditorState(freshWorkflow, freshNodes);
      setNodes(state.nodes);
      setEdges(state.edges);
      setSavedFingerprint(fingerprint(state));
      setSelectedId(null);
      past.current = [];
      future.current = [];
    } finally {
      setSaving(false);
    }
  }, [edges, nodes, queryClient, workflowId]);

  return {
    loaded: savedFingerprint !== null,
    nodes: renderedNodes,
    edges,
    selectedNode: nodes.find((n) => n.id === selectedId) ?? null,
    setSelectedId,
    onNodesChange,
    onEdgesChange,
    onConnect,
    isValidConnection,
    addNode,
    updateConfig,
    deleteNode,
    nodeErrors,
    graphErrors,
    incompleteSteps,
    dirty,
    saving,
    save,
    undo,
    redo,
    canUndo: past.current.length > 0,
    canRedo: future.current.length > 0,
  };
}
