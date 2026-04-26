"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "@/components/layouts/sidebar";
import { useAuthGuard, getUser } from "@/lib/auth";
import api from "@/lib/api";
import { getWorkflowsUrl } from "@/lib/workflows";
import toast from "react-hot-toast";

export default function ApprovalsPage() {
  const router = useRouter();
  const authReady = useAuthGuard();
  const [approvals, setApprovals] = useState<any[]>([]);
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [actioningId, setActioningId] = useState<number | null>(null);
  const [comments, setComments] = useState<Record<number, string>>({});
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const user = getUser();

  const fetchApprovals = useCallback(async () => {
    if (!user?.id) return;
    try {
      const [appRes, wfRes] = await Promise.all([
        api.get(`/executions/nodes/my-approvals?userId=${user.id}`),
        api.get(getWorkflowsUrl()),
      ]);
      setApprovals(appRes.data || []);
      setWorkflows(wfRes.data || []);
    } catch (e) {
      console.error("Failed to fetch approvals", e);
    } finally {
      setLoading(false);
    }
  }, [user?.id]);

  useEffect(() => {
    fetchApprovals();
  }, [fetchApprovals]);

  // Poll for new approvals
  useEffect(() => {
    const interval = setInterval(fetchApprovals, 10000);
    return () => clearInterval(interval);
  }, [fetchApprovals]);

  const handleApprove = async (nodeInstanceId: number) => {
    setActioningId(nodeInstanceId);
    try {
      await api.post(`/executions/nodes/${nodeInstanceId}/approve`, {
        actor: user?.email || "Unknown",
        comment: comments[nodeInstanceId] || "Approved",
      });
      toast.success("Approved successfully!");
      setComments((prev) => ({ ...prev, [nodeInstanceId]: "" }));
      fetchApprovals();
    } catch (e: any) {
      toast.error(e.response?.data?.message || "Failed to approve");
    } finally {
      setActioningId(null);
    }
  };

  const handleReject = async (nodeInstanceId: number) => {
    setActioningId(nodeInstanceId);
    try {
      await api.post(`/executions/nodes/${nodeInstanceId}/reject`, {
        actor: user?.email || "Unknown",
        comment: comments[nodeInstanceId] || "Rejected",
      });
      toast.success("Rejected successfully");
      setComments((prev) => ({ ...prev, [nodeInstanceId]: "" }));
      fetchApprovals();
    } catch (e: any) {
      toast.error(e.response?.data?.message || "Failed to reject");
    } finally {
      setActioningId(null);
    }
  };

  const parseInputData = (inputData: string) => {
    if (!inputData) return null;
    try {
      return JSON.parse(inputData);
    } catch {
      return inputData;
    }
  };

  const timeAgo = (dateStr: string) => {
    const now = new Date();
    const date = new Date(dateStr);
    const diffMs = now.getTime() - date.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return "just now";
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `${diffHr}h ago`;
    const diffDay = Math.floor(diffHr / 24);
    return `${diffDay}d ago`;
  };

  if (!authReady) return null;

  return (
    <div className="flex h-screen overflow-hidden bg-[#f8f9fb]">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
        onWorkflowDeleted={() =>
          api.get(getWorkflowsUrl()).then((r) => setWorkflows(r.data))
        }
      />

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-8">
            <div className="flex items-center gap-3 mb-1">
              <h1 className="text-2xl font-semibold text-gray-900 tracking-tight">
                My Approvals
              </h1>
              {approvals.length > 0 && (
                <span className="bg-orange-100 text-orange-700 text-xs font-semibold px-2.5 py-0.5 rounded-full">
                  {approvals.length} pending
                </span>
              )}
            </div>
            <p className="text-sm text-gray-400">
              Review and respond to workflow approval requests assigned to you
            </p>
          </div>

          {/* Content */}
          {loading ? (
            <div className="text-center py-16">
              <div className="w-8 h-8 border-[2.5px] border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
              <p className="text-gray-400 text-sm">Loading approvals…</p>
            </div>
          ) : approvals.length === 0 ? (
            <div className="text-center py-20">
              <div className="w-16 h-16 bg-gray-50 rounded-2xl flex items-center justify-center mx-auto mb-4 border border-gray-100">
                <span className="text-3xl">✅</span>
              </div>
              <p className="text-gray-500 text-base mb-1">All caught up!</p>
              <p className="text-gray-400 text-sm">
                No pending approvals at the moment
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {approvals.map((approval) => {
                const isExpanded = expandedId === approval.nodeInstanceId;
                const inputData = parseInputData(approval.inputData);
                const isActioning = actioningId === approval.nodeInstanceId;

                return (
                  <div
                    key={approval.nodeInstanceId}
                    className="bg-white border border-gray-100 rounded-xl shadow-sm overflow-hidden"
                  >
                    {/* Card Header */}
                    <div
                      className="px-5 py-4 cursor-pointer hover:bg-gray-50/50 transition-colors"
                      onClick={() =>
                        setExpandedId(
                          isExpanded ? null : approval.nodeInstanceId,
                        )
                      }
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex items-start gap-3">
                          <div className="w-9 h-9 bg-orange-50 border border-orange-100 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5">
                            <span className="text-lg">✋</span>
                          </div>
                          <div>
                            <p className="text-sm font-semibold text-gray-900">
                              {approval.workflowName}
                            </p>
                            <p className="text-xs text-gray-400 mt-0.5">
                              Requested by{" "}
                              <span className="text-gray-600">
                                {approval.triggeredByName ||
                                  approval.triggeredByEmail}
                              </span>{" "}
                              · {timeAgo(approval.createdAt)}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="bg-orange-50 text-orange-600 text-[11px] font-medium px-2 py-0.5 rounded-md border border-orange-100">
                            Waiting
                          </span>
                          <svg
                            className={`w-4 h-4 text-gray-400 transition-transform ${isExpanded ? "rotate-180" : ""}`}
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
                      </div>
                    </div>

                    {/* Expanded Content */}
                    {isExpanded && (
                      <div className="border-t border-gray-100">
                        {/* Instructions */}
                        {approval.instructions && (
                          <div className="px-5 py-3 bg-blue-50/50 border-b border-gray-100">
                            <p className="text-xs font-medium text-blue-700 mb-1">
                              📋 Instructions
                            </p>
                            <p className="text-sm text-blue-800">
                              {approval.instructions}
                            </p>
                          </div>
                        )}

                        {/* Data to Review */}
                        {inputData && (
                          <div className="px-5 py-3 border-b border-gray-100">
                            <p className="text-xs font-medium text-gray-500 mb-2">
                              Data submitted for review
                            </p>
                            <div className="bg-gray-50 border border-gray-100 rounded-lg p-3 max-h-60 overflow-y-auto">
                              <pre className="text-xs text-gray-700 whitespace-pre-wrap break-words font-mono">
                                {typeof inputData === "string"
                                  ? inputData
                                  : JSON.stringify(inputData, null, 2)}
                              </pre>
                            </div>
                          </div>
                        )}

                        {/* View Execution Link */}
                        <div className="px-5 py-2 border-b border-gray-100">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              router.push(
                                `/executions/${approval.workflowInstanceId}`,
                              );
                            }}
                            className="text-xs text-blue-600 hover:text-blue-700 font-medium flex items-center gap-1"
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
                                d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
                              />
                            </svg>
                            View full execution
                          </button>
                        </div>

                        {/* Comment + Actions */}
                        <div className="px-5 py-4">
                          <textarea
                            value={comments[approval.nodeInstanceId] || ""}
                            onChange={(e) =>
                              setComments((prev) => ({
                                ...prev,
                                [approval.nodeInstanceId]: e.target.value,
                              }))
                            }
                            placeholder="Add a comment (optional)..."
                            rows={2}
                            className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400 outline-none resize-none mb-3"
                          />
                          <div className="flex gap-2">
                            <button
                              onClick={() =>
                                handleReject(approval.nodeInstanceId)
                              }
                              disabled={isActioning}
                              className="flex-1 px-4 py-2 bg-white border border-red-200 text-red-600 hover:bg-red-50 hover:border-red-300 rounded-lg text-sm font-medium transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                              {isActioning ? "..." : "Reject"}
                            </button>
                            <button
                              onClick={() =>
                                handleApprove(approval.nodeInstanceId)
                              }
                              disabled={isActioning}
                              className="flex-1 px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-sm font-medium transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                              {isActioning ? "..." : "Approve"}
                            </button>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
