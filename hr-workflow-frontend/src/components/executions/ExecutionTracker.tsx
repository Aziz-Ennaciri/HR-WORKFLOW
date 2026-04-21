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
  inputData: string | null;
  actor: string | null;
  comment: string | null;
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
  APPROVAL: { icon: "✋", color: "#F97316" },
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
  REJECTED: {
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
  WAITING_APPROVAL: {
    bg: "bg-orange-500/15",
    border: "border-orange-400/50",
    text: "text-orange-400",
    glow: "shadow-orange-500/30",
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
  const [approvalComment, setApprovalComment] = useState("");
  const [submittingApproval, setSubmittingApproval] = useState(false);
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
          inputData: ni.inputData,
          actor: ni.actor,
          comment: ni.comment,
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
    fetchStatus();
    const interval = setInterval(() => {
      if (!completedRef.current) {
        fetchStatus();
      }
    }, 2000);
    return () => clearInterval(interval);
  }, [fetchStatus]);

  // Build node status map
  const nodeStatusMap = new Map<number, NodeInstanceInfo>();
  nodeInstances.forEach((ni) => {
    nodeStatusMap.set(ni.nodeId, ni);
  });

  const isDone = overallStatus === "COMPLETED" || overallStatus === "FAILED";
  const isPaused = overallStatus === "PAUSED";
  const isRunning = !isDone && !isPaused && overallStatus !== "PENDING";

  const completedCount = nodeInstances.filter(
    (ni) => ni.status === "COMPLETED",
  ).length;
  const totalNodes = nodes.length || nodeInstances.length || 1;
  const progressPct = isDone
    ? 100
    : Math.round((completedCount / totalNodes) * 100);

  // Find the approval node waiting for action
  const waitingApprovalNode = nodeInstances.find(
    (ni) => ni.status === "WAITING_APPROVAL",
  );

  // ─── Approval handlers ──────────────────────────────────────────
  const handleApprove = async () => {
    if (!waitingApprovalNode) return;
    setSubmittingApproval(true);
    try {
      const user = JSON.parse(localStorage.getItem("user") || "{}");
      await api.post(`/executions/nodes/${waitingApprovalNode.id}/approve`, {
        actor: user.email || user.firstName || "Unknown",
        comment: approvalComment || "Approved",
      });
      setApprovalComment("");
      completedRef.current = false; // Allow polling to resume
      fetchStatus();
    } catch (e) {
      console.error("Failed to approve", e);
    } finally {
      setSubmittingApproval(false);
    }
  };

  const handleReject = async () => {
    if (!waitingApprovalNode) return;
    setSubmittingApproval(true);
    try {
      const user = JSON.parse(localStorage.getItem("user") || "{}");
      await api.post(`/executions/nodes/${waitingApprovalNode.id}/reject`, {
        actor: user.email || user.firstName || "Unknown",
        comment: approvalComment || "Rejected",
      });
      setApprovalComment("");
      fetchStatus();
    } catch (e) {
      console.error("Failed to reject", e);
    } finally {
      setSubmittingApproval(false);
    }
  };

  // ─── Parse data for approval review ─────────────────────────────
  const getApprovalReviewData = () => {
    if (!waitingApprovalNode?.inputData) return null;
    try {
      return JSON.parse(waitingApprovalNode.inputData);
    } catch {
      return waitingApprovalNode.inputData;
    }
  };

  return (
    <div className="space-y-6">
      {/* Header bar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          {isRunning && (
            <div className="w-5 h-5 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
          )}
          {isPaused && <span className="text-orange-400 text-lg">⏸</span>}
          {overallStatus === "COMPLETED" && (
            <span className="text-emerald-400 text-lg">●</span>
          )}
          {overallStatus === "FAILED" && (
            <span className="text-red-400 text-lg">●</span>
          )}
          <h3 className="text-white font-semibold text-base">
            {isDone
              ? "Execution Complete"
              : isPaused
                ? "Waiting for Approval"
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
                  : overallStatus === "PAUSED"
                    ? "bg-orange-500/15 text-orange-400"
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
                : overallStatus === "PAUSED"
                  ? "bg-orange-400"
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
              const isWaiting = status === "WAITING_APPROVAL";
              const isLast = idx === arr.length - 1;

              return (
                <div key={node.id} className="flex items-start">
                  <div
                    className="flex flex-col items-center"
                    style={{ minWidth: 120 }}
                  >
                    <div
                      className={`relative w-14 h-14 rounded-xl flex items-center justify-center border-2 transition-all duration-500 ${style.bg} ${style.border} ${isActive || isWaiting ? `shadow-lg ${style.glow}` : ""}`}
                    >
                      <span className="text-xl">{iconInfo.icon}</span>
                      {isActive && (
                        <div className="absolute -top-1 -right-1 w-3 h-3">
                          <span className="absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75 animate-ping" />
                          <span className="relative inline-flex rounded-full h-3 w-3 bg-cyan-400" />
                        </div>
                      )}
                      {isWaiting && (
                        <div className="absolute -top-1 -right-1 w-3 h-3">
                          <span className="absolute inline-flex h-full w-full rounded-full bg-orange-400 opacity-75 animate-ping" />
                          <span className="relative inline-flex rounded-full h-3 w-3 bg-orange-400" />
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
                      {(status === "FAILED" || status === "REJECTED") && (
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

                    <span
                      className={`mt-2 text-xs font-semibold tracking-wide ${style.text}`}
                    >
                      {nodeType}
                    </span>

                    <span className="text-[10px] text-gray-500 mt-0.5">
                      {isWaiting
                        ? "Awaiting..."
                        : isActive
                          ? "Running..."
                          : status === "COMPLETED"
                            ? formatDuration(ni?.durationMs ?? null) || "Done"
                            : status === "FAILED"
                              ? "Failed"
                              : status === "REJECTED"
                                ? `Rejected${ni?.actor ? ` by ${ni.actor}` : ""}`
                                : "Queued"}
                    </span>
                  </div>

                  {!isLast && (
                    <div className="flex items-center mt-5 mx-1">
                      <div
                        className={`h-0.5 w-8 transition-colors duration-500 ${
                          status === "COMPLETED"
                            ? "bg-emerald-500/60"
                            : isActive || isWaiting
                              ? "bg-cyan-400/40"
                              : "bg-gray-700/50"
                        }`}
                      />
                      <svg
                        className={`w-3 h-3 -ml-0.5 transition-colors duration-500 ${
                          status === "COMPLETED"
                            ? "text-emerald-500/60"
                            : isActive || isWaiting
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

      {/* ─── Approval Panel ──────────────────────────────────── */}
      {isPaused && waitingApprovalNode && (
        <div className="bg-orange-500/10 border border-orange-500/30 rounded-xl p-6 space-y-4">
          <div className="flex items-center gap-3">
            <span className="text-2xl">✋</span>
            <div>
              <h3 className="text-orange-400 font-semibold text-base">
                Approval Required
              </h3>
              <p className="text-gray-400 text-sm">
                Review the data below and approve or reject to continue the
                workflow.
              </p>
            </div>
          </div>

          {/* Show data to review */}
          {waitingApprovalNode.inputData && (
            <div className="bg-gray-800/60 rounded-lg p-4 max-h-64 overflow-y-auto">
              <p className="text-xs text-gray-500 font-medium mb-2">
                Data for Review
              </p>
              <pre className="text-gray-300 text-xs whitespace-pre-wrap">
                {(() => {
                  try {
                    const parsed = JSON.parse(waitingApprovalNode.inputData);
                    // If it's GPT output, show the analysis nicely
                    if (parsed.analysis) {
                      return typeof parsed.analysis === "string"
                        ? parsed.analysis
                        : JSON.stringify(parsed.analysis, null, 2);
                    }
                    return JSON.stringify(parsed, null, 2);
                  } catch {
                    return waitingApprovalNode.inputData?.substring(0, 2000);
                  }
                })()}
              </pre>
            </div>
          )}

          {/* Comment input */}
          <div>
            <textarea
              value={approvalComment}
              onChange={(e) => setApprovalComment(e.target.value)}
              placeholder="Add a comment (optional)..."
              rows={2}
              className="w-full bg-gray-800/60 border border-gray-700/50 rounded-lg px-4 py-2.5 text-sm text-gray-300 placeholder-gray-600 outline-none focus:border-orange-500/50 resize-none"
            />
          </div>

          {/* Approve / Reject buttons */}
          <div className="flex gap-3">
            <button
              onClick={handleApprove}
              disabled={submittingApproval}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-emerald-500/20 hover:bg-emerald-500/30 border border-emerald-500/40 text-emerald-400 rounded-lg text-sm font-semibold transition-all disabled:opacity-50"
            >
              {submittingApproval ? (
                <div className="w-4 h-4 border-2 border-emerald-400 border-t-transparent rounded-full animate-spin" />
              ) : (
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M5 13l4 4L19 7"
                  />
                </svg>
              )}
              Approve & Continue
            </button>
            <button
              onClick={handleReject}
              disabled={submittingApproval}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-red-500/20 hover:bg-red-500/30 border border-red-500/40 text-red-400 rounded-lg text-sm font-semibold transition-all disabled:opacity-50"
            >
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
              Reject & Stop
            </button>
          </div>
        </div>
      )}

      {/* Completion / failure message */}
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
