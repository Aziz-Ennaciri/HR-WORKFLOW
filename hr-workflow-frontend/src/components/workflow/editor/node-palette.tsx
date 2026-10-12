"use client";

import { NODE_META } from "@/lib/node-meta";
import { cn } from "@/lib/utils";
import { NODE_TYPES, type NodeType } from "@/types";

export const DRAG_MIME = "application/x-workflow-node";

/** Step types to add: click to append, or drag onto the canvas to place. */
export function NodePalette({ onAdd }: { onAdd: (type: NodeType) => void }) {
  return (
    <div role="toolbar" aria-label="Add a step" className="flex items-center gap-2 overflow-x-auto border-b bg-card px-4 py-2">
      <span className="shrink-0 text-xs font-medium text-muted-foreground">Add step</span>
      {NODE_TYPES.map((type) => {
        const meta = NODE_META[type];
        const Icon = meta.icon;
        return (
          <button
            key={type}
            type="button"
            draggable
            onDragStart={(event) => {
              event.dataTransfer.setData(DRAG_MIME, type);
              event.dataTransfer.effectAllowed = "move";
            }}
            onClick={() => onAdd(type)}
            title={meta.description}
            className="flex shrink-0 cursor-grab items-center gap-2 rounded-lg border bg-background px-3 py-1.5 text-xs font-medium transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:cursor-grabbing"
          >
            <span className={cn("flex h-5 w-5 items-center justify-center rounded", meta.tile)}>
              <Icon className="h-3.5 w-3.5" aria-hidden />
            </span>
            {meta.label}
          </button>
        );
      })}
    </div>
  );
}
