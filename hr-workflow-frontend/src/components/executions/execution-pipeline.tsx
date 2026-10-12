import { Check, Loader2, Pause, X } from "lucide-react";
import { NODE_META } from "@/lib/node-meta";
import { formatDuration } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ExecutionStatus, ExecutionStep } from "@/types";

const RING: Record<string, string> = {
  COMPLETED: "border-emerald-500/60",
  FAILED: "border-destructive/60",
  REJECTED: "border-destructive/60",
  IN_PROGRESS: "border-primary ring-4 ring-primary/15",
  RETRYING: "border-primary ring-4 ring-primary/15",
  WAITING_APPROVAL: "border-orange-500 ring-4 ring-orange-500/15",
};

function stepCaption(step: ExecutionStep): string {
  switch (step.status) {
    case "IN_PROGRESS":
    case "RETRYING":
      return step.retryCount > 0 ? `Retrying (${step.retryCount})…` : "Running…";
    case "WAITING_APPROVAL":
      return "Awaiting approval";
    case "COMPLETED":
      return formatDuration(step.durationMs) || "Done";
    case "FAILED":
      return "Failed";
    case "REJECTED":
      return step.actor ? `Rejected by ${step.actor}` : "Rejected";
    default:
      return "Queued";
  }
}

/** The run's steps left to right, with live status and an overall progress bar. */
export function ExecutionPipeline({ steps, status }: { steps: ExecutionStep[]; status: ExecutionStatus }) {
  const done = steps.filter((s) => s.status === "COMPLETED").length;
  const progress = status === "COMPLETED" ? 100 : steps.length ? Math.round((done / steps.length) * 100) : 0;
  const barColor =
    status === "FAILED" || status === "CANCELLED"
      ? "bg-destructive"
      : status === "COMPLETED"
        ? "bg-emerald-500"
        : status === "PAUSED"
          ? "bg-orange-500"
          : "bg-primary";

  return (
    <div className="space-y-5">
      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-valuenow={progress}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="Execution progress"
      >
        <div className={cn("h-full rounded-full transition-all duration-700", barColor)} style={{ width: `${progress}%` }} />
      </div>

      <ol className="flex items-start overflow-x-auto pb-2">
        {steps.map((step, index) => {
          const meta = NODE_META[step.nodeType];
          const Icon = meta.icon;
          return (
            <li key={step.id} className="flex items-start">
              <div className="flex min-w-[112px] flex-col items-center text-center">
                <div className={cn("relative flex h-14 w-14 items-center justify-center rounded-xl border-2 bg-card", RING[step.status] ?? "border-border")}>
                  <span className={cn("flex h-9 w-9 items-center justify-center rounded-lg", meta.tile)}>
                    <Icon className="h-5 w-5" aria-hidden />
                  </span>
                  <StepMarker status={step.status} />
                </div>
                <span className="mt-2 text-xs font-semibold">{meta.label}</span>
                <span className="mt-0.5 max-w-[110px] text-[11px] text-muted-foreground">{stepCaption(step)}</span>
                <span className="sr-only">Status: {step.status}</span>
              </div>
              {index < steps.length - 1 && (
                <div
                  aria-hidden
                  className={cn("mx-1 mt-7 h-0.5 w-8 shrink-0", step.status === "COMPLETED" ? "bg-emerald-500/60" : "bg-border")}
                />
              )}
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function StepMarker({ status }: { status: ExecutionStep["status"] }) {
  const base = "absolute -right-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full text-white";
  if (status === "COMPLETED") return <span className={cn(base, "bg-emerald-500")}><Check className="h-3 w-3" strokeWidth={3} /></span>;
  if (status === "FAILED" || status === "REJECTED") return <span className={cn(base, "bg-destructive")}><X className="h-3 w-3" strokeWidth={3} /></span>;
  if (status === "IN_PROGRESS" || status === "RETRYING") return <span className={cn(base, "bg-primary")}><Loader2 className="h-3 w-3 animate-spin" /></span>;
  if (status === "WAITING_APPROVAL") return <span className={cn(base, "bg-orange-500")}><Pause className="h-3 w-3" /></span>;
  return null;
}
