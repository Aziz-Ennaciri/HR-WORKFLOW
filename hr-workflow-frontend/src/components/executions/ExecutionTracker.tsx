"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import api from "@/lib/api";

// ─── Node type config ───────────────────────────────────────────────
const NODE_META: Record<
  string,
  { icon: string; color: string; label: string }
> = {
  DRIVE: { icon: "📁", color: "#3b82f6", label: "Drive" },
  GPT: { icon: "🤖", color: "#10b981", label: "AI / GPT" },
  EXCEL: { icon: "📊", color: "#22c55e", label: "Excel" },
  EMAIL: { icon: "📧", color: "#ef4444", label: "Email" },
};

const STATUS_CONFIG: Record<
  string,
  { bg: string; text: string; border: string; pulse?: boolean }
> = {
  COMPLETED: {
    bg: "bg-emerald-500/20",
    text: "text-emerald-400",
    border: "border-emerald-500/40",
  },
  IN_PROGRESS: {
    bg: "bg-cyan-500/20",
    text: "text-cyan-400",
    border: "border-cyan-500/40",
    pulse: true,
  },
  PENDING: {
    bg: "bg-slate-500/20",
    text: "text-slate-400",
    border: "border-slate-500/30",
  },
  FAILED: {
    bg: "bg-red-500/20",
    text: "text-red-400",
    border: "border-red-500/40",
  },
  REJECTED: {
    bg: "bg-orange-500/20",
    text: "text-orange-400",
    border: "border-orange-500/40",
  },
  RETRYING: {
    bg: "bg-amber-500/20",
    text: "text-amber-400",
    border: "border-amber-500/40",
    pulse: true,
  },
};

// ─── Types ──────────────────────────────────────────────────────────
interface NodeInstanceData {
  id: number;
  nodeType: string;
  nodeId: number;
  status: string;
  executionOrder: number;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  outputData: string | null;
  node?: { type: string; id: number };
}

interface ExecutionData {
  id: number;
  status: string;
  workflowId: number;
  workflowName: string;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  triggeredByEmail: string;
  nodeInstances: NodeInstanceData[];
}

interface Props {
  executionId: number;
  nodes: { id: number; type: string; orderIndex: number }[];
  onComplete?: (data: ExecutionData) => void;
}

