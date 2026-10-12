"use client";

import type { Node } from "reactflow";
import { AlertCircle, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { NODE_META } from "@/lib/node-meta";
import { cn } from "@/lib/utils";
import type { EditorNodeData, NodeConfigs } from "@/types";
import {
  ApprovalConfigForm,
  DriveConfigForm,
  EmailConfigForm,
  ExcelConfigForm,
  GptConfigForm,
} from "./node-config-forms";

interface NodeConfigPanelProps {
  node: Node<EditorNodeData>;
  errors: string[];
  onChange: (config: EditorNodeData["config"]) => void;
  onDelete: () => void;
  onClose: () => void;
  className?: string;
}

/** Settings of the selected step. Changes apply to the canvas immediately; Save persists them. */
export function NodeConfigPanel({ node, errors, onChange, onDelete, onClose, className }: NodeConfigPanelProps) {
  const { data } = node;
  const meta = NODE_META[data.type];
  const Icon = meta.icon;

  const form = (() => {
    switch (data.type) {
      case "EMAIL":
        return <EmailConfigForm nodeId={node.id} config={data.config} onChange={onChange} />;
      case "GPT":
        return <GptConfigForm nodeId={node.id} config={data.config} onChange={onChange} />;
      case "DRIVE":
        return <DriveConfigForm nodeId={node.id} config={data.config} onChange={onChange} />;
      case "EXCEL":
        return <ExcelConfigForm nodeId={node.id} config={data.config} onChange={(c: NodeConfigs["EXCEL"]) => onChange(c)} />;
      case "APPROVAL":
        return <ApprovalConfigForm nodeId={node.id} config={data.config} onChange={onChange} />;
    }
  })();

  return (
    <aside aria-label={`${meta.label} settings`} className={cn("flex h-full flex-col bg-card", className)}>
      <div className="flex items-start gap-3 border-b px-5 py-4">
        <span className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-lg", meta.tile)}>
          <Icon className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-base font-semibold">{meta.label}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{meta.description}</p>
        </div>
        <Button variant="ghost" size="icon" aria-label="Close settings" onClick={onClose}>
          <X />
        </Button>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4">
        {errors.length > 0 && (
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
            <p className="mb-1 flex items-center gap-1.5 font-semibold">
              <AlertCircle className="h-3.5 w-3.5" aria-hidden /> Needed before activating
            </p>
            <ul className="list-inside list-disc space-y-0.5">
              {errors.map((error) => (
                <li key={error}>{error}</li>
              ))}
            </ul>
          </div>
        )}
        {form}
      </div>

      <div className="border-t px-5 py-3">
        <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive" onClick={onDelete}>
          <Trash2 /> Remove step
        </Button>
      </div>
    </aside>
  );
}
