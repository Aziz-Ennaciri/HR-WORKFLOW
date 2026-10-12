"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  CheckCircle2,
  Copy,
  LayoutDashboard,
  LayoutTemplate,
  ListChecks,
  LogOut,
  Moon,
  Plus,
  Search,
  Settings,
  Sun,
  Trash2,
} from "lucide-react";
import { useTheme } from "next-themes";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useApprovalCount, useInvalidateWorkflows, useWorkflows } from "@/hooks/queries";
import { logout } from "@/lib/api";
import { getUser } from "@/lib/auth";
import { getErrorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";
import { workflowsApi } from "@/services/workflows";
import type { Workflow } from "@/types";

const NAV = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/approvals", label: "Approvals", icon: CheckCircle2 },
  { href: "/executions", label: "Executions", icon: ListChecks },
  { href: "/templates", label: "Templates", icon: LayoutTemplate },
  { href: "/settings", label: "Settings", icon: Settings },
];

interface AppSidebarProps {
  onCreateWorkflow: () => void;
  /** Called after any navigation, so the mobile drawer can close. */
  onNavigate?: () => void;
}

export function AppSidebar({ onCreateWorkflow, onNavigate }: AppSidebarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { resolvedTheme, setTheme } = useTheme();
  const { data: workflows, isLoading } = useWorkflows();
  const { data: approvalCount = 0 } = useApprovalCount();
  const invalidateWorkflows = useInvalidateWorkflows();
  const [search, setSearch] = useState("");
  const [toDelete, setToDelete] = useState<Workflow | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [duplicatingId, setDuplicatingId] = useState<number | null>(null);
  const user = getUser();

  const filtered = (workflows ?? []).filter((w) => w.name.toLowerCase().includes(search.trim().toLowerCase()));

  const duplicate = async (workflow: Workflow) => {
    setDuplicatingId(workflow.id);
    try {
      const copy = await workflowsApi.duplicate(workflow.id);
      await invalidateWorkflows();
      toast.success(`Duplicated as "${copy.name}"`);
      router.push(`/workflows/${copy.id}`);
      onNavigate?.();
    } catch (error) {
      toast.error(getErrorMessage(error, "Could not duplicate the workflow"));
    } finally {
      setDuplicatingId(null);
    }
  };

  const confirmDelete = async () => {
    if (!toDelete) return;
    setDeleting(true);
    try {
      await workflowsApi.remove(toDelete.id);
      await invalidateWorkflows();
      toast.success(`"${toDelete.name}" deleted`);
      if (pathname.startsWith(`/workflows/${toDelete.id}`)) router.push("/dashboard");
      setToDelete(null);
    } catch (error) {
      toast.error(getErrorMessage(error, "Could not delete the workflow"));
    } finally {
      setDeleting(false);
    }
  };

  const signOut = async () => {
    await logout();
    router.push("/login");
  };

  return (
    <div className="flex h-full flex-col bg-card">
      <div className="flex h-14 items-center border-b px-4">
        <Link href="/dashboard" onClick={onNavigate} className="flex items-center gap-2.5 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-xs font-bold text-primary-foreground">HR</span>
          <span className="text-[15px] font-semibold tracking-tight">HR Workflow</span>
        </Link>
      </div>

      <div className="space-y-2 px-3 pt-3">
        <Button className="w-full" onClick={onCreateWorkflow}>
          <Plus /> New workflow
        </Button>
        <div className="relative">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            type="search"
            aria-label="Search workflows"
            placeholder="Search workflows…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-8 pl-8 text-[13px]"
          />
        </div>
      </div>

      <nav aria-label="Workflows" className="mt-3 flex-1 overflow-y-auto px-3">
        <p className="mb-1.5 px-2 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Workflows</p>
        {isLoading ? (
          <div className="space-y-2 px-2">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-9 w-full" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <p className="px-2 py-3 text-xs text-muted-foreground">{search ? "No matching workflows" : "No workflows yet"}</p>
        ) : (
          <ul className="space-y-0.5">
            {filtered.map((workflow) => {
              const active = pathname.startsWith(`/workflows/${workflow.id}`);
              return (
                <li key={workflow.id} className={cn("group flex items-center rounded-lg", active ? "bg-primary/10" : "hover:bg-accent")}>
                  <Link
                    href={`/workflows/${workflow.id}`}
                    onClick={onNavigate}
                    aria-current={active ? "page" : undefined}
                    className="min-w-0 flex-1 rounded-lg px-2.5 py-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <span className={cn("block truncate text-[13px] font-medium", active && "text-primary")}>{workflow.name}</span>
                    <span className={cn("text-[11px]", workflow.status === "ACTIVE" ? "text-emerald-600 dark:text-emerald-400" : "text-muted-foreground")}>
                      {workflow.status === "ACTIVE" ? "Active" : workflow.status.charAt(0) + workflow.status.slice(1).toLowerCase()}
                    </span>
                  </Link>
                  {/* Always visible on touch screens; on desktop shown on hover or keyboard focus */}
                  <div className="flex items-center gap-0.5 pr-1 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100 md:group-focus-within:opacity-100">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7"
                      aria-label={`Duplicate ${workflow.name}`}
                      loading={duplicatingId === workflow.id}
                      onClick={() => duplicate(workflow)}
                    >
                      {duplicatingId !== workflow.id && <Copy className="!size-3.5" />}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 hover:text-destructive"
                      aria-label={`Delete ${workflow.name}`}
                      onClick={() => setToDelete(workflow)}
                    >
                      <Trash2 className="!size-3.5" />
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </nav>

      <nav aria-label="Main" className="space-y-0.5 border-t px-3 py-3">
        {NAV.map(({ href, label, icon: Icon }) => {
          const active = pathname === href || (href !== "/dashboard" && pathname.startsWith(href));
          return (
            <Link
              key={href}
              href={href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2 text-[13px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                active ? "bg-primary/10 text-primary" : "text-muted-foreground hover:bg-accent hover:text-foreground",
              )}
            >
              <Icon className="h-[18px] w-[18px]" aria-hidden />
              {label}
              {href === "/approvals" && approvalCount > 0 && (
                <span className="ml-auto min-w-[18px] rounded-full bg-orange-500 px-1.5 py-0.5 text-center text-[10px] font-bold text-white">
                  {approvalCount}
                  <span className="sr-only"> pending</span>
                </span>
              )}
            </Link>
          );
        })}
        <div className="flex items-center gap-1 pt-1">
          <Button
            variant="ghost"
            size="sm"
            className="flex-1 justify-start text-muted-foreground"
            onClick={signOut}
            title={user?.email ?? undefined}
          >
            <LogOut /> Sign out
          </Button>
          <Button
            variant="ghost"
            size="icon"
            aria-label={resolvedTheme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
            onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
          >
            {resolvedTheme === "dark" ? <Sun /> : <Moon />}
          </Button>
        </div>
      </nav>

      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => !open && setToDelete(null)}
        title="Delete workflow?"
        description={
          <>
            <strong className="text-foreground">{toDelete?.name}</strong> will be removed. Past executions stay in the history;
            running ones are cancelled.
          </>
        }
        confirmLabel="Delete"
        destructive
        loading={deleting}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
