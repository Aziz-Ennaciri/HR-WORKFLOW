"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import api from "@/lib/api";
import Sidebar from "@/components/layouts/sidebar";
import CreateWorkflowModal from "@/components/workflow/CreateWorkflowModal";
import Button from "@/components/ui/Button";
import type { Workflow } from "@/types";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import toast from "react-hot-toast";

interface TrackedExecution {
  id: number | null;
  workflowId: number;
  workflowName: string;
  startedAt: string;
  status: "RUNNING" | "COMPLETED" | "FAILED" | "STARTING";
}

function timeAgo(iso?: string) {
  if (!iso) return "";
  const diff = Date.now() - new Date(iso).getTime();
  const s = Math.floor(diff / 1000);
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  return new Date(iso).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function DashboardPage() {
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [stats, setStats] = useState({
    totalWorkflows: 0,
    activeWorkflows: 0,
    totalExecutions: 0,
    successRate: 0,
  });
  const [executionData, setExecutionData] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [trackedExecutions, setTrackedExecutions] = useState<
    TrackedExecution[]
  >([]);
  const router = useRouter();
  const pollRef = useRef<NodeJS.Timeout | null>(null);

  const fetchStats = useCallback(async () => {
    try {
      const [wfRes, exRes] = await Promise.all([
        api.get("/workflows"),
        api.get("/executions"),
      ]);
      const wfs = wfRes.data;
      const exs = exRes.data;
      setWorkflows(wfs);
      setStats({
        totalWorkflows: wfs.length,
        activeWorkflows: wfs.filter((w: any) => w.status === "ACTIVE").length,
        totalExecutions: exs.length,
        successRate: exs.length
          ? Math.round(
              (exs.filter((e: any) => e.status === "COMPLETED").length /
                exs.length) *
                100,
            )
          : 0,
      });
      const counts: Record<string, number> = {};
      exs.forEach((e: any) => {
        const wf = wfs.find((w: any) => w.id === e.workflowId);
        const name = wf ? wf.name : `#${e.workflowId}`;
        counts[name] = (counts[name] || 0) + 1;
      });
      setExecutionData(
        Object.entries(counts).map(([name, executions]) => ({
          name,
          executions,
        })),
      );
      return { wfs, exs };
    } catch {
      toast.error("Unable to load dashboard stats");
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * Build the banner list by merging:
   * 1. Recent executions from API (last 5, so they survive refresh)
   * 2. Any pending placeholder from localStorage (id = -1, still in-flight)
   */
  const buildBanners = useCallback(async () => {
    try {
      const [exRes, wfRes] = await Promise.all([
        api.get("/executions"),
        api.get("/workflows"),
      ]);
      const wfs: any[] = wfRes.data;
      const exs: any[] = exRes.data;

      // Sort newest first, take last 5
      const recent = [...exs]
        .sort(
          (a, b) =>
            new Date(b.startedAt || b.createdAt || 0).getTime() -
            new Date(a.startedAt || a.createdAt || 0).getTime(),
        )
        .slice(0, 5)
        .map((ex) => {
          const wf = wfs.find((w) => w.id === ex.workflowId);
          return {
            id: ex.id,
            workflowId: ex.workflowId,
            workflowName: wf?.name || `Workflow #${ex.workflowId}`,
            startedAt: ex.startedAt || ex.createdAt,
            status: ex.status as TrackedExecution["status"],
          };
        });

      // Also include any still-pending placeholder (id = -1) from localStorage
      const stored: TrackedExecution[] = JSON.parse(
        localStorage.getItem("runningExecutions") || "[]",
      );
      const pending = stored.filter((e) => !e.id || e.id === -1);

      // Merge: pending first, then recent (deduplicated)
      const merged: TrackedExecution[] = [
        ...pending,
        ...recent.filter((r) => !pending.some((p) => p.id === r.id)),
      ];

      setTrackedExecutions(merged);

      // Clean up localStorage — only keep real pending items
      localStorage.setItem("runningExecutions", JSON.stringify(pending));
    } catch (e) {
      console.warn("Could not build banners", e);
    }
  }, []);

  // Listen for execution-updated event fired by execute page
  useEffect(() => {
    const handler = () => {
      buildBanners();
      fetchStats();
    };
    window.addEventListener("execution-updated", handler);
    return () => window.removeEventListener("execution-updated", handler);
  }, [buildBanners, fetchStats]);

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      router.push("/login");
      return;
    }
    fetchStats();
    buildBanners();
  }, [router, fetchStats, buildBanners]);

  // Poll only if there are genuinely running executions
  useEffect(() => {
    const hasRunning = trackedExecutions.some(
      (e) => e.status === "RUNNING" && e.id && e.id !== -1,
    );
    const hasPending = trackedExecutions.some((e) => !e.id || e.id === -1);

    if (hasRunning || hasPending) {
      pollRef.current = setInterval(() => {
        buildBanners();
        fetchStats();
      }, 3000);
    } else {
      if (pollRef.current) clearInterval(pollRef.current);
    }
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [trackedExecutions, buildBanners, fetchStats]);

  // Only show banners for executions from the last 30 minutes
  const recentBanners = trackedExecutions.filter((ex) => {
    if (!ex.startedAt) return true;
    const age = Date.now() - new Date(ex.startedAt).getTime();
    return age < 30 * 60 * 1000; // 30 min
  });

  const dismissBanner = (idx: number) => {
    setTrackedExecutions((prev) => prev.filter((_, i) => i !== idx));
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto" />
      </div>
    );
  }

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => setShowCreateModal(true)}
      />

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-7xl mx-auto px-8 py-8">
          <div className="mb-8 flex items-center justify-between">
            <div>
              <h1 className="text-4xl font-bold text-gray-900">
                Welcome to HR Workflow
              </h1>
              <p className="text-gray-600 mt-1">
                Create and manage your automated workflows
              </p>
            </div>
            <button
              onClick={() => router.push("/executions")}
              className="text-sm text-gray-500 hover:text-gray-800 border border-gray-200 hover:border-gray-300 px-4 py-2 rounded-lg transition-all bg-white flex items-center gap-2"
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
                  d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                />
              </svg>
              View All Executions
            </button>
          </div>

          {/* ── Recent execution banners ── */}
          {recentBanners.length > 0 && (
            <div className="mb-6 space-y-2">
              <p className="text-xs text-gray-400 font-medium uppercase tracking-wide mb-2">
                Recent Runs
              </p>
              {recentBanners.map((ex, idx) => {
                const isPending = !ex.id || ex.id === -1;
                const isRunning = ex.status === "RUNNING" || isPending;
                const isDone = ex.status === "COMPLETED";
                const isFailed = ex.status === "FAILED";

                return (
                  <div
                    key={`${ex.id}-${idx}`}
                    className={`flex items-center justify-between rounded-xl px-5 py-3.5 border transition-all ${
                      isRunning
                        ? "bg-blue-50 border-blue-200"
                        : isDone
                          ? "bg-emerald-50 border-emerald-200"
                          : "bg-red-50 border-red-200"
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      {isRunning ? (
                        <div className="w-4 h-4 rounded-full border-2 border-blue-400 border-t-transparent animate-spin shrink-0" />
                      ) : (
                        <div
                          className={`w-4 h-4 rounded-full flex items-center justify-center shrink-0 ${isDone ? "bg-emerald-500" : "bg-red-500"}`}
                        >
                          <svg
                            className="w-2.5 h-2.5 text-white"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            {isDone ? (
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={3}
                                d="M5 13l4 4L19 7"
                              />
                            ) : (
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={3}
                                d="M6 18L18 6M6 6l12 12"
                              />
                            )}
                          </svg>
                        </div>
                      )}
                      <div>
                        <span className="font-medium text-gray-800 text-sm">
                          {ex.workflowName}
                        </span>
                        <span
                          className={`ml-2 text-xs font-medium px-2 py-0.5 rounded-full ${
                            isRunning
                              ? "bg-blue-100 text-blue-600"
                              : isDone
                                ? "bg-emerald-100 text-emerald-700"
                                : "bg-red-100 text-red-700"
                          }`}
                        >
                          {isPending ? "STARTING…" : ex.status}
                        </span>
                        <span className="ml-2 text-xs text-gray-400">
                          {ex.id && ex.id !== -1 ? `#${ex.id} · ` : ""}
                          {timeAgo(ex.startedAt)}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      {!isRunning && ex.id && ex.id !== -1 && (
                        <button
                          onClick={() => router.push(`/executions/${ex.id}`)}
                          className={`text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors ${
                            isDone
                              ? "bg-emerald-600 text-white hover:bg-emerald-700"
                              : "bg-red-600 text-white hover:bg-red-700"
                          }`}
                        >
                          View Results →
                        </button>
                      )}
                      {!isRunning && (
                        <button
                          onClick={() => dismissBanner(idx)}
                          className="text-gray-300 hover:text-gray-500 text-lg leading-none w-6 h-6 flex items-center justify-center"
                        >
                          ×
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Stats / empty state */}
          {workflows.length === 0 ? (
            <div className="bg-white rounded-2xl shadow-sm border-2 border-dashed border-gray-300 p-12 text-center">
              <div className="max-w-md mx-auto">
                <div className="mx-auto h-20 w-20 rounded-2xl bg-gray-100 flex items-center justify-center mb-6">
                  <svg
                    className="h-10 w-10 text-gray-400"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={1.5}
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                    />
                  </svg>
                </div>
                <h3 className="text-xl font-semibold text-gray-900">
                  No workflows yet
                </h3>
                <p className="mt-2 text-gray-500 text-sm">
                  Get started by creating your first automated workflow
                </p>
                <Button
                  onClick={() => setShowCreateModal(true)}
                  className="mt-6"
                  variant="primary"
                >
                  + Create Your First Workflow
                </Button>
              </div>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-5 mb-8">
                <div className="bg-gradient-to-br from-blue-600 to-blue-700 rounded-xl p-6 text-white">
                  <p className="text-blue-200 text-sm font-medium">
                    Total Workflows
                  </p>
                  <p className="text-4xl font-bold mt-1">
                    {stats.totalWorkflows}
                  </p>
                </div>
                <div className="bg-gradient-to-br from-emerald-500 to-emerald-600 rounded-xl p-6 text-white">
                  <p className="text-emerald-100 text-sm font-medium">Active</p>
                  <p className="text-4xl font-bold mt-1">
                    {stats.activeWorkflows}
                  </p>
                </div>
                <div className="bg-gradient-to-br from-amber-500 to-amber-600 rounded-xl p-6 text-white">
                  <p className="text-amber-100 text-sm font-medium">
                    Success Rate
                  </p>
                  <p className="text-4xl font-bold mt-1">
                    {stats.successRate}%
                  </p>
                </div>
              </div>

              <div className="bg-white rounded-xl shadow-sm border p-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-4">
                  Executions by Workflow
                </h2>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={executionData} barSize={32}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis
                      dataKey="name"
                      tick={{ fontSize: 12, fill: "#9ca3af" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      tick={{ fontSize: 12, fill: "#9ca3af" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      contentStyle={{
                        background: "#1f2937",
                        border: "none",
                        borderRadius: 8,
                        color: "#f9fafb",
                        fontSize: 12,
                      }}
                      cursor={{ fill: "#f3f4f6" }}
                    />
                    <Bar
                      dataKey="executions"
                      fill="#3b82f6"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </>
          )}
        </div>
      </div>

      <CreateWorkflowModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSuccess={fetchStats}
      />
    </div>
  );
}
