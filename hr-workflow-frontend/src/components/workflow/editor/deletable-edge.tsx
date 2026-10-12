import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, useReactFlow, type EdgeProps } from "reactflow";
import { X } from "lucide-react";

/** A connection with a remove button at its midpoint (visible on hover or keyboard focus). */
export function DeletableEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style, markerEnd }: EdgeProps) {
  const { setEdges } = useReactFlow();
  const [path, labelX, labelY] = getSmoothStepPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });

  return (
    <>
      <BaseEdge path={path} markerEnd={markerEnd} style={style} />
      <EdgeLabelRenderer>
        <div
          style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
          className="nodrag nopan group pointer-events-auto absolute"
        >
          <button
            type="button"
            onClick={() => setEdges((edges) => edges.filter((edge) => edge.id !== id))}
            aria-label="Remove connection"
            className="flex h-5 w-5 items-center justify-center rounded-full border bg-card text-muted-foreground opacity-0 shadow-sm transition-opacity hover:border-destructive hover:text-destructive focus-visible:opacity-100 group-hover:opacity-100"
          >
            <X className="h-3 w-3" />
          </button>
        </div>
      </EdgeLabelRenderer>
    </>
  );
}
