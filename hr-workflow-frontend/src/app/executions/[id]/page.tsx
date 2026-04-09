"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import Sidebar from "@/components/layouts/sidebar";
import ExecutionTracker from "@/components/executions/ExecutionTracker";
import api from "@/lib/api";
import toast from "react-hot-toast";

export default function ExecutionDetailPage() {
  const params = useParams();
  const router = useRouter();
  const executionId = parseInt(params.id as string);

  const [execution, setExecution] = useState<any>(null);
  const [nodeInstances, setNodeInstances] = useState<any[]>([]);
  const [workflowNodes, setWorkflowNodes] = useState<any[]>([]);
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [nodeConfigMap, setNodeConfigMap] = useState<Record<number, any>>({});

  // Fetch execution detail
  const fetchDetail = useCallback(async () => {
    try {
      const res = await api.get(`/executions/${executionId}/detail`);
      const data = res.data;

      setExecution(data.workflowInstance || data);
      setNodeInstances(data.nodeInstances || []);

      // Build workflowNodes for tracker from nodeInstances
      const nodes = (data.nodeInstances || []).map((ni: any) => ({
        id: ni.nodeId,
        type: ni.nodeType,
        orderIndex: ni.executionOrder,
      }));

      // Deduplicate by nodeId
      const unique = nodes.filter(
        (n: any, i: number, arr: any[]) =>
          arr.findIndex((x: any) => x.id === n.id) === i,
      );
      unique.sort((a: any, b: any) => a.orderIndex - b.orderIndex);
      setWorkflowNodes(unique);

      // Load node configs for excel download
      const configs: Record<number, any> = {};
      for (const ni of data.nodeInstances || []) {
        if (ni.nodeType === "EXCEL" && !nodeConfigMap[ni.nodeId]) {
          try {
            const nodeResp = await api.get(`/nodes/${ni.nodeId}`);
            configs[ni.nodeId] = JSON.parse(nodeResp.data.configJson || "{}");
          } catch {}
        }
      }
      if (Object.keys(configs).length > 0) {
        setNodeConfigMap((prev) => ({ ...prev, ...configs }));
      }
    } catch (e: any) {
      console.error("Failed to fetch execution detail", e);
      toast.error("Failed to load execution details");
    } finally {
      setLoading(false);
    }
  }, [executionId]);

  useEffect(() => {
    fetchDetail();
    api
      .get("/workflows")
      .then((r) => setWorkflows(r.data || []))
      .catch(() => {});
  }, [fetchDetail]);

  // ─── Excel download ───────────────────────────────────────────────
  const downloadExcel = async (nodeInstance: any) => {
    try {
      if (nodeInstance.outputData) {
        const output = JSON.parse(nodeInstance.outputData);
        if (output.fileContent) {
          const byteChars = atob(output.fileContent);
          const byteArr = new Uint8Array(byteChars.length);
          for (let i = 0; i < byteChars.length; i++)
            byteArr[i] = byteChars.charCodeAt(i);
          const blob = new Blob([byteArr], {
            type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          });
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = output.fileName || `report-${nodeInstance.id}.xlsx`;
          document.body.appendChild(a);
          a.click();
          window.URL.revokeObjectURL(url);
          document.body.removeChild(a);
          return;
        }
        if (output.filePath) {
          toast.success(`File saved at: ${output.filePath}`);
          return;
        }
      }
      toast.error("No downloadable file available");
    } catch {
      toast.error("Failed to download file");
    }
  };

  const status = execution?.status || "LOADING";
  const isRunning =
    status === "IN_PROGRESS" || status === "RUNNING" || status === "PENDING";

  return (
    <div className="flex h-screen overflow-hidden bg-gray-900">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
      />

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-5xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-6">
            <button
              onClick={() => router.push("/executions")}
              className="text-gray-400 hover:text-cyan-400 mb-4 flex items-center gap-2 text-sm transition-colors"
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
                  d="M10 19l-7-7m0 0l7-7m-7 7h18"
                />
              </svg>
              Back to Executions
            </button>

            <div className="flex items-center justify-between">
              <div>
                <h1 className="text-2xl font-bold text-white">
                  {execution?.workflowName || "Execution"} #{executionId}
                </h1>
                <p className="text-gray-400 text-sm mt-1">
                  {execution?.startedAt
                    ? `Started ${new Date(execution.startedAt).toLocaleString()}`
                    : "Loading..."}
                  {execution?.triggeredByEmail &&
                    ` • by ${execution.triggeredByEmail}`}
                </p>
              </div>
            </div>
          </div>

          {loading ? (
            <div className="flex items-center justify-center py-20">
              <div className="text-center">
                <div className="w-8 h-8 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
                <p className="text-gray-500 text-sm">
                  Loading execution details...
                </p>
              </div>
            </div>
          ) : (
            <div className="space-y-6">
              {/* ─── Horizontal Execution Tracker ─────────────────── */}
              {workflowNodes.length > 0 && (
                <div className="bg-gray-800/40 border border-gray-700/40 rounded-xl p-6">
                  <ExecutionTracker
                    executionId={executionId}
                    nodes={workflowNodes}
                    onComplete={() => fetchDetail()}
                  />
                </div>
              )}

              {/* ─── Detailed Node Results (shown after execution) ── */}
              {nodeInstances.length > 0 && !isRunning && (
                <div className="bg-gray-800/40 border border-gray-700/40 rounded-xl p-6">
                  <h2 className="text-white font-semibold text-lg mb-4">
                    Node Results
                  </h2>
                  <div className="space-y-3">
                    {nodeInstances
                      .sort(
                        (a: any, b: any) => a.executionOrder - b.executionOrder,
                      )
                      .map((ni: any) => (
                        <NodeResultCard
                          key={ni.id}
                          nodeInstance={ni}
                          onDownloadExcel={() => downloadExcel(ni)}
                        />
                      ))}
                  </div>
                </div>
              )}

              {/* ─── Execution Metadata ───────────────────────────── */}
              {execution && !isRunning && (
                <div className="bg-gray-800/40 border border-gray-700/40 rounded-xl p-6">
                  <h2 className="text-white font-semibold text-lg mb-4">
                    Execution Info
                  </h2>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    <InfoCard label="Execution ID" value={`#${execution.id}`} />
                    <InfoCard label="Status" value={execution.status} />
                    <InfoCard
                      label="Duration"
                      value={
                        execution.durationMs
                          ? `${(execution.durationMs / 1000).toFixed(1)}s`
                          : "—"
                      }
                    />
                    <InfoCard
                      label="Finished"
                      value={
                        execution.finishedAt
                          ? new Date(execution.finishedAt).toLocaleTimeString()
                          : "—"
                      }
                    />
                  </div>

                  {execution.errorMessage && (
                    <div className="mt-4 bg-red-500/10 border border-red-500/30 rounded-lg p-4">
                      <p className="text-red-400 text-sm font-medium mb-1">
                        Error
                      </p>
                      <pre className="text-red-300/70 text-xs whitespace-pre-wrap overflow-x-auto">
                        {execution.errorMessage}
                      </pre>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Sub-components ──────────────────────────────────────────────────

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-gray-700/30 rounded-lg px-4 py-3">
      <p className="text-xs text-gray-500 mb-0.5">{label}</p>
      <p className="text-white font-medium text-sm">{value}</p>
    </div>
  );
}

const NODE_ICONS: Record<string, string> = {
  DRIVE: "📁",
  GPT: "🤖",
  EXCEL: "📊",
  EMAIL: "📧",
};

function NodeResultCard({
  nodeInstance,
  onDownloadExcel,
}: {
  nodeInstance: any;
  onDownloadExcel: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const type = nodeInstance.nodeType || "UNKNOWN";
  const status = nodeInstance.status || "PENDING";
  const icon = NODE_ICONS[type] || "⚙️";

  const isCompleted = status === "COMPLETED";
  const isFailed = status === "FAILED";
  const hasOutput = nodeInstance.outputData;

  let parsedOutput: any = null;
  let candidates: any[] = [];

  if (hasOutput) {
    try {
      parsedOutput = JSON.parse(nodeInstance.outputData);
      if (parsedOutput.analysis) {
        try {
          const parsed = JSON.parse(parsedOutput.analysis);
          if (Array.isArray(parsed)) candidates = parsed;
        } catch {}
      }
    } catch {}
  }

  return (
    <div
      className={`border rounded-xl transition-all ${
        isFailed
          ? "border-red-500/30 bg-red-500/5"
          : isCompleted
            ? "border-gray-700/50 bg-gray-800/30"
            : "border-gray-700/30 bg-gray-800/20"
      }`}
    >
      <div
        className="flex items-center gap-4 px-4 py-3 cursor-pointer"
        onClick={() => setExpanded(!expanded)}
      >
        <span className="text-xl">{icon}</span>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <span className="text-white font-medium text-sm">{type}</span>
            <span
              className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                isCompleted
                  ? "bg-emerald-500/15 text-emerald-400"
                  : isFailed
                    ? "bg-red-500/15 text-red-400"
                    : "bg-gray-500/15 text-gray-400"
              }`}
            >
              {status}
            </span>
            {parsedOutput?.model && (
              <span className="text-[11px] text-gray-500">
                {parsedOutput.model}
              </span>
            )}
          </div>
          <div className="text-xs text-gray-500 mt-0.5 flex gap-3">
            {nodeInstance.startedAt && (
              <span>
                {new Date(nodeInstance.startedAt).toLocaleTimeString()}
              </span>
            )}
            {nodeInstance.durationMs && (
              <span>
                {nodeInstance.durationMs < 1000
                  ? `${nodeInstance.durationMs}ms`
                  : `${(nodeInstance.durationMs / 1000).toFixed(1)}s`}
              </span>
            )}
            {candidates.length > 0 && (
              <span className="text-emerald-400">
                {candidates.length} candidates
              </span>
            )}
          </div>
        </div>
        <svg
          className={`w-4 h-4 text-gray-500 transition-transform ${expanded ? "rotate-180" : ""}`}
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
      </div>

      {expanded && (
        <div className="border-t border-gray-700/40 px-4 py-4 space-y-3">
          {isFailed && nodeInstance.errorMessage && (
            <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-3">
              <p className="text-red-400 text-xs font-semibold mb-1">Error</p>
              <pre className="text-red-300/70 text-xs whitespace-pre-wrap overflow-x-auto">
                {nodeInstance.errorMessage}
              </pre>
            </div>
          )}

          {candidates.length > 0 && (
            <div className="space-y-2">
              <p className="text-gray-400 text-xs font-medium">
                Matched Candidates
              </p>
              {candidates.map((c: any, i: number) => (
                <div
                  key={i}
                  className="flex items-center justify-between bg-gray-700/30 rounded-lg px-4 py-2.5"
                >
                  <div>
                    <span className="text-white text-sm font-medium">
                      {c.name}
                    </span>
                    {c.email && (
                      <span className="text-gray-500 text-xs ml-2">
                        {c.email}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-4">
                    {c.skills && (
                      <span className="text-gray-500 text-xs hidden md:inline">
                        {c.skills}
                      </span>
                    )}
                    <span className="text-gray-400 text-xs">
                      {c.experience} yrs
                    </span>
                    <span className="text-emerald-400 text-sm font-bold">
                      {c.score}/10
                    </span>
                  </div>
                </div>
              ))}
              {parsedOutput?.tokensUsed && (
                <p className="text-[11px] text-gray-600 mt-1">
                  {parsedOutput.model} • {parsedOutput.tokensUsed} tokens
                </p>
              )}
            </div>
          )}

          {parsedOutput?.analysis && candidates.length === 0 && (
            <div className="bg-gray-700/20 rounded-lg p-3">
              <p className="text-gray-400 text-xs font-medium mb-2">
                AI Analysis
              </p>
              <pre className="text-gray-300 text-xs whitespace-pre-wrap overflow-x-auto max-h-48">
                {parsedOutput.analysis}
              </pre>
            </div>
          )}

          {type === "EXCEL" && parsedOutput && (
            <div className="flex items-center justify-between bg-gray-700/30 rounded-lg px-4 py-3">
              <div className="flex items-center gap-3">
                <span className="text-2xl">📄</span>
                <div>
                  <p className="text-white text-sm font-medium">
                    {parsedOutput.fileName || "Report.xlsx"}
                  </p>
                  {parsedOutput.fileSize && (
                    <p className="text-gray-500 text-xs">
                      {(parsedOutput.fileSize / 1024).toFixed(1)} KB
                    </p>
                  )}
                </div>
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onDownloadExcel();
                }}
                className="px-3 py-1.5 bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 rounded-lg text-xs font-medium hover:bg-emerald-500/25 transition-all"
              >
                Download
              </button>
            </div>
          )}

          {type === "EMAIL" && parsedOutput && (
            <div className="bg-emerald-500/10 border border-emerald-500/20 rounded-lg px-4 py-3 flex items-center gap-3">
              <span className="text-emerald-400">✓</span>
              <div>
                <p className="text-emerald-400 text-sm font-medium">
                  Email sent
                </p>
                {parsedOutput.sentTo && (
                  <p className="text-gray-500 text-xs">
                    To: {parsedOutput.sentTo}
                  </p>
                )}
                {parsedOutput.subject && (
                  <p className="text-gray-500 text-xs">
                    Subject: {parsedOutput.subject}
                  </p>
                )}
              </div>
            </div>
          )}

          {type === "DRIVE" && parsedOutput && (
            <div className="bg-blue-500/10 border border-blue-500/20 rounded-lg px-4 py-3 flex items-center gap-3">
              <span className="text-blue-400 text-lg">📂</span>
              <p className="text-blue-400 text-sm">
                {parsedOutput.cvData?.totalCVs || parsedOutput.totalCVs || "?"}{" "}
                CVs loaded
                {parsedOutput.cvData?.folder && (
                  <span className="text-gray-500 text-xs ml-2">
                    from {parsedOutput.cvData.folder}
                  </span>
                )}
              </p>
            </div>
          )}

          {!parsedOutput && hasOutput && (
            <pre className="text-gray-400 text-xs overflow-x-auto whitespace-pre-wrap max-h-40 bg-gray-700/20 rounded-lg p-3">
              {nodeInstance.outputData.substring(0, 1000)}
            </pre>
          )}
        </div>
      )}
    </div>
  );
}
