"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "@/components/layouts/sidebar";
import api from "@/lib/api";
import toast from "react-hot-toast";

// ─── Status config ─────────────────────────────────────────────────
const STATUS_STYLE: Record<string, { bg: string; text: string; dot: string }> =
  {
    COMPLETED: {
      bg: "bg-emerald-500/15",
      text: "text-emerald-400",
      dot: "bg-emerald-400",
    },
    FAILED: { bg: "bg-red-500/15", text: "text-red-400", dot: "bg-red-400" },
    IN_PROGRESS: {
      bg: "bg-cyan-500/15",
      text: "text-cyan-400",
      dot: "bg-cyan-400",
    },
    RUNNING: {
      bg: "bg-cyan-500/15",
      text: "text-cyan-400",
      dot: "bg-cyan-400",
    },
    PENDING: {
      bg: "bg-amber-500/15",
      text: "text-amber-400",
      dot: "bg-amber-400",
    },
  };

const NODE_ICONS: Record<string, string> = {
  DRIVE: "📁",
  GPT: "🤖",
  EXCEL: "📊",
  EMAIL: "📧",
};

function formatDuration(ms: number | null) {
  if (!ms) return "—";
  if (ms < 1000) return `${ms}ms`;
  const secs = ms / 1000;
  if (secs < 60) return `${secs.toFixed(1)}s`;
  const min = Math.floor(secs / 60);
  const sec = Math.round(secs % 60);
  return `${min}m ${sec}s`;
}

