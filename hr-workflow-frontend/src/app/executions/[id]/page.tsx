"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import api from "@/lib/api";
import Sidebar from "@/components/layouts/sidebar";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function duration(ms?: number) {
  if (!ms) return "—";
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;
}

function fmtTime(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString([], {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

const NODE_META: Record<
  string,
  { icon: string; color: string; bg: string; label: string }
> = {
  GPT: { icon: "◈", color: "#a78bfa", bg: "#1e1b4b", label: "AI / GPT" },
  DRIVE: { icon: "◉", color: "#60a5fa", bg: "#1e3a5f", label: "Drive" },
  EXCEL: { icon: "▦", color: "#34d399", bg: "#064e3b", label: "Excel" },
  EMAIL: { icon: "✉", color: "#fbbf24", bg: "#451a03", label: "Email" },
};

// ─── Sub-components ───────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: string }) {
  const cfg: Record<string, string> = {
    COMPLETED: "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
    FAILED: "bg-red-500/10    text-red-400    border-red-500/20",
    RUNNING: "bg-blue-500/10  text-blue-400   border-blue-500/20",
    PENDING: "bg-amber-500/10 text-amber-400  border-amber-500/20",
  };
  const dot: Record<string, string> = {
    COMPLETED: "bg-emerald-400",
    FAILED: "bg-red-400",
    RUNNING: "bg-blue-400 animate-pulse",
    PENDING: "bg-amber-400",
  };
  return (
    <span
      className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full border ${cfg[status] || cfg["PENDING"]}`}
    >
      <span
        className={`w-1.5 h-1.5 rounded-full ${dot[status] || dot["PENDING"]}`}
      />
      {status}
    </span>
  );
}

function CandidateCard({ candidate, rank }: { candidate: any; rank: number }) {
  const score = Number(candidate.score ?? 0);
  const pct = Math.min((score / 5) * 100, 100);
  const color = score >= 4 ? "#34d399" : score >= 2.5 ? "#fbbf24" : "#f87171";

  return (
    <div className="bg-white/[0.03] border border-white/[0.08] rounded-xl p-4 hover:border-white/[0.15] transition-colors">
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-white/[0.06] border border-white/[0.1] flex items-center justify-center text-xs font-bold text-white/40">
            {rank}
          </div>
          <div>
            <p className="font-semibold text-white/90 text-sm">
              {candidate.name}
            </p>
            <p className="text-xs text-white/40">{candidate.email}</p>
          </div>
        </div>
        <div className="text-right shrink-0 ml-3">
          <span className="text-xl font-bold text-white">{score}</span>
          <span className="text-white/30 text-xs">/5</span>
        </div>
      </div>

      {/* Score bar */}
      <div className="h-1 bg-white/[0.06] rounded-full overflow-hidden mb-3">
        <div
          className="h-full rounded-full transition-all"
          style={{ width: `${pct}%`, background: color }}
        />
      </div>

      <div className="flex flex-wrap gap-2">
        {candidate.experience != null && (
          <span className="text-xs bg-white/[0.05] text-white/50 px-2 py-0.5 rounded-full border border-white/[0.07]">
            {candidate.experience} yrs
          </span>
        )}
        {String(candidate.skills || "")
          .split(/[•,·\/]/)
          .map((s: string) => s.trim())
          .filter(Boolean)
          .map((skill: string, i: number) => (
            <span
              key={i}
              className="text-xs bg-violet-500/10 text-violet-300 px-2 py-0.5 rounded-full border border-violet-500/20"
            >
              {skill}
            </span>
          ))}
      </div>
    </div>
  );
}

function NodeOutput({ nodeInstance }: { nodeInstance: any }) {
  const type = nodeInstance.node?.type || nodeInstance.nodeType || "UNKNOWN";
  const [showRaw, setShowRaw] = useState(false);

  const downloadExcel = async () => {
    try {
      const output = JSON.parse(nodeInstance.outputData || "{}");
      if (output.fileContent) {
        const bytes = atob(output.fileContent);
        const arr = new Uint8Array(bytes.length);
        for (let i = 0; i < bytes.length; i++) arr[i] = bytes.charCodeAt(i);
        const blob = new Blob([arr], {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `result-${nodeInstance.id}.xlsx`;
        document.body.appendChild(a);
        a.click();
        URL.revokeObjectURL(url);
        a.remove();
        return;
      }
      const XLSX = await import("xlsx");
      const input = nodeInstance.inputData
        ? JSON.parse(nodeInstance.inputData)
        : {};
      const ws = XLSX.utils.json_to_sheet(
        Array.isArray(input) ? input : [input],
      );
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, "Data");
      XLSX.writeFile(wb, `result-${nodeInstance.id}.xlsx`);
    } catch {
      alert("Download failed");
    }
  };

  if (!nodeInstance.outputData) {
    return <p className="text-white/25 text-sm italic">No output data</p>;
  }

  // ── GPT node ──────────────────────────────────────────────────────────────
  if (type === "GPT") {
    try {
      const parsed = JSON.parse(nodeInstance.outputData);
      const analysis = parsed.analysis || "";

      let candidates: any[] | null = null;
      try {
        const arr = JSON.parse(analysis);
        if (Array.isArray(arr)) candidates = arr;
      } catch {}

      return (
        <div className="space-y-5">
          {/* Meta chips */}
          <div className="flex flex-wrap gap-2">
            {parsed.model && (
              <span className="text-xs bg-violet-500/10 text-violet-300 px-2.5 py-1 rounded-full border border-violet-500/20">
                {parsed.model}
              </span>
            )}
            {parsed.tokensUsed && (
              <span className="text-xs bg-white/[0.05] text-white/40 px-2.5 py-1 rounded-full border border-white/[0.08]">
                {parsed.tokensUsed} tokens
              </span>
            )}
            {parsed.analyzedAt && (
              <span className="text-xs text-white/25">
                {fmtTime(parsed.analyzedAt)}
              </span>
            )}
          </div>

          {/* Candidate cards */}
          {candidates ? (
            <div>
              <p className="text-xs text-white/25 uppercase tracking-widest font-medium mb-3">
                {candidates.length} candidate
                {candidates.length !== 1 ? "s" : ""} ranked
              </p>
              <div className="space-y-3">
                {candidates.map((c, i) => (
                  <CandidateCard key={i} candidate={c} rank={i + 1} />
                ))}
              </div>
            </div>
          ) : (
            <div className="bg-white/[0.03] border border-white/[0.08] rounded-xl p-4">
              <p className="text-white/70 text-sm whitespace-pre-wrap leading-relaxed">
                {analysis || JSON.stringify(parsed, null, 2)}
              </p>
            </div>
          )}

          {/* Raw JSON toggle */}
          <button
            onClick={() => setShowRaw((v) => !v)}
            className="text-xs text-white/25 hover:text-white/50 transition-colors"
          >
            {showRaw ? "▲ Hide" : "▼ Show"} raw JSON
          </button>
          {showRaw && (
            <pre className="bg-black/40 border border-white/[0.06] rounded-xl p-4 text-xs text-green-400/80 overflow-x-auto leading-relaxed">
              {JSON.stringify(parsed, null, 2)}
            </pre>
          )}
        </div>
      );
    } catch {}
  }

  // ── EXCEL node ────────────────────────────────────────────────────────────
  if (type === "EXCEL") {
    try {
      const d = JSON.parse(nodeInstance.outputData);
      return (
        <div className="bg-white/[0.03] border border-white/[0.08] rounded-xl p-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-white/80 mb-2">
                Excel file generated
              </p>
              <div className="flex flex-wrap gap-3 text-xs text-white/40">
                {d.sheetName && (
                  <span>
                    Sheet: <span className="text-white/60">{d.sheetName}</span>
                  </span>
                )}
                {d.rowsProcessed && <span>{d.rowsProcessed} rows</span>}
                {d.columnsProcessed && <span>{d.columnsProcessed} cols</span>}
                {d.fileSize && <span>{(d.fileSize / 1024).toFixed(1)} KB</span>}
              </div>
            </div>
            <button
              onClick={downloadExcel}
              className="shrink-0 flex items-center gap-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium px-3 py-2 rounded-lg transition-colors"
            >
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
                  d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
                />
              </svg>
              Download .xlsx
            </button>
          </div>
        </div>
      );
    } catch {}
  }

  // ── DRIVE node ────────────────────────────────────────────────────────────
  if (type === "DRIVE") {
    try {
      const d = JSON.parse(nodeInstance.outputData);
      // Combined read output
      if (d.cvData || d.originalInput) {
        return (
          <div className="bg-white/[0.03] border border-white/[0.08] rounded-xl p-4 text-sm">
            <p className="text-white/50">
              Read{" "}
              <span className="text-white/80 font-medium">
                {d.cvData?.totalCVs ?? "?"} files
              </span>{" "}
              from{" "}
              <span className="text-white/80 font-mono">
                /{d.cvData?.folder}
              </span>
            </p>
            {d.originalInput?.prompt && (
              <p className="text-xs text-white/30 mt-2 italic">
                User prompt: "{d.originalInput.prompt}"
              </p>
            )}
          </div>
        );
      }
      // Write output
      return (
        <div className="bg-white/[0.03] border border-white/[0.08] rounded-xl p-4">
          <p className="text-sm font-medium text-white/80">{d.fileName}</p>
          <p className="text-xs text-white/30 mt-1 font-mono">
            {d.webViewLink}
          </p>
          {d.size && (
            <p className="text-xs text-white/30">
              {(d.size / 1024).toFixed(1)} KB
            </p>
          )}
          <button
            onClick={() => {
              navigator.clipboard.writeText(d.webViewLink);
            }}
            className="mt-3 text-xs bg-white/[0.06] hover:bg-white/[0.1] text-white/50 px-3 py-1.5 rounded-lg transition-colors"
          >
            📋 Copy path
          </button>
        </div>
      );
    } catch {}
  }

  // ── Fallback ──────────────────────────────────────────────────────────────
  try {
    const parsed = JSON.parse(nodeInstance.outputData);
    return (
      <pre className="bg-black/40 border border-white/[0.06] rounded-xl p-4 text-xs text-green-400/80 overflow-x-auto whitespace-pre-wrap leading-relaxed">
        {JSON.stringify(parsed, null, 2)}
      </pre>
    );
  } catch {
    return (
      <pre className="bg-black/40 border border-white/[0.06] rounded-xl p-4 text-xs text-white/40 overflow-x-auto whitespace-pre-wrap">
        {nodeInstance.outputData}
      </pre>
    );
  }
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function ExecutionDetailPage() {
  const params = useParams();
  const router = useRouter();

  const [execution, setExecution] = useState<any>(null);
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchExecution = useCallback(async () => {
    try {
      const [exRes, wfRes] = await Promise.all([
        api.get(`/executions/${params.id}/detail`),
        api.get("/workflows"),
      ]);

      const data = exRes.data;
      const wfs = wfRes.data;

      // Attach workflow name
      const wf = wfs.find((w: any) => w.id === data.workflowId);
      data.workflowName = wf?.name || `Workflow #${data.workflowId}`;

      setExecution(data);
      setWorkflows(wfs);

      // Auto-select most interesting node: GPT first, then EXCEL, then first
      const nodes: any[] = data.nodeInstances || [];
      const sorted = [...nodes].sort(
        (a, b) => (a.executionOrder ?? 0) - (b.executionOrder ?? 0),
      );
      const gpt = sorted.find((n) => (n.node?.type || n.nodeType) === "GPT");
      const excel = sorted.find(
        (n) => (n.node?.type || n.nodeType) === "EXCEL",
      );
      setSelectedNodeId(gpt?.id ?? excel?.id ?? sorted[0]?.id ?? null);
    } catch (err: any) {
      console.error("Failed to load execution", err);
      setError(
        err.response?.data?.message || "Failed to load execution details",
      );
    } finally {
      setLoading(false);
    }
  }, [params.id]);

  useEffect(() => {
    fetchExecution();
  }, [fetchExecution]);

  const selectedNode = execution?.nodeInstances?.find(
    (n: any) => n.id === selectedNodeId,
  );
  const nodes: any[] = (execution?.nodeInstances || []).sort(
    (a: any, b: any) => (a.executionOrder ?? 0) - (b.executionOrder ?? 0),
  );

  // ── Loading ──────────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="flex h-screen bg-[#0f0f0f]">
        <Sidebar
          workflows={workflows}
          onCreateWorkflow={() => router.push("/dashboard")}
        />
        <div className="flex-1 flex items-center justify-center">
          <div className="flex flex-col items-center gap-3">
            <div className="w-8 h-8 border-2 border-white/10 border-t-white/50 rounded-full animate-spin" />
            <p className="text-white/30 text-sm">Loading execution…</p>
          </div>
        </div>
      </div>
    );
  }

  // ── Error ────────────────────────────────────────────────────────────────
  if (error || !execution) {
    return (
      <div className="flex h-screen bg-[#0f0f0f]">
        <Sidebar
          workflows={workflows}
          onCreateWorkflow={() => router.push("/dashboard")}
        />
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center">
            <p className="text-white/40 mb-2">
              {error || "Execution not found"}
            </p>
            <button
              onClick={() => router.push("/executions")}
              className="text-sm text-white/50 hover:text-white/80 underline underline-offset-2"
            >
              ← Back to Executions
            </button>
          </div>
        </div>
      </div>
    );
  }

  const ok = execution.status === "COMPLETED";
  const fail = execution.status === "FAILED";

  // ── Page ─────────────────────────────────────────────────────────────────
  return (
    <div className="flex h-screen bg-[#0f0f0f]">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
      />

      <div className="flex-1 flex flex-col overflow-hidden">
        {/* ── Top bar ── */}
        <div className="flex items-center justify-between px-6 py-3.5 border-b border-white/[0.06] shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <button
              onClick={() => router.push("/executions")}
              className="flex items-center gap-1.5 text-sm text-white/35 hover:text-white/70 transition-colors shrink-0"
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
                  strokeWidth={1.5}
                  d="M10 19l-7-7m0 0l7-7m-7 7h18"
                />
              </svg>
              Executions
            </button>
            <span className="text-white/15">/</span>
            <span className="text-white/55 text-sm truncate">
              {execution.workflowName}
            </span>
            <span className="text-white/15">#</span>
            <span className="text-white/35 text-sm font-mono">
              {execution.id}
            </span>
          </div>

          <div className="flex items-center gap-3 shrink-0">
            <span
              className={`flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-full border ${
                ok
                  ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/20"
                  : fail
                    ? "bg-red-500/10 text-red-400 border-red-500/20"
                    : "bg-amber-500/10 text-amber-400 border-amber-500/20"
              }`}
            >
              <span
                className={`w-1.5 h-1.5 rounded-full ${ok ? "bg-emerald-400" : fail ? "bg-red-400" : "bg-amber-400 animate-pulse"}`}
              />
              {execution.status}
            </span>
            <span className="text-xs text-white/20 font-mono hidden sm:block">
              {fmtDate(execution.startedAt)}
              {execution.durationMs && ` · ${duration(execution.durationMs)}`}
            </span>
          </div>
        </div>

        {/* ── Body ── */}
        <div className="flex-1 flex overflow-hidden">
          {/* Left: node list */}
          <div className="w-56 border-r border-white/[0.06] flex flex-col shrink-0 overflow-y-auto">
            <div className="px-4 pt-5 pb-2">
              <p className="text-xs text-white/25 uppercase tracking-widest font-medium">
                Nodes · {nodes.length}
              </p>
            </div>
            <div className="px-2 pb-4 space-y-1">
              {nodes.map((ni) => {
                const type = ni.node?.type || ni.nodeType || "UNKNOWN";
                const meta = NODE_META[type] || {
                  icon: "○",
                  color: "#fff",
                  bg: "#111",
                  label: type,
                };
                const isSelected = ni.id === selectedNodeId;
                const ok2 = ni.status === "COMPLETED";
                const fail2 = ni.status === "FAILED";

                return (
                  <button
                    key={ni.id}
                    onClick={() => setSelectedNodeId(ni.id)}
                    className={`w-full text-left px-3 py-3 rounded-xl border transition-all ${
                      isSelected
                        ? "border-white/[0.18] bg-white/[0.06]"
                        : "border-white/[0.05] bg-white/[0.01] hover:border-white/[0.1] hover:bg-white/[0.03]"
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2.5">
                        <div
                          className="w-7 h-7 rounded-lg flex items-center justify-center text-xs font-bold shrink-0"
                          style={{ background: meta.bg, color: meta.color }}
                        >
                          {meta.icon}
                        </div>
                        <div>
                          <p className="text-xs font-medium text-white/70">
                            {meta.label}
                          </p>
                          <p className="text-xs text-white/25">
                            {duration(ni.durationMs)}
                          </p>
                        </div>
                      </div>
                      <div
                        className={`w-2 h-2 rounded-full shrink-0 ${
                          ok2
                            ? "bg-emerald-400"
                            : fail2
                              ? "bg-red-400"
                              : "bg-amber-400 animate-pulse"
                        }`}
                      />
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right: selected node output */}
          <div className="flex-1 overflow-y-auto">
            {selectedNode ? (
              <div className="p-6 max-w-3xl">
                {/* Node header */}
                {(() => {
                  const type =
                    selectedNode.node?.type ||
                    selectedNode.nodeType ||
                    "UNKNOWN";
                  const meta = NODE_META[type] || {
                    icon: "○",
                    color: "#fff",
                    bg: "#111",
                    label: type,
                  };
                  const ok3 = selectedNode.status === "COMPLETED";
                  const fail3 = selectedNode.status === "FAILED";
                  return (
                    <div className="flex items-center justify-between mb-6">
                      <div className="flex items-center gap-3">
                        <div
                          className="w-10 h-10 rounded-xl flex items-center justify-center text-base font-bold shrink-0"
                          style={{ background: meta.bg, color: meta.color }}
                        >
                          {meta.icon}
                        </div>
                        <div>
                          <p className="text-white font-semibold">
                            {meta.label} Node
                          </p>
                          <p className="text-xs text-white/30 mt-0.5">
                            Order {selectedNode.executionOrder}
                            {selectedNode.startedAt &&
                              ` · started ${fmtTime(selectedNode.startedAt)}`}
                            {selectedNode.durationMs &&
                              ` · ${duration(selectedNode.durationMs)}`}
                          </p>
                        </div>
                      </div>
                      <span
                        className={`text-xs font-semibold px-2.5 py-1 rounded-full border ${
                          ok3
                            ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/20"
                            : fail3
                              ? "bg-red-500/10 text-red-400 border-red-500/20"
                              : "bg-amber-500/10 text-amber-400 border-amber-500/20"
                        }`}
                      >
                        {selectedNode.status}
                      </span>
                    </div>
                  );
                })()}

                {/* Output section */}
                <div className="mb-6">
                  <p className="text-xs text-white/25 uppercase tracking-widest font-medium mb-3">
                    Output
                  </p>
                  <NodeOutput nodeInstance={selectedNode} />
                </div>

                {/* Error section */}
                {selectedNode.errorMessage && (
                  <div className="mt-4">
                    <p className="text-xs text-white/25 uppercase tracking-widest font-medium mb-3">
                      Error
                    </p>
                    <pre className="bg-red-950/40 border border-red-500/20 rounded-xl p-4 text-xs text-red-300 overflow-x-auto whitespace-pre-wrap leading-relaxed">
                      {selectedNode.errorMessage}
                    </pre>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex items-center justify-center h-full">
                <p className="text-white/20 text-sm">
                  Select a node on the left to see its output
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Bottom error bar for workflow-level errors */}
        {fail && execution.errorMessage && (
          <div className="px-6 py-3 border-t border-red-500/20 bg-red-950/20 shrink-0">
            <p className="text-xs text-red-400">
              <span className="font-semibold">Workflow error:</span>{" "}
              {execution.errorMessage}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
