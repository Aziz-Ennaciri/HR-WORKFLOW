"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "@/components/layouts/sidebar";
import api from "@/lib/api";
import toast from "react-hot-toast";

const STATUS_STYLE: Record<string, { bg: string; text: string; dot: string }> =
  {
    COMPLETED: {
      bg: "bg-emerald-50",
      text: "text-emerald-600",
      dot: "bg-emerald-500",
    },
    FAILED: { bg: "bg-red-50", text: "text-red-500", dot: "bg-red-500" },
    IN_PROGRESS: {
      bg: "bg-blue-50",
      text: "text-blue-600",
      dot: "bg-blue-500",
    },
    RUNNING: { bg: "bg-blue-50", text: "text-blue-600", dot: "bg-blue-500" },
    PENDING: { bg: "bg-amber-50", text: "text-amber-600", dot: "bg-amber-500" },
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
    <div className="bg-white border border-gray-100 rounded-xl p-5 hover:border-gray-200 hover:shadow-sm transition-all group">
      <div className="flex items-start justify-between">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3 mb-2">
            <h3 className="text-gray-900 font-semibold text-[15px] truncate">
              {execution.workflowName || `Workflow #${execution.workflowId}`}
            </h3>
            <span
              className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${style.bg} ${style.text}`}
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
                  strokeWidth={1.8}
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
                    strokeWidth={1.8}
                    d="M13 10V3L4 14h7v7l9-11h-7z"
                  />
                </svg>
                {formatDuration(execution.durationMs)}
              </span>
            )}
            <span className="text-gray-300 text-xs">#{execution.id}</span>
          </div>

          {isRunning && execution.currentNodeType && (
            <div className="mt-3 flex items-center gap-2">
              <div className="w-3.5 h-3.5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
              <span className="text-blue-600 text-xs font-medium">
                Running: {NODE_ICONS[execution.currentNodeType] || "⚙️"}{" "}
                {execution.currentNodeType}
              </span>
            </div>
          )}

          {status === "FAILED" && execution.errorMessage && (
            <p className="mt-2 text-red-400 text-xs truncate max-w-md">
              {execution.errorMessage}
            </p>
          )}
        </div>

        <button
          onClick={onView}
          className="flex-shrink-0 ml-4 px-4 py-2 bg-gray-50 hover:bg-gray-100 border border-gray-200 hover:border-gray-300 text-gray-500 hover:text-gray-700 rounded-lg text-sm font-medium transition-all flex items-center gap-2"
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
              strokeWidth={1.8}
              d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
            />
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.8}
              d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
            />
          </svg>
          View
        </button>
      </div>
    </div>
  );
}

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
      data.sort((a: any, b: any) => {
        const da = new Date(a.startedAt || a.createdAt).getTime();
        const db = new Date(b.startedAt || b.createdAt).getTime();
        return db - da;
      });

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
    <div className="flex h-screen overflow-hidden bg-[#f8f9fb]">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
      />

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-5xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-2xl font-semibold text-gray-900 tracking-tight mb-1">
              Execution History
            </h1>
            <p className="text-sm text-gray-400">
              Monitor and inspect your workflow runs
            </p>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-4 gap-3 mb-6">
            {[
              { label: "Total", value: stats.total, color: "text-gray-900" },
              {
                label: "Running",
                value: stats.running,
                color: "text-blue-600",
              },
              {
                label: "Completed",
                value: stats.completed,
                color: "text-emerald-600",
              },
              { label: "Failed", value: stats.failed, color: "text-red-500" },
            ].map((s) => (
              <div
                key={s.label}
                className="bg-white border border-gray-100 rounded-xl px-4 py-3 shadow-sm"
              >
                <p className="text-[11px] text-gray-400 font-medium uppercase tracking-wide mb-0.5">
                  {s.label}
                </p>
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
              className="bg-white border border-gray-200 text-gray-600 rounded-lg px-3 py-2 text-sm focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-500/10 shadow-sm"
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
              className="bg-white border border-gray-200 text-gray-600 rounded-lg px-3 py-2 text-sm focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-500/10 shadow-sm"
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
              className="ml-auto px-3 py-2 bg-white border border-gray-200 text-gray-400 hover:text-gray-600 hover:border-gray-300 rounded-lg text-sm transition-all flex items-center gap-2 shadow-sm"
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
                  strokeWidth={1.8}
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                />
              </svg>
              Refresh
            </button>
          </div>

          {/* Execution List */}
          <div className="space-y-2">
            {loading && executions.length === 0 ? (
              <div className="text-center py-16">
                <div className="w-8 h-8 border-[2.5px] border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
                <p className="text-gray-400 text-sm">Loading executions…</p>
              </div>
            ) : executions.length === 0 ? (
              <div className="text-center py-16">
                <p className="text-gray-400 text-base mb-1">
                  No executions found
                </p>
                <p className="text-gray-300 text-sm">
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
