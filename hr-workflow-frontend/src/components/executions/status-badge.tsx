import { Badge, type BadgeTone } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { ExecutionStatus, StepStatus, WorkflowStatus } from "@/types";

type AnyStatus = ExecutionStatus | StepStatus | WorkflowStatus;

const TONES: Record<AnyStatus, BadgeTone> = {
  COMPLETED: "success",
  ACTIVE: "success",
  FAILED: "danger",
  REJECTED: "danger",
  CANCELLED: "neutral",
  RUNNING: "info",
  IN_PROGRESS: "info",
  RETRYING: "info",
  PENDING: "neutral",
  PAUSED: "warning",
  WAITING_APPROVAL: "warning",
  DRAFT: "neutral",
  INACTIVE: "neutral",
  ARCHIVED: "neutral",
  SKIPPED: "neutral",
};

const LABELS: Partial<Record<AnyStatus, string>> = {
  IN_PROGRESS: "Running",
  WAITING_APPROVAL: "Awaiting approval",
  PAUSED: "Awaiting approval",
};

const LIVE: AnyStatus[] = ["RUNNING", "IN_PROGRESS", "RETRYING", "PENDING", "PAUSED", "WAITING_APPROVAL"];

export function statusLabel(status: AnyStatus): string {
  return LABELS[status] ?? status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, " ");
}

export function StatusBadge({ status, className }: { status: AnyStatus; className?: string }) {
  return (
    <Badge tone={TONES[status] ?? "neutral"} className={className}>
      <span
        className={cn("h-1.5 w-1.5 rounded-full bg-current", LIVE.includes(status) && "animate-pulse")}
        aria-hidden
      />
      {statusLabel(status)}
    </Badge>
  );
}
