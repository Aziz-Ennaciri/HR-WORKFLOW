"use client";

import { useState, useEffect } from "react";
import Button from "@/components/ui/Button";
import api from "@/lib/api";

interface NodeConfigPanelProps {
  node: any;
  onUpdate: (config: any) => void;
  onClose: () => void;
}

export default function NodeConfigPanel({
  node,
  onUpdate,
  onClose,
}: NodeConfigPanelProps) {
  const [config, setConfig] = useState(node?.data?.config || {});
  const [adminUsers, setAdminUsers] = useState<any[]>([]);
  const [loadingAdmins, setLoadingAdmins] = useState(false);

  useEffect(() => {
    setConfig(node?.data?.config || {});
  }, [node]);

  // Fetch admin users when APPROVAL node is selected
  useEffect(() => {
    if (node?.data?.label === "APPROVAL") {
      setLoadingAdmins(true);
      api
        .get("/users/by-role?role=ROLE_ADMIN")
        .then((res) => setAdminUsers(res.data || []))
        .catch(() => setAdminUsers([]))
        .finally(() => setLoadingAdmins(false));
    }
  }, [node?.data?.label]);

  if (!node) return null;

  const handleSave = () => {
    if (node?.data?.label === "DRIVE") {
      if (!config.folderId?.trim()) {
        alert("Folder ID is required. Enter the subfolder name where files are stored.");
        return;
      }
    }
    onUpdate(config);
    onClose();
  };

  const renderConfigFields = () => {
    switch (node.data.label) {
      case "EMAIL":
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                To *
              </label>
              <input
                type="email"
                value={config.to || ""}
                onChange={(e) => setConfig({ ...config, to: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                placeholder="recipient@example.com"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Subject
              </label>
              <input
                type="text"
                value={config.subject || ""}
                onChange={(e) =>
                  setConfig({ ...config, subject: e.target.value })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                placeholder="Email subject"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Body
              </label>
              <textarea
                value={config.body || ""}
                onChange={(e) => setConfig({ ...config, body: e.target.value })}
                rows={4}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                placeholder="Email message"
              />
            </div>
          </>
        );

      case "GPT":
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Model *
              </label>
              <select
                value={config.model || "gpt-4"}
                onChange={(e) =>
                  setConfig({ ...config, model: e.target.value })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                <option value="gpt-4">GPT-4</option>
                <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Prompt *
              </label>
              <textarea
                value={config.prompt || ""}
                onChange={(e) =>
                  setConfig({ ...config, prompt: e.target.value })
                }
                rows={4}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                placeholder="Enter your prompt here..."
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Temperature
              </label>
              <input
                type="number"
                min="0"
                max="2"
                step="0.1"
                value={config.temperature || 0.7}
                onChange={(e) =>
                  setConfig({
                    ...config,
                    temperature: parseFloat(e.target.value),
                  })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
              />
            </div>
          </>
        );

      case "DRIVE": {
        const isReadAction = (config.action || "write") === "read";
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Action *
              </label>
              <select
                name="action"
                value={config.action || "write"}
                onChange={(e) =>
                  setConfig({ ...config, action: e.target.value })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                <option value="read">Read Folder</option>
                <option value="write">Upload File</option>
                <option value="download">Download File</option>
                <option value="create_folder">Create Folder</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Folder ID *
              </label>
              <input
                type="text"
                value={config.folderId || ""}
                onChange={(e) =>
                  setConfig({ ...config, folderId: e.target.value })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                placeholder="e.g. cvs"
              />
              <p className="text-xs text-gray-400 mt-1">
                Subfolder name inside the storage directory (e.g.{" "}
                <span className="font-mono">cvs</span> →{" "}
                <span className="font-mono">/storage/cvs/</span>)
              </p>
            </div>
            {!isReadAction && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  File Name
                </label>
                <input
                  type="text"
                  value={config.fileName || ""}
                  onChange={(e) =>
                    setConfig({ ...config, fileName: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                  placeholder="example.pdf"
                />
              </div>
            )}
          </>
        );
      }

      case "EXCEL":
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Operation *
              </label>
              <select
                value={config.operation || "READ"}
                onChange={(e) =>
                  setConfig({ ...config, operation: e.target.value })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                <option value="READ">📥 Read File (CSV/XLS to JSON)</option>
                <option value="WRITE">📤 Write File (JSON to CSV/XLS)</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                File Format *
              </label>
              <select
                value={config.fileFormat || "CSV"}
                onChange={(e) =>
                  setConfig({ ...config, fileFormat: e.target.value })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                <option value="CSV">CSV</option>
                <option value="XLSX">Excel (.xlsx)</option>
              </select>
            </div>

            {config.operation === "READ" && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Upload File *
                </label>
                <input
                  type="file"
                  accept=".csv,.xlsx,.xls"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) {
                      const reader = new FileReader();
                      reader.onload = (event) => {
                        const result = event.target?.result as string;
                        const base64 = result.split(",")[1];
                        setConfig({
                          ...config,
                          uploadedFile: base64,
                          fileName: file.name,
                        });
                      };
                      reader.readAsDataURL(file);
                    }
                  }}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                />
                {config.fileName && (
                  <p className="text-xs text-gray-500 mt-1">
                    ✓ {config.fileName}
                  </p>
                )}
              </div>
            )}

            {config.operation === "WRITE" && (
              <>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Output File Name
                  </label>
                  <input
                    type="text"
                    value={config.fileName || "export"}
                    onChange={(e) =>
                      setConfig({ ...config, fileName: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                    placeholder="export"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Sheet Name (for Excel)
                  </label>
                  <input
                    type="text"
                    value={config.sheetName || "Data"}
                    onChange={(e) =>
                      setConfig({ ...config, sheetName: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                    placeholder="Data"
                  />
                </div>
              </>
            )}
          </>
        );

      case "APPROVAL":
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Assign Approver *
              </label>
              {loadingAdmins ? (
                <div className="flex items-center gap-2 py-2 text-sm text-gray-400">
                  <div className="w-4 h-4 border-2 border-blue-400 border-t-transparent rounded-full animate-spin" />
                  Loading admins...
                </div>
              ) : adminUsers.length > 0 ? (
                <select
                  value={config.approverEmail || ""}
                  onChange={(e) =>
                    setConfig({ ...config, approverEmail: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                >
                  <option value="">-- Select an approver --</option>
                  {adminUsers.map((user: any) => (
                    <option key={user.id} value={user.email}>
                      {user.firstName} {user.lastName} ({user.email})
                    </option>
                  ))}
                </select>
              ) : (
                <div>
                  <input
                    type="email"
                    value={config.approverEmail || ""}
                    onChange={(e) =>
                      setConfig({ ...config, approverEmail: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                    placeholder="manager@company.com"
                  />
                  <p className="text-xs text-amber-500 mt-1">
                    No admin users found. Enter an email manually.
                  </p>
                </div>
              )}
              <p className="text-xs text-gray-400 mt-1">
                This person will receive the approval request and must approve
                or reject before the workflow continues.
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Instructions
              </label>
              <textarea
                value={config.instructions || ""}
                onChange={(e) =>
                  setConfig({ ...config, instructions: e.target.value })
                }
                rows={3}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary focus:border-transparent"
                placeholder="Review the AI analysis results and approve if the candidates meet the requirements..."
              />
              <p className="text-xs text-gray-400 mt-1">
                Instructions shown to the approver
              </p>
            </div>
          </>
        );

      default:
        return null;
    }
  };

  return (
    <div className="w-96 bg-white border-l border-gray-200 h-full overflow-y-auto">
      <div className="p-6 border-b border-gray-200">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-lg font-semibold text-gray-900">
            Configure {node.data.label}
          </h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
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
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>
        <p className="text-sm text-gray-500">
          Configure the settings for this node
        </p>
      </div>

      <div className="p-6 space-y-4">
        {renderConfigFields()}

        <div className="pt-4 flex space-x-3">
          <Button onClick={onClose} variant="secondary" className="flex-1">
            Cancel
          </Button>
          <Button onClick={handleSave} variant="primary" className="flex-1">
            Save
          </Button>
        </div>
      </div>

      {/* Node Info */}
      <div className="p-6 border-t border-gray-200 bg-gray-50">
        <h4 className="text-sm font-semibold text-gray-900 mb-2">
          About this node
        </h4>
        <p className="text-xs text-gray-600">
          {node.data.label === "EMAIL" &&
            "Send email notifications to specified recipients"}
          {node.data.label === "GPT" && "Process text using OpenAI GPT models"}
          {node.data.label === "DRIVE" &&
            "Interact with Google Drive files and folders"}
          {node.data.label === "EXCEL" &&
            "Read, write, or update Excel spreadsheets"}
          {node.data.label === "APPROVAL" &&
            "Pause the workflow and wait for the assigned approver to review and approve or reject before continuing"}
        </p>
      </div>
    </div>
  );
}
