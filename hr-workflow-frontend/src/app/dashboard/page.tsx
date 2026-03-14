"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import api from "@/lib/api";
import Sidebar from "@/components/layouts/sidebar";
import CreateWorkflowModal from "@/components/workflow/CreateWorkflowModal";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
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
  const router = useRouter();

  // when a workflow is created we want to refresh our stats/workflows
  const handleNewWorkflow = () => {
    fetchStats();
  };

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      router.push("/login");
      return;
    }
    fetchStats();
  }, [router]);

  const fetchStats = async () => {
    try {
      const workflowsRes = await api.get("/workflows");
      const executionsRes = await api.get("/executions");

      const workflowsData = workflowsRes.data;
      const executionsData = executionsRes.data;

      setWorkflows(workflowsData);
      setStats({
        totalWorkflows: workflowsData.length,
        activeWorkflows: workflowsData.filter((w: any) => w.status === "ACTIVE")
          .length,
        totalExecutions: executionsData.length,
        successRate: executionsData.length
          ? Math.round(
              (executionsData.filter((e: any) => e.status === "COMPLETED")
                .length /
                executionsData.length) *
                100,
            )
          : 0,
      });

      // build chart data by workflow name
      const counts: Record<string, number> = {};
      executionsData.forEach((e: any) => {
        const wf = workflowsData.find((w: any) => w.id === e.workflowId);
        const name = wf ? wf.name : `#${e.workflowId}`;
        counts[name] = (counts[name] || 0) + 1;
      });
      setExecutionData(
        Object.entries(counts).map(([name, executions]) => ({
          name,
          executions,
        })),
      );
    } catch (error) {
      console.error("Failed to fetch stats:", error);
      toast.error("Unable to load dashboard stats");
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto"></div>
          <p className="mt-4 text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      {/* Sidebar */}
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => setShowCreateModal(true)}
      />

      {/* Main Content */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-7xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-4xl font-bold text-gray-900">
              Welcome to HR Workflow
            </h1>
            <p className="text-gray-600 mt-2">
              Create and manage your automated workflows
            </p>
          </div>

          {/* Empty State or Stats */}
          {workflows.length === 0 ? (
            <div className="bg-white rounded-2xl shadow-sm border-2 border-dashed border-gray-300 p-12 text-center">
              <div className="max-w-md mx-auto">
                <svg
                  className="mx-auto h-24 w-24 text-gray-400"
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
                <h3 className="mt-6 text-2xl font-semibold text-gray-900">
                  No workflows yet
                </h3>
                <p className="mt-2 text-gray-600">
                  Get started by creating your first automated workflow
                </p>
                <Button
                  onClick={() => setShowCreateModal(true)}
                  className="mt-8 inline-flex items-center space-x-2"
                  variant="primary"
                >
                  <svg
                    className="w-5 h-5"
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
                  <span>Create Your First Workflow</span>
                </Button>
              </div>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                {/* Stats Cards using state */}
                <div className="bg-gradient-to-br from-primary to-primary/90 rounded-xl shadow-lg p-6 text-white">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-yellow-100 text-sm font-medium">
                        Total Workflows
                      </p>
                      <p className="text-4xl font-bold mt-2">
                        {stats.totalWorkflows}
                      </p>
                    </div>
                    <div className="bg-white bg-opacity-20 p-3 rounded-lg">
                      <svg
                        className="w-8 h-8"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                        />
                      </svg>
                    </div>
                  </div>
                </div>

                <div className="bg-gradient-to-br from-green-500 to-green-600 rounded-xl shadow-lg p-6 text-white">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-green-100 text-sm font-medium">
                        Active
                      </p>
                      <p className="text-4xl font-bold mt-2">
                        {stats.activeWorkflows}
                      </p>
                    </div>
                    <div className="bg-white bg-opacity-20 p-3 rounded-lg">
                      <svg
                        className="w-8 h-8"
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
                    </div>
                  </div>
                </div>

                <div className="bg-gradient-to-br from-yellow-500 to-yellow-600 rounded-xl shadow-lg p-6 text-white">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-yellow-100 text-sm font-medium">
                        Success%
                      </p>
                      <p className="text-4xl font-bold mt-2">
                        {stats.successRate}%
                      </p>
                    </div>
                    <div className="bg-white bg-opacity-20 p-3 rounded-lg">
                      <svg
                        className="w-8 h-8"
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
                    </div>
                  </div>
                </div>
              </div>

              {/* Chart */}
              <div className="bg-white rounded-xl shadow p-6">
                <h2 className="text-xl font-semibold mb-4">
                  Executions by Workflow
                </h2>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={executionData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="executions" fill="#3b82f6" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </>
          )}

          {/* Quick Actions */}
          {workflows.length > 0 && (
            <div className="mt-8 bg-white rounded-xl shadow-sm border p-6">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">
                Quick Actions
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Button
                  onClick={() => setShowCreateModal(true)}
                  className="flex items-center space-x-3 p-4"
                  variant="outline"
                >
                  <div className="bg-primary/20 group-hover:bg-primary/30 p-3 rounded-lg transition">
                    <svg
                      className="w-6 h-6 text-primary"
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
                  </div>
                  <div className="text-left">
                    <p className="font-medium text-gray-900">
                      Create New Workflow
                    </p>
                    <p className="text-sm text-gray-500">
                      Start automating a new process
                    </p>
                  </div>
                </Button>

                <Button
                  onClick={() => router.push("/executions")}
                  className="flex items-center space-x-3 p-4"
                  variant="outline"
                >
                  <div className="bg-green-100 group-hover:bg-green-200 p-3 rounded-lg transition">
                    <svg
                      className="w-6 h-6 text-green-600"
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
                  </div>
                  <div className="text-left">
                    <p className="font-medium text-gray-900">View Executions</p>
                    <p className="text-sm text-gray-500">
                      Monitor workflow runs
                    </p>
                  </div>
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Create Workflow Modal */}
      <CreateWorkflowModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSuccess={handleNewWorkflow}
      />
    </div>
  );
}
