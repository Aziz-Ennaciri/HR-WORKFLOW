import { memo } from "react";
import { Handle, Position, type NodeProps } from "reactflow";
import { AlertCircle } from "lucide-react";
import { NODE_META } from "@/lib/node-meta";
import { cn } from "@/lib/utils";
import type { EditorNodeData } from "@/types";

/** One-line summary of a step's key setting, shown on the canvas. */
function summary(data: EditorNodeData): string | undefined {
  switch (data.type) {
    case "EMAIL":
      return data.config.to ? `To ${data.config.to}` : undefined;
    case "GPT":
      return [data.config.provider ?? "ollama", data.config.model].filter(Boolean).join(" · ");
    case "DRIVE":
      return data.config.folderId ? `${data.config.action === "write" ? "Save to" : "Read"} ${data.config.folderId}/` : undefined;
    case "EXCEL":
      return data.config.fileName ? `${data.config.operation === "READ" ? "Read" : "Report"}: ${data.config.fileName}` : undefined;
    case "APPROVAL":
      return data.config.approverEmail ? `Approver: ${data.config.approverEmail}` : undefined;
  }
}

function WorkflowNodeComponent({ data, selected }: NodeProps<EditorNodeData>) {
  const meta = NODE_META[data.type];
  const Icon = meta.icon;
  const detail = summary(data);

  return (
    <div
      className={cn(
        "w-[220px] rounded-xl border-2 bg-card shadow-sm transition-shadow",
        selected ? "shadow-lg" : "hover:shadow-md",
        data.invalid ? "border-destructive/70" : selected ? "border-primary" : "border-border",
      )}
    >
      <div className="flex items-center gap-3 px-3.5 py-3">
        <span className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-lg", meta.tile)}>
          <Icon className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[13px] font-semibold leading-tight">{meta.label}</p>
          <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{detail || "Not configured"}</p>
        </div>
        {data.invalid && <AlertCircle className="h-4 w-4 shrink-0 text-destructive" aria-label="Needs settings" />}
      </div>
      <Handle type="target" position={Position.Left} className="!h-3 !w-3 !border-2 !border-card !bg-muted-foreground" />
      <Handle type="source" position={Position.Right} className="!h-3 !w-3 !border-2 !border-card !bg-muted-foreground" />
    </div>
  );
}

export const WorkflowNode = memo(WorkflowNodeComponent);
