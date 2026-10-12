"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { ReactFlowProvider } from "reactflow";
import { AlertTriangle, ArrowLeft, Copy, Pause, Pencil, Play, Power, Redo2, Save, SearchX, Undo2 } from "lucide-react";
import { toast } from "sonner";
import { StatusBadge } from "@/components/executions/status-badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { EditWorkflowDialog } from "@/components/workflow/editor/edit-workflow-dialog";
import { NodeConfigPanel } from "@/components/workflow/editor/node-config-panel";
import { NodePalette } from "@/components/workflow/editor/node-palette";
import { useWorkflowEditor } from "@/components/workflow/editor/use-workflow-editor";
import { WorkflowCanvas } from "@/components/workflow/editor/workflow-canvas";
import { queryKeys, useInvalidateWorkflows, useWorkflow, useWorkflowNodes } from "@/hooks/queries";
import { getErrorMessage, getErrorStatus } from "@/lib/errors";
import { executionsApi } from "@/services/executions";
import { workflowsApi } from "@/services/workflows";
import type { WorkflowStatus } from "@/types";

export default function WorkflowEditorPage() {
  return (
    <ReactFlowProvider>
      <WorkflowEditor />
    </ReactFlowProvider>
  );
}

function WorkflowEditor() {
  const params = useParams<{ id: string }>();
  const workflowId = Number(params.id);
  const router = useRouter();
  const queryClient = useQueryClient();
  const invalidateWorkflows = useInvalidateWorkflows();
  const workflowQuery = useWorkflow(workflowId);
  const nodesQuery = useWorkflowNodes(workflowId);
  const workflow = workflowQuery.data;
  const editor = useWorkflowEditor(workflowId, workflow, nodesQuery.data);

  const [renameOpen, setRenameOpen] = useState(false);
  const [busy, setBusy] = useState<"status" | "run" | "duplicate" | null>(null);
  const [serverErrors, setServerErrors] = useState<string | null>(null);

  // Keyboard shortcuts: Ctrl/Cmd+S save, Ctrl/Cmd+Z undo, Ctrl/Cmd+Shift+Z or Ctrl+Y redo
  const { save, undo, redo, dirty } = editor;
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (!(event.metaKey || event.ctrlKey)) return;
      const target = event.target as HTMLElement;
      const typing = target.closest("input, textarea, select, [contenteditable]");
      const key = event.key.toLowerCase();
      if (key === "s") {
        event.preventDefault();
        if (dirty) void saveWithFeedback();
      } else if (!typing && key === "z") {
        event.preventDefault();
        if (event.shiftKey) redo();
        else undo();
      } else if (!typing && key === "y") {
        event.preventDefault();
        redo();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const saveWithFeedback = async (): Promise<boolean> => {
    setServerErrors(null);
    try {
      await save();
      await invalidateWorkflows();
      toast.success("Workflow saved");
      return true;
    } catch (error) {
      setServerErrors(getErrorMessage(error, "Could not save the workflow"));
      return false;
    }
  };

  const changeStatus = async (status: WorkflowStatus) => {
    if (!workflow) return;
    setBusy("status");
    setServerErrors(null);
    try {
      if (dirty && !(await saveWithFeedback())) return;
      const updated = await workflowsApi.setStatus(workflow, status);
      queryClient.setQueryData(queryKeys.workflow(workflowId), updated);
      await invalidateWorkflows();
      toast.success(status === "ACTIVE" ? "Workflow activated" : "Workflow moved back to draft");
    } catch (error) {
      setServerErrors(getErrorMessage(error));
    } finally {
      setBusy(null);
    }
  };

  /** Runs what is on screen: unsaved changes are saved first. */
  const run = async () => {
    setBusy("run");
    try {
      if (dirty && !(await saveWithFeedback())) return;
      const execution = await executionsApi.trigger(workflowId);
      await queryClient.invalidateQueries({ queryKey: ["executions"] });
      router.push(`/executions/${execution.id}`);
    } catch (error) {
      toast.error(getErrorMessage(error, "Could not start the workflow"));
    } finally {
      setBusy(null);
    }
  };

  const duplicate = async () => {
    setBusy("duplicate");
    try {
      const copy = await workflowsApi.duplicate(workflowId);
      await invalidateWorkflows();
      toast.success(`Duplicated as "${copy.name}"`);
      router.push(`/workflows/${copy.id}`);
    } catch (error) {
      toast.error(getErrorMessage(error, "Could not duplicate the workflow"));
    } finally {
      setBusy(null);
    }
  };

  if (workflowQuery.isError || nodesQuery.isError) {
    const notFound = getErrorStatus(workflowQuery.error) === 404;
    return (
      <div className="p-8">
        <EmptyState
          icon={SearchX}
          title={notFound ? "Workflow not found" : "Could not load this workflow"}
          description={notFound ? "It may have been deleted." : getErrorMessage(workflowQuery.error ?? nodesQuery.error)}
          action={
            <Button variant="outline" asChild>
              <Link href="/dashboard">Back to dashboard</Link>
            </Button>
          }
        />
      </div>
    );
  }

  if (!workflow || !editor.loaded) {
    return (
      <div className="flex h-full flex-col">
        <div className="border-b bg-card px-4 py-3">
          <Skeleton className="h-7 w-64" />
        </div>
        <Skeleton className="m-4 flex-1 rounded-xl" />
      </div>
    );
  }

  const active = workflow.status === "ACTIVE";
  const selected = editor.selectedNode;
  const warnings = [
    ...editor.graphErrors,
    ...(editor.incompleteSteps > 0 ? [`${editor.incompleteSteps} step(s) still need settings (marked in red)`] : []),
  ];

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <div className="flex flex-col gap-3 border-b bg-card px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <Button variant="ghost" size="icon" asChild aria-label="Back to dashboard">
            <Link href="/dashboard">
              <ArrowLeft />
            </Link>
          </Button>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="truncate text-lg font-semibold">{workflow.name}</h1>
              <StatusBadge status={workflow.status} />
              {editor.dirty && (
                <span className="text-xs text-amber-600 dark:text-amber-400" role="status">
                  Unsaved changes
                </span>
              )}
            </div>
            {workflow.description && <p className="truncate text-xs text-muted-foreground">{workflow.description}</p>}
          </div>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Rename workflow"
            title={workflow.status === "DRAFT" ? "Rename" : "Move back to draft to rename"}
            disabled={workflow.status !== "DRAFT"}
            onClick={() => setRenameOpen(true)}
          >
            <Pencil />
          </Button>
        </div>

        <div className="flex flex-wrap items-center gap-2 lg:shrink-0 lg:flex-nowrap">
          <Button variant="ghost" size="icon" aria-label="Undo" title="Undo (Ctrl+Z)" disabled={!editor.canUndo} onClick={editor.undo}>
            <Undo2 />
          </Button>
          <Button variant="ghost" size="icon" aria-label="Redo" title="Redo (Ctrl+Shift+Z)" disabled={!editor.canRedo} onClick={editor.redo}>
            <Redo2 />
          </Button>
          <Button variant="outline" size="sm" loading={editor.saving} disabled={!editor.dirty} onClick={saveWithFeedback} title="Save (Ctrl+S)">
            {!editor.saving && <Save />} Save
          </Button>
          <Button variant="outline" size="sm" loading={busy === "duplicate"} disabled={busy !== null} onClick={duplicate}>
            {busy !== "duplicate" && <Copy />} Duplicate
          </Button>
          {active ? (
            <Button variant="outline" size="sm" loading={busy === "status"} disabled={busy !== null} onClick={() => changeStatus("DRAFT")}>
              {busy !== "status" && <Pause />} Deactivate
            </Button>
          ) : (
            <Button variant="outline" size="sm" loading={busy === "status"} disabled={busy !== null || editor.nodes.length === 0} onClick={() => changeStatus("ACTIVE")}>
              {busy !== "status" && <Power />} Activate
            </Button>
          )}
          <Button size="sm" loading={busy === "run"} disabled={!active || busy !== null} title={active ? "Run now" : "Activate the workflow to run it"} onClick={run}>
            {busy !== "run" && <Play />} Run
          </Button>
        </div>
      </div>

      {(serverErrors || warnings.length > 0) && (
        <div
          role={serverErrors ? "alert" : "status"}
          className={
            serverErrors
              ? "flex items-start gap-2 border-b border-destructive/30 bg-destructive/10 px-4 py-2 text-sm text-destructive"
              : "flex items-start gap-2 border-b bg-amber-500/10 px-4 py-2 text-sm text-amber-800 dark:text-amber-300"
          }
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <div>{serverErrors ?? warnings.join(" · ")}</div>
        </div>
      )}

      <NodePalette onAdd={(type) => editor.addNode(type)} />

      <div className="relative flex min-h-0 flex-1">
        <div className="min-w-0 flex-1">
          {editor.nodes.length === 0 && (
            <div className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center">
              <p className="rounded-lg border bg-card px-4 py-3 text-sm text-muted-foreground shadow-sm">
                Add a step from the bar above, or drag one onto the canvas.
              </p>
            </div>
          )}
          <WorkflowCanvas
            nodes={editor.nodes}
            edges={editor.edges}
            onNodesChange={editor.onNodesChange}
            onEdgesChange={editor.onEdgesChange}
            onConnect={editor.onConnect}
            isValidConnection={editor.isValidConnection}
            onSelect={editor.setSelectedId}
            onDropNode={editor.addNode}
          />
        </div>
        {selected && (
          <NodeConfigPanel
            key={selected.id}
            node={selected}
            errors={editor.nodeErrors.get(selected.id) ?? []}
            onChange={(config) => editor.updateConfig(selected.id, config)}
            onDelete={() => editor.deleteNode(selected.id)}
            onClose={() => editor.setSelectedId(null)}
            className="absolute inset-0 z-20 border-l md:static md:w-96 md:shrink-0"
          />
        )}
      </div>

      <EditWorkflowDialog workflow={workflow} open={renameOpen} onOpenChange={setRenameOpen} />
    </div>
  );
}
