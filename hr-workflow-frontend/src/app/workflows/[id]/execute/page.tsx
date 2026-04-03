"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import api from "@/lib/api";
import Sidebar from "@/components/layouts/sidebar";
import Button from "@/components/ui/Button";
// XLSX is only needed in the downloadExcel helper, we load it dynamically to avoid
// bundler complaints about default exports and to keep it client‑side.

export default function ExecuteWorkflowPage() {
  const params = useParams();
  const router = useRouter();
  const [workflow, setWorkflow] = useState<any>(null);
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [execution, setExecution] = useState<any>(null);
  const [nodeConfigMap, setNodeConfigMap] = useState<Record<number, any>>({});

  const [inputData, setInputData] = useState(`{
  "employeeName": "John Doe",
  "department": "Engineering",
  "email": "john.doe@company.com",
  "startDate": "2026-03-15"
}`);

  const [loading, setLoading] = useState(false);
  const [executing, setExecuting] = useState(false);

  const fetchWorkflows = async () => {
    try {
      const response = await api.get("/workflows");
      setWorkflows(response.data);
    } catch (error) {
      console.error("Failed to fetch workflows:", error);
    }
  };

  const fetchWorkflow = async () => {
    try {
      const response = await api.get(`/workflows/${params.id}`);
      setWorkflow(response.data);
    } catch (error) {
      console.error("Failed to fetch workflow:", error);
    }
  };

  const fetchWorkflowNodes = async () => {
    try {
      const response = await api.get(`/nodes/workflow/${params.id}`);
      setWorkflowNodes(response.data);
    } catch (error) {
      console.error("Failed to fetch nodes:", error);
    }
  };

  useEffect(() => {
    fetchWorkflow();
    fetchWorkflows();
    fetchWorkflowNodes();
  }, [params.id]);

  const [workflowNodes, setWorkflowNodes] = useState<any[]>([]);

  const clearExecutionHistory = async () => {
    if (
      !confirm(
        "⚠️ This will delete all execution history for this workflow. Continue?",
      )
    ) {
      return;
    }

    try {
      const executionsResponse = await api.get(
        `/executions/workflow/${params.id}`,
      );

      for (const execution of executionsResponse.data) {
        await api.delete(`/executions/${execution.id}`);
      }

      alert("✅ Execution history cleared!");
    } catch (error: any) {
      console.error("Failed to clear history:", error);
      alert("❌ Failed to clear history");
    }
  };

  // helper used when excel node returns data or config to generate file on client
  const downloadExcel = async (nodeInstance: any) => {
    try {
      // If the node has outputData with fileContent, use it
      if (nodeInstance.outputData) {
        const output = JSON.parse(nodeInstance.outputData);
        if (output.fileContent) {
          // Decode base64 and download
          const byteCharacters = atob(output.fileContent);
          const byteNumbers = new Array(byteCharacters.length);
          for (let i = 0; i < byteCharacters.length; i++) {
            byteNumbers[i] = byteCharacters.charCodeAt(i);
          }
          const byteArray = new Uint8Array(byteNumbers);
          const blob = new Blob([byteArray], {
            type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          });

          const url = window.URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = `workflow-excel-${nodeInstance.id}.xlsx`;
          document.body.appendChild(a);
          a.click();
          window.URL.revokeObjectURL(url);
          document.body.removeChild(a);
          return;
        }
      }

      // Fallback: create Excel from input data (old logic)
      const XLSX = await import("xlsx");
      const config = nodeConfigMap[nodeInstance.nodeId] || {};
      const input = nodeInstance.inputData
        ? JSON.parse(nodeInstance.inputData)
        : {};

      const ws = XLSX.utils.json_to_sheet([input]);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, config.sheetName || "Data");

      const ext = (config.fileFormat || "XLSX").toLowerCase();
      const filename =
        (config.fileName || `workflow-${nodeInstance.id}`) +
        (ext === "csv" ? ".csv" : ".xlsx");

      XLSX.writeFile(wb, filename);
    } catch (e) {
      console.error("excel download failed", e);
      alert("Failed to download Excel file");
    }
  };

  // ✅ CHANGE 2: Add JSON validation
  const executeWorkflow = async () => {
    // Validate JSON first
    try {
      JSON.parse(inputData);
    } catch (e) {
      alert("❌ Invalid JSON! Please enter valid JSON format.");
      return;
    }

    setExecuting(true);
    setLoading(true);

    try {
      const user = JSON.parse(localStorage.getItem("user") || "{}");

      console.log("Executing workflow:", {
        workflowId: parseInt(params.id as string),
        inputData: inputData,
        userId: user.id,
      });

      const response = await api.post(`/executions/trigger?userId=${user.id}`, {
        workflowId: parseInt(params.id as string),
        inputData: inputData,
      });

      console.log("Execution response:", response.data);
      setExecution(response.data);

      if (response.data.id) {
        setTimeout(() => fetchExecutionDetails(response.data.id), 2000);
      }
    } catch (error: any) {
      console.error("Failed to execute workflow:", error);
      console.error("Error details:", error.response?.data);
      alert(
        error.response?.data?.message ||
          error.message ||
          "Failed to execute workflow",
      );
      setExecuting(false);
      setLoading(false);
    }
  };

  const fetchExecutionDetails = async (executionId: number) => {
    try {
      const response = await api.get(`/executions/${executionId}/detail`);
      setExecution(response.data);

      // load node configs for excel nodes so we can reconstruct files
      if (response.data.nodeInstances) {
        const configs: Record<number, any> = {};
        for (const ni of response.data.nodeInstances) {
          if (ni.nodeType === "EXCEL") {
            try {
              const nodeResp = await api.get(`/nodes/${ni.nodeId}`);
              configs[ni.nodeId] = JSON.parse(nodeResp.data.configJson || "{}");
            } catch (e) {
              console.warn("Couldn't fetch config for node", ni.nodeId, e);
            }
          }
        }
        setNodeConfigMap(configs);
      }

      setExecuting(false);
      setLoading(false);
    } catch (error) {
      console.error("Failed to fetch execution details:", error);
      setExecuting(false);
      setLoading(false);
    }
  };

  return (
    <div className="flex h-screen">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
      />

      <div className="flex-1 overflow-y-auto bg-gray-50">
        <div className="max-w-5xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-8">
            <button
              onClick={() => router.push(`/workflows/${params.id}`)}
              className="text-primary hover:text-primary/90 mb-4 flex items-center space-x-2"
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
                  d="M10 19l-7-7m0 0l7-7m-7 7h18"
                />
              </svg>
              <span>Back to Designer</span>
            </button>
            <h1 className="text-3xl font-bold text-gray-900">
              Execute Workflow
            </h1>
            <p className="text-gray-600 mt-1">{workflow?.name}</p>
          </div>
          <Button
            onClick={clearExecutionHistory}
            variant="danger"
            className="text-sm"
          >
            🗑️ Clear History
          </Button>

          {/* Execution Plan */}
          {workflowNodes.length > 0 && (
            <div className="bg-white rounded-xl shadow-sm border p-6 mb-6">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">
                📋 Execution Plan
              </h2>
              <p className="text-sm text-gray-600 mb-4">
                These nodes will execute in order:
              </p>
              <div className="flex flex-wrap gap-3">
                {workflowNodes
                  .sort((a, b) => a.order - b.order)
                  .map((node, index) => (
                    <div
                      key={node.id}
                      className="flex items-center space-x-2 bg-gray-50 px-4 py-2 rounded-lg border"
                    >
                      <span className="text-gray-500 font-mono text-sm">
                        {index + 1}
                      </span>
                      <span className="text-2xl">
                        {node.type === "EMAIL" && "📧"}
                        {node.type === "GPT" && "🤖"}
                        {node.type === "DRIVE" && "💾"}
                        {node.type === "EXCEL" && "📊"}
                      </span>
                      <span className="font-medium text-gray-900">
                        {node.type}
                      </span>
                      {index < workflowNodes.length - 1 && (
                        <span className="text-gray-400 ml-2">→</span>
                      )}
                    </div>
                  ))}
              </div>
            </div>
          )}

          {/* Input Data Card */}
          <div className="bg-white rounded-xl shadow-sm border p-6 mb-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">
              Input Data (JSON)
            </h2>
            <textarea
              value={inputData}
              onChange={(e) => setInputData(e.target.value)}
              rows={8}
              className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent font-mono text-sm"
              placeholder='{"employeeName": "John Doe", "department": "Engineering"}'
            />
            <p className="text-sm text-gray-500 mt-2">
              ℹ️ Enter input data as valid JSON. This data will be available to
              all nodes in the workflow; GPT nodes will use it to filter and
              tailor the response.
            </p>

            <div className="mt-4">
              <Button
                onClick={executeWorkflow}
                disabled={loading || workflow?.status !== "ACTIVE"}
                variant="primary"
                className="px-8 py-3 flex items-center justify-center"
              >
                Execute
              </Button>
            </div>
            {executing && (
              <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                <div className="bg-white rounded-xl p-8 max-w-md">
                  <div className="flex flex-col items-center">
                    <div className="animate-spin rounded-full h-16 w-16 border-b-4 border-primary mb-4"></div>
                    <h3 className="text-xl font-semibold text-gray-900 mb-2">
                      Executing Workflow
                    </h3>
                    <p className="text-gray-600 text-center">
                      Running {workflowNodes.length} nodes...
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Execution Results */}
          {execution && (
            <div className="bg-white rounded-xl shadow-sm border overflow-hidden">
              <div className="p-6 border-b bg-gray-50">
                <div className="flex items-center justify-between">
                  <h2 className="text-xl font-semibold text-gray-900">
                    Execution Results
                  </h2>
                  <span
                    className={`px-4 py-2 rounded-full text-sm font-semibold ${
                      execution.status === "COMPLETED"
                        ? "bg-green-100 text-green-800"
                        : execution.status === "FAILED"
                          ? "bg-red-100 text-red-800"
                          : execution.status === "RUNNING"
                            ? "bg-primary/20 text-primary/80"
                            : "bg-yellow-100 text-yellow-800"
                    }`}
                  >
                    {execution.status}
                  </span>
                </div>
              </div>

              {/* Execution Info */}
              <div className="p-6 border-b bg-gray-50">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-gray-600">Execution ID</p>
                    <p className="text-lg font-semibold text-gray-900">
                      {execution.id}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Started At</p>
                    <p className="text-lg font-semibold text-gray-900">
                      {new Date(execution.startedAt).toLocaleString()}
                    </p>
                  </div>
                  {execution.finishedAt && (
                    <>
                      <div>
                        <p className="text-sm text-gray-600">Finished At</p>
                        <p className="text-lg font-semibold text-gray-900">
                          {new Date(execution.finishedAt).toLocaleString()}
                        </p>
                      </div>
                      <div>
                        <p className="text-sm text-gray-600">Duration</p>
                        <p className="text-lg font-semibold text-gray-900">
                          {execution.durationMs
                            ? `${execution.durationMs}ms`
                            : "N/A"}
                        </p>
                      </div>
                    </>
                  )}
                </div>
              </div>

              {/* Node Instances */}
              {execution.nodeInstances &&
                execution.nodeInstances.length > 0 && (
                  <div className="p-6">
                    <h3 className="text-lg font-semibold text-gray-900 mb-4">
                      Node Execution Details
                    </h3>
                    <div className="space-y-4">
                      {execution.nodeInstances.map((nodeInstance: any) => (
                        <div
                          key={nodeInstance.id}
                          className={`border rounded-lg p-4 ${
                            nodeInstance.status === "COMPLETED"
                              ? "border-green-200 bg-green-50"
                              : nodeInstance.status === "FAILED"
                                ? "border-red-200 bg-red-50"
                                : "border-gray-200 bg-gray-50"
                          }`}
                        >
                          <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center space-x-3">
                              {/* ✅ ALREADY FIXED - nodeType fallback */}
                              <span className="text-2xl">
                                {(nodeInstance.node?.type ||
                                  nodeInstance.nodeType) === "EMAIL" && "📧"}
                                {(nodeInstance.node?.type ||
                                  nodeInstance.nodeType) === "GPT" && "🤖"}
                                {(nodeInstance.node?.type ||
                                  nodeInstance.nodeType) === "DRIVE" && "💾"}
                                {(nodeInstance.node?.type ||
                                  nodeInstance.nodeType) === "EXCEL" && "📊"}
                              </span>
                              <div>
                                <h4 className="font-semibold text-gray-900">
                                  {nodeInstance.node?.type ||
                                    nodeInstance.nodeType ||
                                    "Unknown"}{" "}
                                  Node
                                </h4>
                                <p className="text-sm text-gray-600">
                                  Execution Order: {nodeInstance.executionOrder}
                                </p>
                              </div>
                            </div>
                            <span
                              className={`px-3 py-1 rounded-full text-xs font-semibold ${
                                nodeInstance.status === "COMPLETED"
                                  ? "bg-green-100 text-green-800"
                                  : nodeInstance.status === "FAILED"
                                    ? "bg-red-100 text-red-800"
                                    : "bg-gray-100 text-gray-800"
                              }`}
                            >
                              {nodeInstance.status}
                            </span>
                          </div>

                          {nodeInstance.outputData && (
                            <div className="mt-2">
                              <p className="text-sm font-medium text-gray-700 mb-1">
                                Output:
                              </p>
                              {(() => {
                                try {
                                  const parsed = JSON.parse(
                                    nodeInstance.outputData,
                                  );
                                  return (
                                    <div className="space-y-2">
                                      {/* Show Analysis */}
                                      {parsed.analysis && (
                                        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                                          <h4 className="font-semibold text-blue-900 mb-2">
                                            AI Analysis:
                                          </h4>
                                          <p className="text-gray-800 whitespace-pre-wrap">
                                            {parsed.analysis}
                                          </p>
                                        </div>
                                      )}

                                      {/* Show Metadata */}
                                      <div className="grid grid-cols-3 gap-2 text-xs">
                                        {parsed.model && (
                                          <div className="bg-gray-50 p-2 rounded">
                                            <span className="text-gray-600">
                                              Model:
                                            </span>
                                            <span className="ml-1 font-medium">
                                              {parsed.model}
                                            </span>
                                          </div>
                                        )}
                                        {parsed.tokensUsed && (
                                          <div className="bg-gray-50 p-2 rounded">
                                            <span className="text-gray-600">
                                              Tokens:
                                            </span>
                                            <span className="ml-1 font-medium">
                                              {parsed.tokensUsed}
                                            </span>
                                          </div>
                                        )}
                                        {parsed.analyzedAt && (
                                          <div className="bg-gray-50 p-2 rounded">
                                            <span className="text-gray-600">
                                              Time:
                                            </span>
                                            <span className="ml-1 font-medium">
                                              {new Date(
                                                parsed.analyzedAt,
                                              ).toLocaleTimeString()}
                                            </span>
                                          </div>
                                        )}
                                      </div>

                                      {/* Show Raw JSON (collapsible) */}
                                      <details className="text-xs">
                                        <summary className="cursor-pointer text-gray-600 hover:text-gray-900">
                                          Show raw JSON
                                        </summary>
                                        <pre className="mt-2 p-2 bg-gray-900 text-green-400 rounded overflow-x-auto">
                                          {JSON.stringify(parsed, null, 2)}
                                        </pre>
                                      </details>
                                    </div>
                                  );
                                } catch (e) {
                                  // If not JSON, show as text
                                  return (
                                    <pre className="text-sm bg-gray-50 p-3 rounded border overflow-x-auto whitespace-pre-wrap">
                                      {nodeInstance.outputData}
                                    </pre>
                                  );
                                }
                              })()}
                            </div>
                          )}

                          {nodeInstance.node?.type === "DRIVE" &&
                            nodeInstance.outputData &&
                            (() => {
                              try {
                                const driveData = JSON.parse(
                                  nodeInstance.outputData,
                                );
                                return (
                                  <div className="mt-2 space-y-2">
                                    <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                                      <h4 className="font-semibold text-green-900 mb-2">
                                        📁 File Saved Successfully
                                      </h4>
                                      <div className="space-y-1 text-sm">
                                        <p>
                                          <span className="text-gray-600">
                                            File:
                                          </span>{" "}
                                          <span className="font-medium">
                                            {driveData.fileName}
                                          </span>
                                        </p>
                                        <p>
                                          <span className="text-gray-600">
                                            Size:
                                          </span>{" "}
                                          <span className="font-medium">
                                            {(driveData.size / 1024).toFixed(2)}{" "}
                                            KB
                                          </span>
                                        </p>
                                        <p>
                                          <span className="text-gray-600">
                                            Location:
                                          </span>{" "}
                                          <span className="font-mono text-xs">
                                            {driveData.webViewLink}
                                          </span>
                                        </p>
                                      </div>
                                      <button
                                        onClick={() => {
                                          // Copy path to clipboard
                                          navigator.clipboard.writeText(
                                            driveData.webViewLink,
                                          );
                                          alert(
                                            "✅ File path copied to clipboard!",
                                          );
                                        }}
                                        className="mt-3 bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700 text-sm"
                                      >
                                        📋 Copy File Path
                                      </button>
                                    </div>
                                  </div>
                                );
                              } catch (e) {
                                return null;
                              }
                            })()}

                          {nodeInstance.errorMessage && (
                            <div className="mt-3">
                              <p className="text-sm font-medium text-red-700 mb-1">
                                Error:
                              </p>
                              <pre className="bg-red-50 p-3 rounded border border-red-200 text-xs text-red-800 overflow-x-auto">
                                {nodeInstance.errorMessage}
                              </pre>
                            </div>
                          )}

                          {(nodeInstance.node?.type ||
                            nodeInstance.nodeType) === "EXCEL" &&
                            nodeInstance.outputData &&
                            (() => {
                              try {
                                const excelData = JSON.parse(
                                  nodeInstance.outputData,
                                );
                                return (
                                  <div className="mt-2 space-y-2">
                                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                                      <h4 className="font-semibold text-blue-900 mb-2">
                                        📊 Excel File Generated
                                      </h4>
                                      <div className="space-y-1 text-sm">
                                        <p>
                                          <span className="text-gray-600">
                                            Sheet:
                                          </span>{" "}
                                          <span className="font-medium">
                                            {excelData.sheetName}
                                          </span>
                                        </p>
                                        <p>
                                          <span className="text-gray-600">
                                            Rows:
                                          </span>{" "}
                                          <span className="font-medium">
                                            {excelData.rowsProcessed}
                                          </span>
                                        </p>
                                        <p>
                                          <span className="text-gray-600">
                                            Columns:
                                          </span>{" "}
                                          <span className="font-medium">
                                            {excelData.columnsProcessed}
                                          </span>
                                        </p>
                                        <p>
                                          <span className="text-gray-600">
                                            Size:
                                          </span>{" "}
                                          <span className="font-medium">
                                            {(
                                              excelData.fileSize / 1024
                                            ).toFixed(2)}{" "}
                                            KB
                                          </span>
                                        </p>
                                      </div>
                                      <button
                                        onClick={() =>
                                          downloadExcel(nodeInstance)
                                        }
                                        className="mt-3 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 text-sm"
                                      >
                                        📥 Download Excel File
                                      </button>
                                    </div>
                                  </div>
                                );
                              } catch (e) {
                                return null;
                              }
                            })()}

                          <div className="mt-3 flex items-center space-x-4 text-xs text-gray-600">
                            {nodeInstance.startedAt && (
                              <span>
                                Started:{" "}
                                {new Date(
                                  nodeInstance.startedAt,
                                ).toLocaleTimeString()}
                              </span>
                            )}
                            {nodeInstance.finishedAt && (
                              <span>
                                Finished:{" "}
                                {new Date(
                                  nodeInstance.finishedAt,
                                ).toLocaleTimeString()}
                              </span>
                            )}
                            {nodeInstance.durationMs && (
                              <span>Duration: {nodeInstance.durationMs}ms</span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

              {/* Overall Output */}
              {execution.outputData && (
                <div className="p-6 border-t bg-gray-50">
                  <h3 className="text-lg font-semibold text-gray-900 mb-2">
                    Final Output
                  </h3>
                  <pre className="bg-white p-4 rounded border text-sm overflow-x-auto">
                    {execution.outputData}
                  </pre>
                </div>
              )}

              {/* Error */}
              {execution.errorMessage && (
                <div className="p-6 border-t bg-red-50">
                  <h3 className="text-lg font-semibold text-red-900 mb-2">
                    Error
                  </h3>
                  <pre className="bg-white p-4 rounded border border-red-200 text-sm text-red-800 overflow-x-auto">
                    {execution.errorMessage}
                  </pre>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
