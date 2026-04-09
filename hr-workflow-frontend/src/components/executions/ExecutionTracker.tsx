"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import api from "@/lib/api";

interface NodeInfo {
  id: number;
  type: string;
  orderIndex: number;
}

interface NodeInstanceInfo {
  id: number;
  nodeId: number;
  nodeType: string;
  status: string;
  executionOrder: number;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  outputData: string | null;
}

interface Props {
  executionId: number;
  nodes: NodeInfo[];
  onComplete?: () => void;
}

const NODE_ICONS: Record<string, { icon: string; color: string }> = {
  DRIVE: { icon: "📁", color: "#3B82F6" },
  GPT: { icon: "🤖", color: "#8B5CF6" },
  EXCEL: { icon: "📊", color: "#10B981" },
  EMAIL: { icon: "📧", color: "#F59E0B" },
};

const STATUS_CONFIG: Record<
  string,
  { bg: string; border: string; text: string; glow: string }
> = {
  COMPLETED: {
    bg: "bg-emerald-500/15",
    border: "border-emerald-500/40",
    text: "text-emerald-400",
    glow: "shadow-emerald-500/20",
  },
  FAILED: {
    bg: "bg-red-500/15",
    border: "border-red-500/40",
    text: "text-red-400",
    glow: "shadow-red-500/20",
  },
  RUNNING: {
    bg: "bg-cyan-500/15",
    border: "border-cyan-400/50",
    text: "text-cyan-400",
    glow: "shadow-cyan-500/30",
  },
  IN_PROGRESS: {
    bg: "bg-cyan-500/15",
    border: "border-cyan-400/50",
    text: "text-cyan-400",
    glow: "shadow-cyan-500/30",
  },
  PENDING: {
    bg: "bg-gray-800/40",
    border: "border-gray-700/40",
    text: "text-gray-500",
    glow: "",
  },
  QUEUED: {
    bg: "bg-gray-800/40",
    border: "border-gray-700/40",
    text: "text-gray-500",
    glow: "",
  },
};

