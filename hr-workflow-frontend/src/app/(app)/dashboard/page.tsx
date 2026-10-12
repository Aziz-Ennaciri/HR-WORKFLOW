"use client";

import Link from "next/link";
import { Activity, ArrowRight, CheckCircle2, Plus, Workflow as WorkflowIcon, Zap } from "lucide-react";
import dynamic from "next/dynamic";
import { useCreateWorkflow } from "@/components/layout/app-shell";
import { StatusBadge } from "@/components/executions/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useExecutions, useExecutionStats, useWorkflows } from "@/hooks/queries";
import { formatDuration, timeAgo } from "@/lib/format";

const ExecutionsChart = dynamic(() => import("@/components/dashboard/executions-chart"), {
  ssr: false,
  loading: () => <Skeleton className="h-64 w-full" />,
});

export default function DashboardPage() {
  const openCreate = useCreateWorkflow();
  const workflows = useWorkflows();
  const stats = useExecutionStats();
  const recent = useExecutions({ page: 0, size: 5 });

  const loading = workflows.isLoading || stats.isLoading;
  const total = stats.data?.total ?? 0;
  const successRate = total ? Math.round(((stats.data?.byStatus.COMPLETED ?? 0) / total) * 100) : 0;
  const activeCount = workflows.data?.filter((w) => w.status === "ACTIVE").length ?? 0;
  const chartData = (stats.data?.byWorkflow ?? []).map((row) => ({ name: row.workflowName, executions: row.total }));

  const statCards = [
    { label: "Workflows", value: workflows.data?.length ?? 0, icon: WorkflowIcon },
    { label: "Active workflows", value: activeCount, icon: Zap },
    { label: "Executions", value: total, icon: Activity },
    { label: "Success rate", value: `${successRate}%`, icon: CheckCircle2 },
  ];

  return (
    <div className="mx-auto max-w-6xl px-4 py-6 sm:px-8 sm:py-8">
      <PageHeader
        title="Dashboard"
        description="Overview of your workflows and recent activity"
        actions={
          <Button variant="outline" asChild>
            <Link href="/executions">
              All executions <ArrowRight />
            </Link>
          </Button>
        }
      />

      {!loading && workflows.data?.length === 0 ? (
        <EmptyState
          icon={WorkflowIcon}
          title="No workflows yet"
          description="Create your first workflow, or start from a template."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Button onClick={openCreate}>
                <Plus /> New workflow
              </Button>
              <Button variant="outline" asChild>
                <Link href="/templates">Browse templates</Link>
              </Button>
            </div>
          }
        />
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {statCards.map(({ label, value, icon: Icon }) => (
              <Card key={label} className="p-5">
                <div className="flex items-center justify-between">
                  <p className="text-sm text-muted-foreground">{label}</p>
                  <Icon className="h-4 w-4 text-muted-foreground" aria-hidden />
                </div>
                {loading ? <Skeleton className="mt-3 h-8 w-16" /> : <p className="mt-2 text-3xl font-bold tracking-tight">{value}</p>}
              </Card>
            ))}
          </div>

          <div className="grid gap-6 lg:grid-cols-5">
            <Card className="lg:col-span-3">
              <CardHeader>
                <CardTitle>Executions by workflow</CardTitle>
              </CardHeader>
              <CardContent>
                {loading ? (
                  <Skeleton className="h-64 w-full" />
                ) : chartData.length === 0 ? (
                  <p className="py-16 text-center text-sm text-muted-foreground">No executions yet</p>
                ) : (
                  <ExecutionsChart data={chartData} />
                )}
              </CardContent>
            </Card>

            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Recent runs</CardTitle>
              </CardHeader>
              <CardContent className="space-y-1">
                {recent.isLoading ? (
                  [0, 1, 2].map((i) => <Skeleton key={i} className="h-12 w-full" />)
                ) : recent.data?.content.length ? (
                  recent.data.content.map((execution) => (
                    <Link
                      key={execution.id}
                      href={`/executions/${execution.id}`}
                      className="flex items-center justify-between gap-3 rounded-lg px-3 py-2.5 hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium">{execution.workflowName}</p>
                        <p className="text-xs text-muted-foreground">
                          #{execution.id} · {timeAgo(execution.startedAt ?? execution.createdAt)}
                          {execution.durationMs ? ` · ${formatDuration(execution.durationMs)}` : ""}
                        </p>
                      </div>
                      <StatusBadge status={execution.status} />
                    </Link>
                  ))
                ) : (
                  <p className="py-10 text-center text-sm text-muted-foreground">Run a workflow to see it here</p>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
