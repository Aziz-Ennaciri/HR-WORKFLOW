"use client";

import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import Button from "../ui/Button";

interface SidebarProps {
  workflows: any[];
  onCreateWorkflow: () => void;
}

export default function Sidebar({ workflows, onCreateWorkflow }: SidebarProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [searchTerm, setSearchTerm] = useState("");

  const filteredWorkflows = workflows.filter((w) =>
    w.name.toLowerCase().includes(searchTerm.toLowerCase()),
  );

  const handleLogout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    router.push("/login");
  };

  // Determine which workflow is currently active in the URL
  const activeWorkflowId = (() => {
    const match = pathname.match(/\/workflows\/(\d+)/);
    return match ? match[1] : null;
  })();

  return (
    <div className="w-80 bg-gray-900 text-white h-screen flex flex-col">
      {/* Header */}
      <div className="p-6 border-b border-gray-800">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 bg-gradient-to-br from-primary to-secondary rounded-lg flex items-center justify-center">
            <span className="text-white font-bold text-xl">HW</span>
          </div>
          <div>
            <h1 className="text-xl font-bold text-white">HR Workflow</h1>
            <p className="text-xs text-gray-400">Automation Platform</p>
          </div>
        </div>
      </div>

      {/* Create Workflow Button */}
      <div className="p-4">
        <Button
          onClick={onCreateWorkflow}
          className="w-full flex items-center justify-center space-x-2 py-3 px-4"
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
          <span>New Workflow</span>
        </Button>
      </div>

      {/* Search */}
      <div className="px-4 pb-4">
        <div className="relative">
          <svg
            className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          <input
            type="text"
            placeholder="Search workflows..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full bg-gray-800 text-white pl-10 pr-4 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
      </div>

      {/* Workflows List */}
      <div className="flex-1 overflow-y-auto px-4">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-xs text-gray-400 uppercase font-semibold">
            Workflows
          </span>
          <span className="text-xs text-gray-400">
            {filteredWorkflows.length}
          </span>
        </div>

        {filteredWorkflows.length === 0 ? (
          <div className="text-center py-8 text-gray-400 text-sm">
            No workflows found
          </div>
        ) : (
          <div className="space-y-1">
            {filteredWorkflows.map((workflow) => {
              const isActive = activeWorkflowId === String(workflow.id);
              const isRunnable = workflow.status === "ACTIVE";

              return (
                <div
                  key={workflow.id}
                  className={`rounded-lg transition group ${
                    isActive ? "bg-gray-800" : "hover:bg-gray-800"
                  }`}
                >
                  {/* Main row */}
                  <div className="flex items-center justify-between px-3 py-2.5">
                    {/* Left: name + description */}
                    <button
                      onClick={() =>
                        router.push(`/workflows/${workflow.id}/execute`)
                      }
                      className="flex-1 min-w-0 text-left"
                    >
                      <div className="flex items-center space-x-2">
                        <div
                          className={`w-2 h-2 rounded-full shrink-0 ${
                            workflow.status === "ACTIVE"
                              ? "bg-green-500"
                              : workflow.status === "DRAFT"
                                ? "bg-yellow-500"
                                : "bg-gray-500"
                          }`}
                        />
                        <span
                          className={`font-medium truncate text-sm ${isActive ? "text-white" : "text-gray-300 group-hover:text-white"}`}
                        >
                          {workflow.name}
                        </span>
                      </div>
                      <p className="text-xs text-gray-400 mt-0.5 truncate pl-4">
                        {workflow.description || "No description"}
                      </p>
                    </button>

                    {/* Right: Run button + Designer link */}
                    <div className="flex items-center gap-1 ml-2 shrink-0">
                      {/* Run button — only for active workflows */}
                      {isRunnable && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            router.push(`/workflows/${workflow.id}/execute`);
                          }}
                          title="Run workflow"
                          className="w-7 h-7 flex items-center justify-center rounded-md bg-emerald-500/20 hover:bg-emerald-500/40 text-emerald-400 hover:text-emerald-300 transition-colors"
                        >
                          <svg
                            className="w-3.5 h-3.5"
                            fill="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path d="M8 5v14l11-7z" />
                          </svg>
                        </button>
                      )}

                      {/* Designer link */}
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          router.push(`/workflows/${workflow.id}`);
                        }}
                        title="Open designer"
                        className="w-7 h-7 flex items-center justify-center rounded-md text-gray-500 hover:bg-gray-700 hover:text-gray-300 transition-colors opacity-0 group-hover:opacity-100"
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
                            d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
                          />
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                          />
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Bottom Navigation */}
      <div className="p-4 border-t border-gray-800 space-y-1">
        <button
          onClick={() => router.push("/dashboard")}
          className={`w-full text-left px-3 py-2 rounded-lg transition flex items-center space-x-3 ${
            pathname === "/dashboard"
              ? "bg-gray-800 text-white"
              : "text-gray-300 hover:bg-gray-800 hover:text-white"
          }`}
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
              d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
            />
          </svg>
          <span>Dashboard</span>
        </button>

        <button
          onClick={() => router.push("/executions")}
          className={`w-full text-left px-3 py-2 rounded-lg transition flex items-center space-x-3 ${
            pathname === "/executions"
              ? "bg-gray-800 text-white"
              : "text-gray-300 hover:bg-gray-800 hover:text-white"
          }`}
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
              d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
            />
          </svg>
          <span>Executions</span>
        </button>

        <button
          onClick={() => router.push("/settings")}
          className={`w-full text-left px-3 py-2 rounded-lg transition flex items-center space-x-3 ${
            pathname === "/settings"
              ? "bg-gray-800 text-white"
              : "text-gray-300 hover:bg-gray-800 hover:text-white"
          }`}
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
              d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
            />
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
            />
          </svg>
          <span>Settings</span>
        </button>

        <button
          onClick={handleLogout}
          className="w-full text-left px-3 py-2 rounded-lg text-red-400 hover:bg-gray-800 hover:text-red-300 transition flex items-center space-x-3"
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
              d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
            />
          </svg>
          <span>Logout</span>
        </button>
      </div>
    </div>
  );
}
