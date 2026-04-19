"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuthGuard } from "@/lib/auth";
import api from "@/lib/api";
import Sidebar from "@/components/layouts/sidebar";

export default function ExecuteWorkflowPage() {
  const params = useParams();
  const router = useRouter();
  const authReady = useAuthGuard();
  const [workflow, setWorkflow] = useState<any>(null);
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [workflowNodes, setWorkflowNodes] = useState<any[]>([]);
  const [prompt, setPrompt] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const fetchAll = async () => {
      try {
        const [wfRes, allRes, nodesRes] = await Promise.all([
          api.get(`/workflows/${params.id}`),
          api.get("/workflows"),
          api.get(`/nodes/workflow/${params.id}`),
        ]);
        setWorkflow(wfRes.data);
        setWorkflows(allRes.data);
        setWorkflowNodes(nodesRes.data);
      } catch (err) {}
    };
    fetchAll();
  }, [params.id]);

  const executeWorkflow = async () => {
    if (!prompt.trim() || submitting) return;
    setSubmitting(true);

    try {
      const user = JSON.parse(localStorage.getItem("user") || "{}");
      const inputData = JSON.stringify({ prompt: prompt.trim() });

      const response = await api.post(`/executions/trigger?userId=${user.id}`, {
        workflowId: parseInt(params.id as string),
        inputData,
      });

      const executionId = response.data?.id;

      if (executionId) {
        router.push(`/executions/${executionId}`);
      } else {
        router.push("/dashboard");
      }
    } catch (error: any) {
      setSubmitting(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
      e.preventDefault();
      executeWorkflow();
    }
  };

  const nodeIcons: Record<string, string> = {
    EMAIL: "📧",
    GPT: "🤖",
    DRIVE: "💾",
    EXCEL: "📊",
  };
  if (!authReady) return null;
  const isActive = workflow?.status === "ACTIVE";

  return (
    <div className="flex h-screen bg-[#f8f9fb]">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
        onWorkflowDeleted={() =>
          api.get("/workflows").then((r) => setWorkflows(r.data))
        }
      />

      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top bar */}
        <div className="flex items-center justify-between px-8 py-4 bg-white border-b border-gray-100">
          <div className="flex items-center gap-3">
            <button
              onClick={() => router.push(`/workflows/${params.id}`)}
              className="flex items-center gap-2 text-sm text-gray-400 hover:text-gray-700 transition-colors font-medium"
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
              Back to Designer
            </button>
            <span className="text-gray-200">·</span>
            <span className="text-gray-700 font-medium text-sm">
              {workflow?.name || "..."}
            </span>
          </div>
          <span
            className={`text-[11px] px-2.5 py-1 rounded-full font-semibold border ${
              isActive
                ? "bg-emerald-50 text-emerald-600 border-emerald-200"
                : "bg-amber-50 text-amber-600 border-amber-200"
            }`}
          >
            {isActive ? "● ACTIVE" : "○ INACTIVE"}
          </span>
        </div>

        {/* Main content */}
        <div className="flex-1 overflow-y-auto">
          <div className="max-w-2xl mx-auto px-6 py-12">
            {/* Title */}
            <div className="mb-8">
              <h1 className="text-2xl font-semibold text-gray-900 tracking-tight">
                Run Workflow
              </h1>
              <p className="text-gray-400 text-sm mt-1.5 leading-relaxed">
                Describe what you want — the AI will process it through the
                workflow nodes. You'll be taken to the live execution tracker.
              </p>
            </div>

            {/* Pipeline */}
            {workflowNodes.length > 0 && (
              <div className="mb-8">
                <p className="text-[11px] text-gray-400 uppercase tracking-widest font-semibold mb-3">
                  Pipeline
                </p>
                <div className="flex items-center flex-wrap gap-0">
                  {workflowNodes
                    .sort(
                      (a, b) =>
                        (a.orderIndex ?? a.order ?? 0) -
                        (b.orderIndex ?? b.order ?? 0),
                    )
                    .map((node, idx) => (
                      <div key={node.id} className="flex items-center">
                        <div className="flex items-center gap-2 px-3.5 py-2 rounded-lg border border-gray-200 bg-white text-sm shadow-sm">
                          <span>{nodeIcons[node.type] || "○"}</span>
                          <span className="font-medium text-gray-700 text-[13px]">
                            {node.type}
                          </span>
                        </div>
                        {idx < workflowNodes.length - 1 && (
                          <div className="w-6 flex items-center justify-center">
                            <svg
                              className="w-4 h-4 text-gray-300"
                              fill="none"
                              stroke="currentColor"
                              viewBox="0 0 24 24"
                            >
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M9 5l7 7-7 7"
                              />
                            </svg>
                          </div>
                        )}
                      </div>
                    ))}
                </div>
              </div>
            )}

            {/* Prompt input */}
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
              <div className="px-5 pt-5 pb-1">
                <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-widest mb-3">
                  Your request
                </p>
                <textarea
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={submitting || !isActive}
                  rows={6}
                  placeholder={
                    `Just describe what you want in plain language:\n\n` +
                    `• "Find me senior software engineers with Java and Spring, 5+ years experience"\n` +
                    `• "Top 3 candidates for a DevOps role with Docker and Kubernetes"\n` +
                    `• "Who is the best match for a React frontend position?"`
                  }
                  className="w-full text-gray-800 text-sm leading-relaxed resize-none outline-none placeholder-gray-300 bg-transparent"
                />
              </div>

              {/* Bottom bar */}
              <div className="flex items-center justify-between px-5 py-3 border-t border-gray-100 bg-gray-50/60">
                <span className="text-xs text-gray-400">
                  {prompt.length > 0 ? `${prompt.length} chars · ` : ""}⌘ Enter
                  to run
                </span>
                <button
                  onClick={executeWorkflow}
                  disabled={submitting || !prompt.trim() || !isActive}
                  className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                    submitting || !prompt.trim() || !isActive
                      ? "bg-gray-100 text-gray-300 cursor-not-allowed"
                      : "bg-blue-600 text-white hover:bg-blue-700 active:scale-[0.97] cursor-pointer shadow-sm"
                  }`}
                >
                  {submitting ? (
                    <>
                      <div className="w-3.5 h-3.5 border-2 border-blue-300 border-t-white rounded-full animate-spin" />
                      Starting…
                    </>
                  ) : (
                    <>
                      <svg
                        className="w-3.5 h-3.5"
                        fill="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path d="M8 5v14l11-7z" />
                      </svg>
                      Run Workflow
                    </>
                  )}
                </button>
              </div>
            </div>

            {!isActive && (
              <div className="mt-3 flex items-center gap-2 text-sm text-amber-600 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3">
                <svg
                  className="w-4 h-4 shrink-0"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
                This workflow is inactive. Open the designer and activate it
                first.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
