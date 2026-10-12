"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Hand, SearchX } from "lucide-react";
import { ApprovalDecision } from "@/components/executions/approval-decision";
import { ExecutionPipeline } from "@/components/executions/execution-pipeline";
import { StatusBadge } from "@/components/executions/status-badge";
import { StepResultCard } from "@/components/executions/step-result-card";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { useExecutionDetail } from "@/hooks/queries";
import { getUser } from "@/lib/auth";
import { getErrorMessage, getErrorStatus } from "@/lib/errors";
import { formatDateTime, formatDuration } from "@/lib/format";
import { describeInput } from "@/lib/step-output";

export default function ExecutionDetailPage() {
  const params = useParams<{ id: string }>();
  const executionId = Number(params.id);
  const { data, isLoading, isError, error, refetch } = useExecutionDetail(executionId);

  const backLink = (
    <Button variant="ghost" size="sm" asChild className="-ml-2 mb-3 text-muted-foreground">
      <Link href="/executions">
        <ArrowLeft /> Executions
      </Link>
    </Button>
  );

  if (isLoading) {
    return (
      <div className="mx-auto max-w-5xl space-y-4 px-4 py-6 sm:px-8 sm:py-8">
        {backLink}
        <Skeleton className="h-8 w-72" />
        <Skeleton className="h-40 w-full rounded-xl" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-8">
        {backLink}
        <EmptyState
          icon={SearchX}
          title={getErrorStatus(error) === 404 ? "Execution not found" : "Could not load this execution"}
          description={getErrorStatus(error) === 404 ? "It may have been deleted." : getErrorMessage(error)}
        />
      </div>
    );
  }

  const execution = data.workflowInstance;
  const steps = [...data.nodeInstances].sort((a, b) => a.executionOrder - b.executionOrder);
  const waiting = steps.find((s) => s.status === "WAITING_APPROVAL");
  const me = getUser();
  const canDecide = waiting && (!waiting.assignedToEmail || waiting.assignedToEmail.toLowerCase() === me?.email?.toLowerCase());

  return (
    <div className="mx-auto max-w-5xl space-y-5 px-4 py-6 sm:px-8 sm:py-8">
      <div>
        {backLink}
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">{execution.workflowName}</h1>
          <StatusBadge status={execution.status} />
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          #{execution.id} · Started {formatDateTime(execution.startedAt ?? execution.createdAt)} by {execution.triggeredByEmail}
          {execution.durationMs !== null && ` · ${formatDuration(execution.durationMs)}`}
        </p>
      </div>

      {(execution.status === "FAILED" || execution.status === "CANCELLED") && execution.errorMessage && (
        <div role="alert" className="rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span className="font-semibold">{execution.status === "FAILED" ? "Execution failed" : "Execution cancelled"}:</span>{" "}
          {execution.errorMessage}
        </div>
      )}

      <Card>
        <CardContent className="pt-5">
          <ExecutionPipeline steps={steps} status={execution.status} />
        </CardContent>
      </Card>

      {waiting && (
        <Card className="border-orange-500/40">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Hand className="h-4 w-4 text-orange-500" aria-hidden /> Approval required
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              {canDecide
                ? "Review the data below, then approve to continue or reject to stop the workflow."
                : `Waiting for ${waiting.assignedToName || waiting.assignedToEmail} to review this step.`}
            </p>
          </CardHeader>
          <CardContent className="space-y-4">
            {waiting.inputData && (
              <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-lg border bg-muted/40 p-3 text-xs">
                {describeInput(waiting.inputData)}
              </pre>
            )}
            {canDecide && <ApprovalDecision stepId={waiting.id} onDecided={() => refetch()} />}
          </CardContent>
        </Card>
      )}

      <section aria-labelledby="steps-heading" className="space-y-2">
        <h2 id="steps-heading" className="text-base font-semibold">
          Steps
        </h2>
        {steps.map((step) => (
          <StepResultCard key={step.id} step={step} onRetried={() => refetch()} />
        ))}
      </section>
    </div>
  );
}