function formatDate(iso: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  const now = new Date();
  const isToday = d.toDateString() === now.toDateString();
  if (isToday)
    return `Today at ${d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
  return (
    d.toLocaleDateString([], { month: "short", day: "numeric" }) +
    " " +
    d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
  );
}

// ─── Execution Card ────────────────────────────────────────────────
function ExecutionCard({
  execution,
  onView,
}: {
  execution: any;
  onView: () => void;
}) {
  const status = execution.status || "PENDING";
  const style = STATUS_STYLE[status] || STATUS_STYLE.PENDING;
  const isRunning = status === "IN_PROGRESS" || status === "RUNNING";

  return (
    <div className="bg-gray-800/50 border border-gray-700/50 rounded-xl p-5 hover:border-gray-600/60 transition-all group">
      <div className="flex items-start justify-between">
        {/* Left side */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3 mb-2">
            <h3 className="text-white font-semibold text-base truncate">
              {execution.workflowName || `Workflow #${execution.workflowId}`}
            </h3>
            <span
              className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium ${style.bg} ${style.text}`}
            >
              <span
                className={`w-1.5 h-1.5 rounded-full ${style.dot} ${isRunning ? "animate-pulse" : ""}`}
              />
              {status}
            </span>
          </div>

          <div className="flex items-center gap-4 text-sm text-gray-400">
            <span className="flex items-center gap-1.5">
              <svg
                className="w-3.5 h-3.5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              {formatDate(execution.startedAt || execution.createdAt)}
            </span>
            {execution.durationMs && (
              <span className="flex items-center gap-1.5">
                <svg
                  className="w-3.5 h-3.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M13 10V3L4 14h7v7l9-11h-7z"
                  />
                </svg>
                {formatDuration(execution.durationMs)}
              </span>
            )}
            <span className="text-gray-500">#{execution.id}</span>
          </div>

          {/* Current node indicator for running executions */}
          {isRunning && execution.currentNodeType && (
            <div className="mt-3 flex items-center gap-2">
              <div className="w-4 h-4 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
              <span className="text-cyan-400 text-xs font-medium">
                Running: {NODE_ICONS[execution.currentNodeType] || "⚙️"}{" "}
                {execution.currentNodeType}
              </span>
            </div>
          )}

          {/* Error preview */}
          {status === "FAILED" && execution.errorMessage && (
            <p className="mt-2 text-red-400/70 text-xs truncate max-w-md">
              {execution.errorMessage}
            </p>
          )}
        </div>

        {/* View button */}
        <button
          onClick={onView}
          className="flex-shrink-0 ml-4 px-4 py-2 bg-gray-700/50 hover:bg-gray-600/60 border border-gray-600/50 hover:border-gray-500/60 text-gray-300 hover:text-white rounded-lg text-sm font-medium transition-all flex items-center gap-2 group-hover:border-cyan-500/30 group-hover:text-cyan-400"
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
              d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
            />
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
            />
          </svg>
          View
        </button>
      </div>
    </div>
  );
}

// ─── Main Page ─────────────────────────────────────────────────────
export default function ExecutionsPage() {
  const router = useRouter();
  const [executions, setExecutions] = useState<any[]>([]);
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState({ status: "all", workflow: "all" });

  const fetchData = async () => {
    try {
      const [execRes, wfRes] = await Promise.all([
        api.get("/executions"),
        api.get("/workflows"),
      ]);

      let data = execRes.data || [];

      // Sort: newest first
      data.sort((a: any, b: any) => {
        const da = new Date(a.startedAt || a.createdAt).getTime();
        const db = new Date(b.startedAt || b.createdAt).getTime();
        return db - da;
      });

      // Apply filters
      if (filters.status !== "all") {
        data = data.filter((e: any) => e.status === filters.status);
      }
      if (filters.workflow !== "all") {
        data = data.filter(
          (e: any) => e.workflowId?.toString() === filters.workflow,
        );
      }

      setExecutions(data);
      setWorkflows(wfRes.data || []);
    } catch (e: any) {
      console.error("Failed to fetch executions", e);
      toast.error("Unable to load executions");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [filters]);

  // Auto-refresh if any execution is running
  useEffect(() => {
    const hasRunning = executions.some(
      (e) =>
        e.status === "IN_PROGRESS" ||
        e.status === "RUNNING" ||
        e.status === "PENDING",
    );
    if (!hasRunning) return;

    const interval = setInterval(fetchData, 3000);
    return () => clearInterval(interval);
  }, [executions]);

  const stats = {
    total: executions.length,
    completed: executions.filter((e) => e.status === "COMPLETED").length,
    failed: executions.filter((e) => e.status === "FAILED").length,
    running: executions.filter(
      (e) => e.status === "IN_PROGRESS" || e.status === "RUNNING",
    ).length,
  };

  return (
    <div className="flex h-screen overflow-hidden bg-gray-900">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
      />

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-5xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-2xl font-bold text-white mb-1">
              Execution History
            </h1>
            <p className="text-gray-400 text-sm">
              Monitor and inspect your workflow runs
            </p>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-4 gap-3 mb-6">
            {[
              { label: "Total", value: stats.total, color: "text-white" },
              {
                label: "Running",
                value: stats.running,
                color: "text-cyan-400",
              },
              {
                label: "Completed",
                value: stats.completed,
                color: "text-emerald-400",
              },
              { label: "Failed", value: stats.failed, color: "text-red-400" },
            ].map((s) => (
              <div
                key={s.label}
                className="bg-gray-800/40 border border-gray-700/40 rounded-lg px-4 py-3"
              >
                <p className="text-xs text-gray-500 mb-0.5">{s.label}</p>
                <p className={`text-xl font-bold ${s.color}`}>{s.value}</p>
              </div>
            ))}
          </div>

          {/* Filters */}
          <div className="flex gap-3 mb-6">
            <select
              value={filters.status}
              onChange={(e) =>
                setFilters({ ...filters, status: e.target.value })
              }
              className="bg-gray-800 border border-gray-700 text-gray-300 rounded-lg px-3 py-2 text-sm focus:border-cyan-500 focus:outline-none"
            >
              <option value="all">All Status</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
              <option value="IN_PROGRESS">Running</option>
              <option value="PENDING">Pending</option>
            </select>

            <select
              value={filters.workflow}
              onChange={(e) =>
                setFilters({ ...filters, workflow: e.target.value })
              }
              className="bg-gray-800 border border-gray-700 text-gray-300 rounded-lg px-3 py-2 text-sm focus:border-cyan-500 focus:outline-none"
            >
              <option value="all">All Workflows</option>
              {workflows.map((w: any) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </select>

            <button
              onClick={() => {
                setLoading(true);
                fetchData();
              }}
              className="ml-auto px-3 py-2 bg-gray-800 border border-gray-700 text-gray-400 hover:text-white hover:border-gray-600 rounded-lg text-sm transition-all flex items-center gap-2"
            >
              <svg
                className={`w-4 h-4 ${loading ? "animate-spin" : ""}`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                />
              </svg>
              Refresh
            </button>
          </div>

          {/* Execution List */}
          <div className="space-y-3">
            {loading && executions.length === 0 ? (
              <div className="text-center py-16">
                <div className="w-8 h-8 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
                <p className="text-gray-500 text-sm">Loading executions...</p>
              </div>
            ) : executions.length === 0 ? (
              <div className="text-center py-16">
                <p className="text-gray-500 text-lg mb-2">
                  No executions found
                </p>
                <p className="text-gray-600 text-sm">
                  Run a workflow to see it here
                </p>
              </div>
            ) : (
              executions.map((execution) => (
                <ExecutionCard
                  key={execution.id}
                  execution={execution}
                  onView={() => router.push(`/executions/${execution.id}`)}
                />
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
