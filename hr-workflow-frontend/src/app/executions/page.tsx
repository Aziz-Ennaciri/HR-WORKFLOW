"use client";

import { useEffect, useState } from "react";
import Sidebar from "@/components/layouts/sidebar";
import api from "@/lib/api";
import toast from "react-hot-toast";

function ExecutionCard({ execution }: { execution: any }) {
  return (
    <div className="bg-white rounded-lg shadow p-4">
      <h3 className="font-semibold">Execution #{execution.id}</h3>
      <p>Status: {execution.status}</p>
      <p>Workflow: {execution.workflowName || execution.workflowId}</p>
      <p>Started: {new Date(execution.startedAt).toLocaleString()}</p>
    </div>
  );
}

export default function ExecutionsPage() {
  const [executions, setExecutions] = useState<any[]>([]);
  const [filters, setFilters] = useState({
    status: "all",
    workflow: "all",
    dateRange: "week",
  });
  const [workflows, setWorkflows] = useState<any[]>([]);

  useEffect(() => {
    fetchData();
  }, [filters]);

  const fetchData = async () => {
    try {
      const execRes = await api.get("/executions");
      const wfRes = await api.get("/workflows");
      let data = execRes.data;

      // simple filter logic
      if (filters.status !== "all") {
        data = data.filter((e: any) => e.status === filters.status);
      }
      if (filters.workflow !== "all") {
        data = data.filter(
          (e: any) => e.workflowId.toString() === filters.workflow,
        );
      }
      // dateRange filtering omitted for brevity

      setExecutions(data);
      setWorkflows(wfRes.data);
    } catch (e: any) {
      console.error("failed to fetch executions", e);
      toast.error("Unable to load executions");
    }
  };

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      <Sidebar workflows={workflows} onCreateWorkflow={() => {}} />
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-7xl mx-auto px-8 py-8">
          <h1 className="text-3xl font-bold mb-6">Execution History</h1>

          {/* Filters */}
          <div className="flex gap-4 mb-6">
            <select
              value={filters.status}
              onChange={(e) =>
                setFilters({ ...filters, status: e.target.value })
              }
              className="border rounded px-3 py-2"
            >
              <option value="all">All Status</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
            </select>

            <select
              value={filters.workflow}
              onChange={(e) =>
                setFilters({ ...filters, workflow: e.target.value })
              }
              className="border rounded px-3 py-2"
            >
              <option value="all">All Workflows</option>
              {workflows.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </select>
          </div>

          {/* Execution List */}
          <div className="grid gap-4">
            {executions.map((execution) => (
              <ExecutionCard key={execution.id} execution={execution} />
            ))}
            {executions.length === 0 && (
              <p className="text-gray-500">No executions found.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
