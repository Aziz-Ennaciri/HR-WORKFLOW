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

  const activeWorkflowId = (() => {
    const match = pathname.match(/\/workflows\/(\d+)/);
    return match ? match[1] : null;
  })();

  const navItems = [
    {
      label: "Dashboard",
      path: "/dashboard",
      icon: (
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.8}
          d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zm10 0a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zm10 0a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"
        />
      ),
    },
    {
      label: "Executions",
      path: "/executions",
      icon: (
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.8}
          d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
        />
      ),
    },
    {
      label: "Settings",
      path: "/settings",
      icon: (
        <>
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.8}
            d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
          />
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.8}
            d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
          />
        </>
      ),
    },
  ];

  return (
    <div className="w-72 bg-white border-r border-gray-200/80 h-screen flex flex-col">
      {/* Header */}
      <div className="px-5 py-5 border-b border-gray-100">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-lg flex items-center justify-center shadow-sm">
            <span className="text-white font-bold text-sm tracking-tight">
              HR
            </span>
          </div>
          <div>
            <h1 className="text-[15px] font-semibold text-gray-900 tracking-tight">
              HR Workflow
            </h1>
            <p className="text-[11px] text-gray-400 font-medium">
              Automation Platform
            </p>
          </div>
        </div>
      </div>

      {/* Create Button */}
      <div className="px-4 pt-4 pb-2">
        <button
          onClick={onCreateWorkflow}
          className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors shadow-sm"
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
          New Workflow
        </button>
      </div>

      {/* Search */}
      <div className="px-4 py-2">
        <div className="relative">
          <svg
            className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
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
            placeholder="Search workflows…"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full bg-gray-50 text-gray-700 text-sm pl-9 pr-3 py-2 rounded-lg border border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400 placeholder:text-gray-400 transition-all"
          />
        </div>
      </div>

      {/* Workflows */}
      <div className="flex-1 overflow-y-auto px-3 pt-2">
        <div className="mb-2 flex items-center justify-between px-1">
          <span className="text-[11px] text-gray-400 uppercase font-semibold tracking-wider">
            Workflows
          </span>
          <span className="text-[11px] text-gray-400 tabular-nums">
            {filteredWorkflows.length}
          </span>
        </div>

        {filteredWorkflows.length === 0 ? (
          <div className="text-center py-8 text-gray-400 text-sm">
            No workflows found
          </div>
        ) : (
          <div className="space-y-0.5">
            {filteredWorkflows.map((workflow) => {
              const isActive = activeWorkflowId === String(workflow.id);
              const isRunnable = workflow.status === "ACTIVE";

              return (
                <div
                  key={workflow.id}
                  className={`rounded-lg transition-all group ${
                    isActive
                      ? "bg-blue-50 border border-blue-100"
                      : "hover:bg-gray-50 border border-transparent"
                  }`}
                >
                  <div className="flex items-center justify-between px-3 py-2.5">
                    <button
                      onClick={() =>
                        router.push(`/workflows/${workflow.id}/execute`)
                      }
                      className="flex-1 min-w-0 text-left"
                    >
                      <div className="flex items-center gap-2">
                        <div
                          className={`w-1.5 h-1.5 rounded-full shrink-0 ${
                            workflow.status === "ACTIVE"
                              ? "bg-emerald-500"
                              : workflow.status === "DRAFT"
                                ? "bg-amber-400"
                                : "bg-gray-300"
                          }`}
                        />
                        <span
                          className={`font-medium truncate text-[13px] ${
                            isActive
                              ? "text-blue-700"
                              : "text-gray-700 group-hover:text-gray-900"
                          }`}
                        >
                          {workflow.name}
                        </span>
                      </div>
                      <p className="text-[11px] text-gray-400 mt-0.5 truncate pl-3.5">
                        {workflow.description || "No description"}
                      </p>
                    </button>

                    <div className="flex items-center gap-0.5 ml-2 shrink-0">
                      {isRunnable && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            router.push(`/workflows/${workflow.id}/execute`);
                          }}
                          title="Run workflow"
                          className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-emerald-50 text-emerald-500 hover:text-emerald-600 transition-colors"
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
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          router.push(`/workflows/${workflow.id}`);
                        }}
                        title="Open designer"
                        className="w-7 h-7 flex items-center justify-center rounded-md text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors opacity-0 group-hover:opacity-100"
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

      {/* Bottom Nav */}
      <div className="px-3 py-3 border-t border-gray-100 space-y-0.5">
        {navItems.map((item) => (
          <button
            key={item.path}
            onClick={() => router.push(item.path)}
            className={`w-full text-left px-3 py-2 rounded-lg transition-all flex items-center gap-3 text-[13px] font-medium ${
              pathname === item.path
                ? "bg-blue-50 text-blue-700"
                : "text-gray-500 hover:bg-gray-50 hover:text-gray-700"
            }`}
          >
            <svg
              className="w-[18px] h-[18px]"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              {item.icon}
            </svg>
            {item.label}
          </button>
        ))}

        <button
          onClick={handleLogout}
          className="w-full text-left px-3 py-2 rounded-lg text-gray-400 hover:bg-red-50 hover:text-red-500 transition-all flex items-center gap-3 text-[13px] font-medium"
        >
          <svg
            className="w-[18px] h-[18px]"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.8}
              d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
            />
          </svg>
          Logout
        </button>
      </div>
    </div>
  );
}
