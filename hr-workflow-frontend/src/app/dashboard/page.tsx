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

  const buildBanners = useCallback(async () => {
    try {
      const [exRes, wfRes] = await Promise.all([
        api.get("/executions"),
        api.get("/workflows"),
      ]);
      const wfs: any[] = wfRes.data;
      const exs: any[] = exRes.data;

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

      const stored: TrackedExecution[] = JSON.parse(
        localStorage.getItem("runningExecutions") || "[]",
      );
      const pending = stored.filter((e) => !e.id || e.id === -1);

      const merged: TrackedExecution[] = [
        ...pending,
        ...recent.filter((r) => !pending.some((p) => p.id === r.id)),
      ];

      setTrackedExecutions(merged);
      localStorage.setItem("runningExecutions", JSON.stringify(pending));
    } catch (e) {
      console.warn("Could not build banners", e);
    }
  }, []);

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

  const recentBanners = trackedExecutions.filter((ex) => {
    if (!ex.startedAt) return true;
    const age = Date.now() - new Date(ex.startedAt).getTime();
    return age < 30 * 60 * 1000;
  });

  const dismissBanner = (idx: number) => {
    setTrackedExecutions((prev) => prev.filter((_, i) => i !== idx));
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-[2.5px] border-blue-600 border-t-transparent rounded-full animate-spin" />
          <p className="text-sm text-gray-400 font-medium">
            Loading dashboard…
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen overflow-hidden bg-[#f8f9fb]">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => setShowCreateModal(true)}
      />

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-6xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-8 flex items-start justify-between">
            <div>
              <h1 className="text-2xl font-semibold text-gray-900 tracking-tight">
                Dashboard
              </h1>
              <p className="text-sm text-gray-400 mt-1">
                Overview of your workflows and recent activity
              </p>
            </div>
            <button
              onClick={() => router.push("/executions")}
              className="text-sm text-gray-500 hover:text-gray-700 border border-gray-200 hover:border-gray-300 px-4 py-2 rounded-lg transition-all bg-white flex items-center gap-2 shadow-sm"
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
                  d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                />
              </svg>
              View All Executions
            </button>
          </div>

          {/* Recent Runs */}
          {recentBanners.length > 0 && (
            <div className="mb-6 space-y-2">
              <p className="text-[11px] text-gray-400 font-semibold uppercase tracking-wider mb-2">
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
                    className={`flex items-center justify-between rounded-xl px-5 py-3 border transition-all bg-white ${
                      isRunning
                        ? "border-blue-200"
                        : isDone
                          ? "border-emerald-200"
                          : "border-red-200"
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      {isRunning ? (
                        <div className="w-4 h-4 rounded-full border-2 border-blue-500 border-t-transparent animate-spin shrink-0" />
                      ) : (
                        <div
                          className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 ${
                            isDone
                              ? "bg-emerald-100 text-emerald-600"
                              : "bg-red-100 text-red-500"
                          }`}
                        >
                          <svg
                            className="w-3 h-3"
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
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-gray-800 text-sm">
                          {ex.workflowName}
                        </span>
                        <span
                          className={`text-[11px] font-semibold px-2 py-0.5 rounded-full ${
                            isRunning
                              ? "bg-blue-50 text-blue-600"
                              : isDone
                                ? "bg-emerald-50 text-emerald-600"
                                : "bg-red-50 text-red-600"
                          }`}
                        >
                          {isPending ? "STARTING…" : ex.status}
                        </span>
                        <span className="text-[11px] text-gray-400">
                          {ex.id && ex.id !== -1 ? `#${ex.id} · ` : ""}
                          {timeAgo(ex.startedAt)}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      {!isRunning && ex.id && ex.id !== -1 && (
                        <button
                          onClick={() => router.push(`/executions/${ex.id}`)}
                          className="text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors bg-gray-900 text-white hover:bg-gray-700"
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
            <div className="bg-white rounded-2xl shadow-sm border border-dashed border-gray-300 p-12 text-center">
              <div className="max-w-md mx-auto">
                <div className="mx-auto h-16 w-16 rounded-2xl bg-gray-50 border border-gray-100 flex items-center justify-center mb-5">
                  <svg
                    className="h-8 w-8 text-gray-300"
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
                <h3 className="text-lg font-semibold text-gray-900">
                  No workflows yet
                </h3>
                <p className="mt-1.5 text-gray-400 text-sm">
                  Get started by creating your first automated workflow
                </p>
                <button
                  onClick={() => setShowCreateModal(true)}
                  className="mt-5 inline-flex items-center gap-2 px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors shadow-sm"
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
                      d="M12 4v16m8-8H4"
                    />
                  </svg>
                  Create Your First Workflow
                </button>
              </div>
            </div>
          ) : (
            <>
              {/* Stat Cards */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
                {[
                  {
                    label: "Total Workflows",
                    value: stats.totalWorkflows,
                    icon: (
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1.8}
                        d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
                      />
                    ),
                    color: "text-blue-600",
                    bg: "bg-blue-50",
                  },
                  {
                    label: "Active Workflows",
                    value: stats.activeWorkflows,
                    icon: (
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1.8}
                        d="M13 10V3L4 14h7v7l9-11h-7z"
                      />
                    ),
                    color: "text-emerald-600",
                    bg: "bg-emerald-50",
                  },
                  {
                    label: "Success Rate",
                    value: `${stats.successRate}%`,
                    icon: (
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1.8}
                        d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                      />
                    ),
                    color: "text-amber-600",
                    bg: "bg-amber-50",
                  },
                ].map((stat) => (
                  <div
                    key={stat.label}
                    className="bg-white rounded-xl border border-gray-100 p-5 shadow-sm"
                  >
                    <div className="flex items-center justify-between mb-3">
                      <p className="text-sm text-gray-400 font-medium">
                        {stat.label}
                      </p>
                      <div
                        className={`w-9 h-9 ${stat.bg} rounded-lg flex items-center justify-center`}
                      >
                        <svg
                          className={`w-[18px] h-[18px] ${stat.color}`}
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          {stat.icon}
                        </svg>
                      </div>
                    </div>
                    <p className="text-3xl font-bold text-gray-900 tracking-tight">
                      {stat.value}
                    </p>
                  </div>
                ))}
              </div>

              {/* Chart */}
              <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                <h2 className="text-sm font-semibold text-gray-900 mb-5">
                  Executions by Workflow
                </h2>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={executionData} barSize={28}>
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="#f1f5f9"
                      vertical={false}
                    />
                    <XAxis
                      dataKey="name"
                      tick={{ fontSize: 12, fill: "#94a3b8" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      tick={{ fontSize: 12, fill: "#94a3b8" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      contentStyle={{
                        background: "#fff",
                        border: "1px solid #e2e8f0",
                        borderRadius: 8,
                        color: "#1e293b",
                        fontSize: 13,
                        boxShadow: "0 4px 6px -1px rgba(0,0,0,0.06)",
                      }}
                      cursor={{ fill: "#f8fafc" }}
                    />
                    <Bar
                      dataKey="executions"
                      fill="#3b82f6"
                      radius={[6, 6, 0, 0]}
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
