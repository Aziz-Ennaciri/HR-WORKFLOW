"use client";

import { useState } from "react";
import { ChevronDown, Download, FileSpreadsheet, MailCheck, RotateCcw } from "lucide-react";
import { toast } from "sonner";
import { CandidateList, GptOutput } from "@/components/executions/gpt-output";
import { StatusBadge } from "@/components/executions/status-badge";
import { Button } from "@/components/ui/button";
import { getErrorMessage } from "@/lib/errors";
import { formatDuration, formatFileSize } from "@/lib/format";
import { NODE_META } from "@/lib/node-meta";
import { extractCandidates, parseJson } from "@/lib/step-output";
import { cn } from "@/lib/utils";
import { executionsApi } from "@/services/executions";
import type { ExecutionStep } from "@/types";

interface StepOutput {
  analysis?: string;
  model?: string;
  tokensUsed?: number;
  fileSize?: number;
  webFileUrl?: string;
  sheetName?: string;
  recipient?: string;
  fileData?: { totalFiles?: number; folder?: string };
}

export function StepResultCard({ step, onRetried }: { step: ExecutionStep; onRetried: () => void }) {
  const [expanded, setExpanded] = useState(step.status === "FAILED");
  const [retrying, setRetrying] = useState(false);
  const meta = NODE_META[step.nodeType];
  const Icon = meta.icon;
  const output = parseJson<StepOutput>(step.outputData);
  const candidates = output?.analysis ? extractCandidates(output.analysis) : [];
  const panelId = `step-${step.id}-details`;

  const retry = async () => {
    setRetrying(true);
    try {
      await executionsApi.retryStep(step.id);
      toast.success(`Retrying ${meta.label}`);
      onRetried();
    } catch (error) {
      toast.error(getErrorMessage(error, "Retry failed"));
    } finally {
      setRetrying(false);
    }
  };

  const download = async () => {
    if (!output?.webFileUrl) return;
    try {
      const blob = await executionsApi.downloadReport(output.webFileUrl);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${output.sheetName || `report-${step.id}`}.xlsx`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast.error(getErrorMessage(error, "Download failed"));
    }
  };

  return (
    <div className={cn("rounded-xl border bg-card", step.status === "FAILED" && "border-destructive/40")}>
      <div className="flex items-center gap-3 px-4 py-3">
        <span className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-lg", meta.tile)}>
          <Icon className="h-[18px] w-[18px]" aria-hidden />
        </span>
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
          aria-controls={panelId}
          className="flex min-w-0 flex-1 items-center gap-3 rounded-md text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium">
                {step.executionOrder}. {meta.label}
              </span>
              <StatusBadge status={step.status} />
              {output?.model && <span className="text-[11px] text-muted-foreground">{output.model}</span>}
            </div>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {[
                formatDuration(step.durationMs),
                step.retryCount > 0 && `${step.retryCount} retr${step.retryCount === 1 ? "y" : "ies"}`,
                candidates.length > 0 && `${candidates.length} candidates`,
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </div>
          <ChevronDown className={cn("h-4 w-4 shrink-0 text-muted-foreground transition-transform", expanded && "rotate-180")} aria-hidden />
        </button>
        {step.status === "FAILED" && (
          <Button size="sm" variant="outline" loading={retrying} onClick={retry}>
            {!retrying && <RotateCcw />} Retry
          </Button>
        )}
      </div>

      {expanded && (
        <div id={panelId} className="space-y-3 border-t px-4 py-4">
          {step.errorMessage && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-3">
              <p className="mb-1 text-xs font-semibold text-destructive">Error</p>
              <pre className="whitespace-pre-wrap break-words text-xs text-destructive/90">{step.errorMessage}</pre>
            </div>
          )}

          {step.actor && (
            <p className="text-xs text-muted-foreground">
              Decided by <span className="font-medium text-foreground">{step.actor}</span>
              {step.comment && <>: “{step.comment}”</>}
            </p>
          )}

          {candidates.length > 0 ? (
            <>
              <CandidateList candidates={candidates} />
              {output?.tokensUsed ? (
                <p className="text-[11px] text-muted-foreground">
                  {output.model} · {output.tokensUsed} tokens
                </p>
              ) : null}
            </>
          ) : (
            output?.analysis && (
              <div className="max-h-72 overflow-y-auto rounded-lg border bg-muted/40 p-3">
                <GptOutput text={output.analysis} />
              </div>
            )
          )}

          {step.nodeType === "EXCEL" && output?.webFileUrl && (
            <div className="flex items-center justify-between gap-3 rounded-lg border bg-muted/40 px-4 py-3">
              <div className="flex items-center gap-3">
                <FileSpreadsheet className="h-6 w-6 text-emerald-600" aria-hidden />
                <div>
                  <p className="text-sm font-medium">{output.sheetName || "Report"}.xlsx</p>
                  <p className="text-xs text-muted-foreground">{formatFileSize(output.fileSize)}</p>
                </div>
              </div>
              <Button size="sm" onClick={download}>
                <Download /> Download
              </Button>
            </div>
          )}

          {step.nodeType === "EMAIL" && output && step.status === "COMPLETED" && (
            <div className="flex items-center gap-3 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm">
              <MailCheck className="h-5 w-5 text-emerald-600" aria-hidden />
              <div>
                <p className="font-medium text-emerald-700 dark:text-emerald-300">Email sent</p>
                {output.recipient && <p className="text-xs text-muted-foreground">To: {output.recipient}</p>}
              </div>
            </div>
          )}

          {step.nodeType === "DRIVE" && output?.fileData && (
            <p className="text-sm text-muted-foreground">
              {output.fileData.totalFiles ?? 0} file(s) read
              {output.fileData.folder && <> from <span className="font-mono text-foreground">{output.fileData.folder}</span></>}
            </p>
          )}

          {!output && step.outputData && (
            <pre className="max-h-40 overflow-auto whitespace-pre-wrap rounded-lg border bg-muted/40 p-3 text-xs text-muted-foreground">
              {step.outputData.slice(0, 2000)}
            </pre>
          )}

          {!step.outputData && !step.errorMessage && !step.actor && (
            <p className="text-xs text-muted-foreground">No output yet.</p>
          )}
        </div>
      )}
    </div>
  );
}