// ─── Main Component ─────────────────────────────────────────────────
export default function ExecutionTracker({
  executionId,
  nodes,
  onComplete,
}: Props) {
  const [execution, setExecution] = useState<ExecutionData | null>(null);
  const [expandedNode, setExpandedNode] = useState<number | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const pollRef = useRef<NodeJS.Timeout | null>(null);
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const startTime = useRef(Date.now());

  const fetchExecution = useCallback(async () => {
    try {
      const res = await api.get(`/executions/${executionId}`);
      const data = res.data?.workflowInstance
        ? {
            ...res.data.workflowInstance,
            nodeInstances: res.data.nodeInstances || [],
          }
        : res.data;
      setExecution(data);

      if (data.status === "COMPLETED" || data.status === "FAILED") {
        if (pollRef.current) clearInterval(pollRef.current);
        if (timerRef.current) clearInterval(timerRef.current);
        onComplete?.(data);
      }
    } catch (e) {
      console.error("Failed to fetch execution", e);
    }
  }, [executionId, onComplete]);

  useEffect(() => {
    startTime.current = Date.now();
    fetchExecution();
    pollRef.current = setInterval(fetchExecution, 2000);
    timerRef.current = setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - startTime.current) / 1000));
    }, 1000);

    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [fetchExecution]);

  // ─── Helpers ────────────────────────────────────────────────────
  const getNodeInstance = (nodeId: number): NodeInstanceData | undefined => {
    return execution?.nodeInstances?.find(
      (ni) => (ni.node?.id || ni.nodeId) === nodeId,
    );
  };

  const getNodeStatus = (nodeId: number): string => {
    const ni = getNodeInstance(nodeId);
    return ni?.status || "WAITING";
  };

  const formatDuration = (ms: number | null): string => {
    if (!ms) return "—";
    if (ms < 1000) return `${ms}ms`;
    const secs = (ms / 1000).toFixed(1);
    return `${secs}s`;
  };

  const formatTime = (iso: string | null): string => {
    if (!iso) return "—";
    return new Date(iso).toLocaleTimeString();
  };

  const formatElapsed = (s: number): string => {
    const min = Math.floor(s / 60);
    const sec = s % 60;
    return min > 0 ? `${min}m ${sec}s` : `${sec}s`;
  };

  const overallStatus = execution?.status || "IN_PROGRESS";
  const isRunning =
    overallStatus === "IN_PROGRESS" || overallStatus === "PENDING";
  const isDone = overallStatus === "COMPLETED";
  const isFailed = overallStatus === "FAILED";

  const completedCount = nodes.filter(
    (n) => getNodeStatus(n.id) === "COMPLETED",
  ).length;
  const progressPct =
    nodes.length > 0 ? (completedCount / nodes.length) * 100 : 0;

  return (
    <div className="w-full space-y-4">
      {/* ─── Header ──────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div
            className={`w-3 h-3 rounded-full ${
              isDone
                ? "bg-emerald-400"
                : isFailed
                  ? "bg-red-400"
                  : "bg-cyan-400 animate-pulse"
            }`}
          />
          <h3 className="text-white font-semibold text-lg">
            {isDone
              ? "Execution Complete"
              : isFailed
                ? "Execution Failed"
                : "Executing..."}
          </h3>
        </div>
        <div className="flex items-center gap-4 text-sm">
          {isRunning && (
            <span className="text-cyan-400 font-mono tabular-nums">
              ⏱ {formatElapsed(elapsedSeconds)}
            </span>
          )}
          {execution?.durationMs && (
            <span className="text-slate-400 font-mono">
              Total: {formatDuration(execution.durationMs)}
            </span>
          )}
          <span
            className={`px-2.5 py-1 rounded-full text-xs font-medium ${
              STATUS_CONFIG[overallStatus]?.bg || "bg-slate-500/20"
            } ${STATUS_CONFIG[overallStatus]?.text || "text-slate-400"}`}
          >
            {overallStatus}
          </span>
        </div>
      </div>

      {/* ─── Progress Bar ────────────────────────────────────────── */}
      <div className="relative h-1.5 bg-slate-700/50 rounded-full overflow-hidden">
        <div
          className={`absolute inset-y-0 left-0 rounded-full transition-all duration-700 ease-out ${
            isFailed ? "bg-red-500" : isDone ? "bg-emerald-500" : "bg-cyan-500"
          }`}
          style={{ width: `${isDone ? 100 : progressPct}%` }}
        />
        {isRunning && (
          <div
            className="absolute inset-y-0 rounded-full bg-cyan-400/30 animate-pulse"
            style={{ left: `${progressPct}%`, width: "20%" }}
          />
        )}
      </div>

      {/* ─── Node Steps ──────────────────────────────────────────── */}
      <div className="space-y-2">
        {nodes
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .map((node, idx) => {
            const status = getNodeStatus(node.id);
            const ni = getNodeInstance(node.id);
            const meta = NODE_META[node.type] || {
              icon: "⚙️",
              color: "#6b7280",
              label: node.type,
            };
            const cfg = STATUS_CONFIG[status] || STATUS_CONFIG.PENDING;
            const isExpanded = expandedNode === node.id;
            const isActive = status === "IN_PROGRESS";
            const hasOutput = ni?.outputData;
            const hasError = ni?.errorMessage;

            return (
              <div key={node.id} className="relative">
                {/* Connector line */}
                {idx < nodes.length - 1 && (
                  <div
                    className={`absolute left-[23px] top-[48px] w-0.5 h-[calc(100%-32px)] transition-colors duration-500 ${
                      status === "COMPLETED"
                        ? "bg-emerald-500/40"
                        : "bg-slate-700/50"
                    }`}
                  />
                )}

                <div
                  onClick={() =>
                    (hasOutput || hasError) &&
                    setExpandedNode(isExpanded ? null : node.id)
                  }
                  className={`relative rounded-xl border transition-all duration-300 ${
                    isActive
                      ? "border-cyan-500/50 bg-cyan-500/5 shadow-lg shadow-cyan-500/10"
                      : `${cfg.border} bg-slate-800/40 hover:bg-slate-800/60`
                  } ${hasOutput || hasError ? "cursor-pointer" : ""}`}
                >
                  <div className="flex items-center gap-4 px-4 py-3">
                    {/* Step icon */}
                    <div
                      className={`relative flex-shrink-0 w-[46px] h-[46px] rounded-xl flex items-center justify-center text-xl border ${cfg.border} ${cfg.bg}`}
                    >
                      {meta.icon}
                      {isActive && (
                        <div className="absolute inset-0 rounded-xl border-2 border-cyan-400/60 animate-ping" />
                      )}
                    </div>

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-white font-medium text-sm">
                          {meta.label}
                        </span>
                        <span
                          className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${cfg.bg} ${cfg.text}`}
                        >
                          {status === "WAITING" ? "Waiting" : status}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 mt-0.5 text-xs text-slate-500">
                        {ni?.startedAt && (
                          <span>Started {formatTime(ni.startedAt)}</span>
                        )}
                        {ni?.durationMs && (
                          <span>• {formatDuration(ni.durationMs)}</span>
                        )}
                        {isActive && (
                          <span className="text-cyan-400">Processing...</span>
                        )}
                        {status === "WAITING" && <span>Queued</span>}
                      </div>
                    </div>

                    {/* Status indicator */}
                    <div className="flex-shrink-0">
                      {status === "COMPLETED" && (
                        <svg
                          className="w-5 h-5 text-emerald-400"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2.5}
                            d="M5 13l4 4L19 7"
                          />
                        </svg>
                      )}
                      {status === "FAILED" && (
                        <svg
                          className="w-5 h-5 text-red-400"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2.5}
                            d="M6 18L18 6M6 6l12 12"
                          />
                        </svg>
                      )}
                      {isActive && (
                        <div className="w-5 h-5 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
                      )}
                      {status === "WAITING" && (
                        <div className="w-5 h-5 rounded-full border-2 border-slate-600" />
                      )}
                      {(hasOutput || hasError) && !isActive && (
                        <svg
                          className={`w-4 h-4 text-slate-500 transition-transform ${isExpanded ? "rotate-180" : ""}`}
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M19 9l-7 7-7-7"
                          />
                        </svg>
                      )}
                    </div>
                  </div>

                  {/* ─── Expanded detail panel ──────────────────── */}
                  {isExpanded && (
                    <div className="border-t border-slate-700/50 px-4 py-3 space-y-3">
                      {/* Error */}
                      {hasError && (
                        <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-3">
                          <p className="text-red-400 text-xs font-semibold mb-1">
                            Error
                          </p>
                          <pre className="text-red-300/80 text-xs overflow-x-auto whitespace-pre-wrap">
                            {ni.errorMessage}
                          </pre>
                        </div>
                      )}

                      {/* Output summary */}
                      {hasOutput &&
                        (() => {
                          try {
                            const parsed = JSON.parse(ni.outputData!);

                            // GPT node — show candidates
                            if (parsed.analysis) {
                              try {
                                const candidates = JSON.parse(parsed.analysis);
                                if (Array.isArray(candidates)) {
                                  return (
                                    <div className="space-y-2">
                                      <p className="text-slate-400 text-xs font-medium">
                                        {candidates.length} candidate
                                        {candidates.length !== 1 ? "s" : ""}{" "}
                                        found
                                      </p>
                                      {candidates.map((c: any, i: number) => (
                                        <div
                                          key={i}
                                          className="flex items-center justify-between bg-slate-700/30 rounded-lg px-3 py-2"
                                        >
                                          <div>
                                            <span className="text-white text-sm font-medium">
                                              {c.name}
                                            </span>
                                            <span className="text-slate-500 text-xs ml-2">
                                              {c.email}
                                            </span>
                                          </div>
                                          <div className="flex items-center gap-3">
                                            <span className="text-slate-400 text-xs">
                                              {c.experience} yrs
                                            </span>
                                            <span className="text-emerald-400 text-sm font-bold">
                                              {c.score}/10
                                            </span>
                                          </div>
                                        </div>
                                      ))}
                                      {parsed.model && (
                                        <div className="flex gap-3 text-[11px] text-slate-500 mt-1">
                                          <span>Model: {parsed.model}</span>
                                          {parsed.tokensUsed && (
                                            <span>
                                              • {parsed.tokensUsed} tokens
                                            </span>
                                          )}
                                        </div>
                                      )}
                                    </div>
                                  );
                                }
                              } catch {}
                              return (
                                <pre className="text-slate-300 text-xs overflow-x-auto whitespace-pre-wrap max-h-40">
                                  {parsed.analysis}
                                </pre>
                              );
                            }

                            // Excel node
                            if (parsed.filePath) {
                              return (
                                <div className="flex items-center gap-2 bg-slate-700/30 rounded-lg px-3 py-2">
                                  <span className="text-xl">📄</span>
                                  <div>
                                    <p className="text-white text-sm">
                                      {parsed.fileName || "Report"}
                                    </p>
                                    <p className="text-slate-500 text-xs">
                                      {parsed.fileSize
                                        ? `${(parsed.fileSize / 1024).toFixed(1)} KB`
                                        : ""}
                                    </p>
                                  </div>
                                </div>
                              );
                            }

                            // Email node
                            if (parsed.sentTo || parsed.emailSent) {
                              return (
                                <div className="flex items-center gap-2 bg-emerald-500/10 rounded-lg px-3 py-2">
                                  <span className="text-emerald-400 text-sm">
                                    ✓ Email sent
                                  </span>
                                  {parsed.sentTo && (
                                    <span className="text-slate-500 text-xs">
                                      to {parsed.sentTo}
                                    </span>
                                  )}
                                </div>
                              );
                            }

                            // Drive node — show CV count
                            if (parsed.totalCVs || parsed.cvData) {
                              const total =
                                parsed.totalCVs || parsed.cvData?.totalCVs;
                              return (
                                <div className="flex items-center gap-2 bg-blue-500/10 rounded-lg px-3 py-2">
                                  <span className="text-blue-400 text-sm">
                                    📂 {total} CVs loaded
                                  </span>
                                </div>
                              );
                            }

                            // Generic
                            return (
                              <pre className="text-slate-300 text-xs overflow-x-auto whitespace-pre-wrap max-h-40">
                                {JSON.stringify(parsed, null, 2).substring(
                                  0,
                                  500,
                                )}
                              </pre>
                            );
                          } catch {
                            return (
                              <pre className="text-slate-300 text-xs overflow-x-auto whitespace-pre-wrap max-h-40">
                                {ni.outputData?.substring(0, 500)}
                              </pre>
                            );
                          }
                        })()}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
      </div>

      {/* ─── Error Banner ────────────────────────────────────────── */}
      {isFailed && execution?.errorMessage && (
        <div className="bg-red-500/10 border border-red-500/30 rounded-xl p-4">
          <div className="flex items-start gap-3">
            <span className="text-red-400 text-lg mt-0.5">⚠️</span>
            <div>
              <p className="text-red-400 font-semibold text-sm">
                Workflow Failed
              </p>
              <p className="text-red-300/70 text-xs mt-1">
                {execution.errorMessage}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ─── Success Banner ──────────────────────────────────────── */}
      {isDone && (
        <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-4">
          <div className="flex items-center gap-3">
            <span className="text-emerald-400 text-lg">✅</span>
            <div>
              <p className="text-emerald-400 font-semibold text-sm">
                All steps completed successfully
              </p>
              {execution?.durationMs && (
                <p className="text-emerald-300/60 text-xs mt-0.5">
                  Total time: {formatDuration(execution.durationMs)}
                </p>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
