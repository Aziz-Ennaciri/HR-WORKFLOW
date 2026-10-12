"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowUpRight, ChevronDown, ClipboardCheck, Hand } from "lucide-react";
import { ApprovalDecision } from "@/components/executions/approval-decision";
import { GptOutput } from "@/components/executions/gpt-output";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useMyApprovals } from "@/hooks/queries";
import { getErrorMessage } from "@/lib/errors";
import { timeAgo } from "@/lib/format";
import { describeInput } from "@/lib/step-output";
import { cn } from "@/lib/utils";
import type { PendingApproval } from "@/types";

export default function ApprovalsPage() {
  const { data: approvals, isLoading, isError, error } = useMyApprovals();

  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-8 sm:py-8">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            My approvals
            {!!approvals?.length && <Badge tone="warning">{approvals.length} pending</Badge>}
          </span>
        }
        description="Workflow steps waiting for your decision"
      />

      {isLoading ? (
        <div className="space-y-3">
          {[0, 1].map((i) => (
            <Skeleton key={i} className="h-20 w-full rounded-xl" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={ClipboardCheck} title="Could not load approvals" description={getErrorMessage(error)} />
      ) : !approvals?.length ? (
        <EmptyState icon={ClipboardCheck} title="All caught up" description="Nothing is waiting for your approval." />
      ) : (
        <ul className="space-y-3">
          {approvals.map((approval) => (
            <ApprovalCard key={approval.nodeInstanceId} approval={approval} />
          ))}
        </ul>
      )}
    </div>
  );
}

function ApprovalCard({ approval }: { approval: PendingApproval }) {
  const [expanded, setExpanded] = useState(false);
  const panelId = `approval-${approval.nodeInstanceId}`;
  const reviewText = describeInput(approval.inputData);

  return (
    <li>
      <Card className="overflow-hidden">
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
          aria-controls={panelId}
          className="flex w-full items-start justify-between gap-3 px-5 py-4 text-left hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
        >
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300">
              <Hand className="h-[18px] w-[18px]" aria-hidden />
            </span>
            <div>
              <p className="text-sm font-semibold">{approval.workflowName}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Requested by <span className="text-foreground">{approval.triggeredByName?.trim() || approval.triggeredByEmail}</span> ·{" "}
                {timeAgo(approval.createdAt)}
              </p>
            </div>
          </div>
          <ChevronDown className={cn("mt-1 h-4 w-4 shrink-0 text-muted-foreground transition-transform", expanded && "rotate-180")} aria-hidden />
        </button>

        {expanded && (
          <div id={panelId} className="space-y-4 border-t px-5 py-4">
            {approval.instructions && (
              <div className="rounded-lg border border-primary/20 bg-primary/5 px-4 py-3">
                <p className="mb-1 text-xs font-semibold text-primary">Instructions</p>
                <p className="text-sm">{approval.instructions}</p>
              </div>
            )}
            {reviewText && (
              <div>
                <p className="mb-2 text-xs font-medium text-muted-foreground">Data submitted for review</p>
                <div className="max-h-64 overflow-y-auto rounded-lg border bg-muted/40 p-3">
                  <GptOutput text={reviewText} />
                </div>
              </div>
            )}
            <Link
              href={`/executions/${approval.workflowInstanceId}`}
              className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
            >
              View full execution <ArrowUpRight className="h-3.5 w-3.5" aria-hidden />
            </Link>
            <ApprovalDecision stepId={approval.nodeInstanceId} />
          </div>
        )}
      </Card>
    </li>
  );
}
