"use client";

import { useState } from "react";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Clock, ListChecks, Loader2, Trash2, Zap } from "lucide-react";
import { toast } from "sonner";
import { StatusBadge } from "@/components/executions/status-badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Select } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useExecutions, useExecutionStats, useWorkflows } from "@/hooks/queries";
import { getErrorMessage } from "@/lib/errors";
import { formatDateTime, formatDuration } from "@/lib/format";
import { NODE_META } from "@/lib/node-meta";
import { executionsApi } from "@/services/executions";
import type { Execution, ExecutionStatus } from "@/types";

const PAGE_SIZE = 20;
const STATUSES: ExecutionStatus[] = ["RUNNING", "PAUSED", "PENDING", "COMPLETED", "FAILED", "CANCELLED"];

export default function ExecutionsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<ExecutionStatus | "">("");
  const [workflowId, setWorkflowId] = useState<number | "">("");
  const [toDelete, setToDelete] = useState<Execution | null>(null);
  const [deleting, setDeleting] = useState(false);

  const workflows = useWorkflows();
  const stats = useExecutionStats();
  const executions = useExecutions({
    page,
    size: PAGE_SIZE,
    status: status || undefined,
    workflowId: workflowId || undefined,
  });

  const byStatus = stats.data?.byStatus;
  const statCards = [
    { label: "Total", value: stats.data?.total },
    { label: "In progress", value: byStatus && byStatus.RUNNING + byStatus.PENDING + byStatus.PAUSED },
    { label: "Completed", value: byStatus?.COMPLETED },
    { label: "Failed", value: byStatus?.FAILED },
  ];

  const confirmDelete = async () => {
    if (!toDelete) return;
    setDeleting(true);
    try {
      await executionsApi.remove(toDelete.id);
      toast.success(`Execution #${toDelete.id} deleted`);
      setToDelete(null);
      await queryClient.invalidateQueries({ queryKey: ["executions"] });
    } catch (error) {
      toast.error(getErrorMessage(error, "Could not delete the execution"));
    } finally {
      setDeleting(false);
    }
  };

  const data = executions.data;

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-8">
      <PageHeader title="Executions" description="Every workflow run, newest first" />

      <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {statCards.map(({ label, value }) => (
          <Card key={label} className="px-4 py-3">
            <p className="text-xs text-muted-foreground">{label}</p>
            {value === undefined ? <Skeleton className="mt-1.5 h-6 w-10" /> : <p className="mt-0.5 text-2xl font-semibold">{value}</p>}
          </Card>
        ))}
      </div>

      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="space-y-1.5">
          <Label htmlFor="filter-status">Status</Label>
          <Select
            id="filter-status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as ExecutionStatus | "");
              setPage(0);
            }}
            className="sm:w-44"
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.charAt(0) + s.slice(1).toLowerCase()}
              </option>
            ))}
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="filter-workflow">Workflow</Label>
          <Select
            id="filter-workflow"
            value={workflowId}
            onChange={(e) => {
              setWorkflowId(e.target.value ? Number(e.target.value) : "");
              setPage(0);
            }}
            className="sm:w-56"
          >
            <option value="">All workflows</option>
            {workflows.data?.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </Select>
        </div>
        {executions.isFetching && !executions.isLoading && (
          <Loader2 className="mb-2.5 h-4 w-4 animate-spin text-muted-foreground" aria-label="Refreshing" />
        )}
      </div>

      {executions.isLoading ? (
        <div className="space-y-2">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-20 w-full rounded-xl" />
          ))}
        </div>
      ) : executions.isError ? (
        <EmptyState icon={ListChecks} title="Could not load executions" description={getErrorMessage(executions.error)} />
      ) : !data?.content.length ? (
        <EmptyState
          icon={ListChecks}
          title="No executions found"
          description={status || workflowId ? "Try other filters." : "Run a workflow to see it here."}
        />
      ) : (
        <ul className="space-y-2">
          {data.content.map((execution) => (
            <ExecutionRow key={execution.id} execution={execution} onDelete={() => setToDelete(execution)} />
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <nav aria-label="Pagination" className="mt-6 flex items-center justify-between text-sm text-muted-foreground">
          <span>
            Page {data.page + 1} of {data.totalPages} · {data.totalElements} executions
          </span>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              <ChevronLeft /> Previous
            </Button>
            <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next <ChevronRight />
            </Button>
          </div>
        </nav>
      )}

      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => !open && setToDelete(null)}
        title="Delete execution?"
        description={`Execution #${toDelete?.id} of "${toDelete?.workflowName}" and its step results will be removed.`}
        confirmLabel="Delete"
        destructive
        loading={deleting}
        onConfirm={confirmDelete}
      />
    </div>
  );
}

function ExecutionRow({ execution, onDelete }: { execution: Execution; onDelete: () => void }) {
  const running = execution.status === "RUNNING";
  const current = execution.currentNodeType ? NODE_META[execution.currentNodeType] : null;

  return (
    <li>
      <Card className="flex items-start gap-4 p-4 transition-shadow hover:shadow-md">
        <Link
          href={`/executions/${execution.id}`}
          className="min-w-0 flex-1 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="truncate text-[15px] font-semibold">{execution.workflowName}</h3>
            <StatusBadge status={execution.status} />
          </div>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <span className="flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5" aria-hidden />
              {formatDateTime(execution.startedAt ?? execution.createdAt)}
            </span>
            {execution.durationMs !== null && (
              <span className="flex items-center gap-1.5">
                <Zap className="h-3.5 w-3.5" aria-hidden />
                {formatDuration(execution.durationMs)}
              </span>
            )}
            <span className="text-xs">#{execution.id}</span>
          </div>
          {running && current && (
            <p className="mt-2 flex items-center gap-2 text-xs font-medium text-primary">
              <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden /> Running: {current.label}
            </p>
          )}
          {execution.status === "FAILED" && execution.errorMessage && (
            <p className="mt-2 max-w-xl truncate text-xs text-destructive">{execution.errorMessage}</p>
          )}
        </Link>
        <Button variant="ghost" size="icon" aria-label={`Delete execution #${execution.id}`} className="hover:text-destructive" onClick={onDelete}>
          <Trash2 />
        </Button>
      </Card>
    </li>
  );
}