function formatDuration(ms: number | null) {
  if (!ms) return "";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

export default function ExecutionTracker({
  executionId,
  nodes,
  onComplete,
}: Props) {
  const [nodeInstances, setNodeInstances] = useState<NodeInstanceInfo[]>([]);
  const [overallStatus, setOverallStatus] = useState<string>("PENDING");
  const [totalDuration, setTotalDuration] = useState<number | null>(null);
  const completedRef = useRef(false);

  const fetchStatus = useCallback(async () => {
    try {
      const res = await api.get(`/executions/${executionId}/detail`);
      const data = res.data;

      const instances: NodeInstanceInfo[] = (data.nodeInstances || []).map(
        (ni: any) => ({
          id: ni.id,
          nodeId: ni.nodeId,
          nodeType: ni.nodeType,
          status: ni.status || "PENDING",
          executionOrder: ni.executionOrder,
          startedAt: ni.startedAt,
          finishedAt: ni.finishedAt,
          durationMs: ni.durationMs,
          errorMessage: ni.errorMessage,
          outputData: ni.outputData,
        }),
      );

      instances.sort((a, b) => a.executionOrder - b.executionOrder);
      setNodeInstances(instances);

      const wfStatus =
        data.workflowInstance?.status || data.status || "PENDING";
      setOverallStatus(wfStatus);
      setTotalDuration(
        data.workflowInstance?.durationMs || data.durationMs || null,
      );

      // Check if done
      if (
        (wfStatus === "COMPLETED" || wfStatus === "FAILED") &&
        !completedRef.current
      ) {
        completedRef.current = true;
        onComplete?.();
      }
    } catch (e) {
      console.error("Failed to poll execution status", e);
    }
  }, [executionId, onComplete]);

  useEffect(() => {
    // Initial fetch
    fetchStatus();

    // Poll every 2 seconds while running
    const interval = setInterval(() => {
      if (!completedRef.current) {
        fetchStatus();
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [fetchStatus]);

  // Build node status map from nodeInstances
  const nodeStatusMap = new Map<number, NodeInstanceInfo>();
  nodeInstances.forEach((ni) => {
    nodeStatusMap.set(ni.nodeId, ni);
  });

  const isDone = overallStatus === "COMPLETED" || overallStatus === "FAILED";
  const isRunning = !isDone && overallStatus !== "PENDING";

  // Calculate progress
  const completedCount = nodeInstances.filter(
    (ni) => ni.status === "COMPLETED",
  ).length;
  const totalNodes = nodes.length || nodeInstances.length || 1;
  const progressPct = isDone
    ? 100
    : Math.round((completedCount / totalNodes) * 100);

  return (
    <div className="space-y-6">
      {/* Header bar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          {isRunning && (
            <div className="w-5 h-5 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
          )}
          {overallStatus === "COMPLETED" && (
            <span className="text-emerald-400 text-lg">●</span>
          )}
          {overallStatus === "FAILED" && (
            <span className="text-red-400 text-lg">●</span>
          )}
          <h3 className="text-white font-semibold text-base">
            {isDone
              ? "Execution Complete"
              : isRunning
                ? "Executing..."
                : "Preparing..."}
          </h3>
        </div>
        <div className="flex items-center gap-4">
          {totalDuration && (
            <span className="text-gray-400 text-sm font-mono">
              Total: {formatDuration(totalDuration)}
            </span>
          )}
          <span
            className={`text-xs px-2.5 py-1 rounded-full font-semibold ${
              overallStatus === "COMPLETED"
                ? "bg-emerald-500/15 text-emerald-400"
                : overallStatus === "FAILED"
                  ? "bg-red-500/15 text-red-400"
                  : "bg-cyan-500/15 text-cyan-400"
            }`}
          >
            {overallStatus}
          </span>
        </div>
      </div>

      {/* Progress bar */}
      <div className="w-full h-1.5 bg-gray-700/50 rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-700 ease-out ${
            overallStatus === "FAILED"
              ? "bg-red-500"
              : overallStatus === "COMPLETED"
                ? "bg-emerald-500"
                : "bg-cyan-400"
          }`}
          style={{ width: `${progressPct}%` }}
        />
      </div>

      {/* ─── Horizontal Pipeline ─────────────────────────────── */}
      <div className="overflow-x-auto pb-2">
        <div className="flex items-start gap-0 min-w-max">
          {(nodes.length > 0 ? nodes : nodeInstances)
            .sort((a, b) => {
              const aOrder =
                "orderIndex" in a
                  ? a.orderIndex
                  : (a as NodeInstanceInfo).executionOrder;
              const bOrder =
                "orderIndex" in b
                  ? b.orderIndex
                  : (b as NodeInstanceInfo).executionOrder;
              return aOrder - bOrder;
            })
            .map((node, idx, arr) => {
              const ni =
                nodeStatusMap.get(
                  "id" in node && "nodeId" in node
                    ? (node as any).nodeId
                    : node.id,
                ) || nodeStatusMap.get(node.id);

              const nodeType =
                (node as any).type || (node as any).nodeType || "UNKNOWN";
              const status = ni?.status || "QUEUED";
              const style = STATUS_CONFIG[status] || STATUS_CONFIG.PENDING;
              const iconInfo = NODE_ICONS[nodeType] || {
                icon: "⚙️",
                color: "#6B7280",
              };
              const isActive = status === "RUNNING" || status === "IN_PROGRESS";
              const isLast = idx === arr.length - 1;

              return (
                <div key={node.id} className="flex items-start">
                  {/* Node card */}
                  <div
                    className="flex flex-col items-center"
                    style={{ minWidth: 120 }}
                  >
                    {/* Icon circle */}
                    <div
                      className={`relative w-14 h-14 rounded-xl flex items-center justify-center border-2 transition-all duration-500 ${style.bg} ${style.border} ${isActive ? `shadow-lg ${style.glow}` : ""}`}
                    >
                      <span className="text-xl">{iconInfo.icon}</span>
                      {isActive && (
                        <div className="absolute -top-1 -right-1 w-3 h-3">
                          <span className="absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75 animate-ping" />
                          <span className="relative inline-flex rounded-full h-3 w-3 bg-cyan-400" />
                        </div>
                      )}
                      {status === "COMPLETED" && (
                        <div className="absolute -top-1 -right-1 w-5 h-5 bg-emerald-500 rounded-full flex items-center justify-center">
                          <svg
                            className="w-3 h-3 text-white"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={3}
                              d="M5 13l4 4L19 7"
                            />
                          </svg>
                        </div>
                      )}
                      {status === "FAILED" && (
                        <div className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 rounded-full flex items-center justify-center">
                          <svg
                            className="w-3 h-3 text-white"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={3}
                              d="M6 18L18 6M6 6l12 12"
                            />
                          </svg>
                        </div>
                      )}
                    </div>

                    {/* Node label */}
                    <span
                      className={`mt-2 text-xs font-semibold tracking-wide ${style.text}`}
                    >
                      {nodeType}
                    </span>

                    {/* Status + duration */}
                    <span className="text-[10px] text-gray-500 mt-0.5">
                      {isActive
                        ? "Running..."
                        : status === "COMPLETED"
                          ? formatDuration(ni?.durationMs ?? null) || "Done"
                          : status === "FAILED"
                            ? "Failed"
                            : "Queued"}
                    </span>
                  </div>

                  {/* Connector arrow */}
                  {!isLast && (
                    <div className="flex items-center mt-5 mx-1">
                      <div
                        className={`h-0.5 w-8 transition-colors duration-500 ${
                          status === "COMPLETED"
                            ? "bg-emerald-500/60"
                            : isActive
                              ? "bg-cyan-400/40"
                              : "bg-gray-700/50"
                        }`}
                      />
                      <svg
                        className={`w-3 h-3 -ml-0.5 transition-colors duration-500 ${
                          status === "COMPLETED"
                            ? "text-emerald-500/60"
                            : isActive
                              ? "text-cyan-400/40"
                              : "text-gray-700/50"
                        }`}
                        fill="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path d="M8 5v14l11-7z" />
                      </svg>
                    </div>
                  )}
                </div>
              );
            })}
        </div>
      </div>

      {/* Completion message */}
      {isDone && (
        <div
          className={`flex items-center gap-3 px-4 py-3 rounded-xl border ${
            overallStatus === "COMPLETED"
              ? "bg-emerald-500/10 border-emerald-500/20"
              : "bg-red-500/10 border-red-500/20"
          }`}
        >
          <span className="text-lg">
            {overallStatus === "COMPLETED" ? "✅" : "❌"}
          </span>
          <div>
            <p
              className={`text-sm font-medium ${
                overallStatus === "COMPLETED"
                  ? "text-emerald-400"
                  : "text-red-400"
              }`}
            >
              {overallStatus === "COMPLETED"
                ? "All steps completed successfully"
                : "Execution failed"}
            </p>
            {totalDuration && (
              <p className="text-xs text-gray-500">
                Total time: {formatDuration(totalDuration)}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
