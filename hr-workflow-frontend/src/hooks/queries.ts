"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { approvalsApi, executionsApi, type ExecutionFilters } from "@/services/executions";
import { workflowsApi } from "@/services/workflows";
import { usersApi } from "@/services/users";
import { ACTIVE_EXECUTION_STATUSES, type ExecutionStatus, type Role } from "@/types";

/** Query keys in one place, so invalidation always matches what was cached. */
export const queryKeys = {
  workflows: ["workflows"] as const,
  workflow: (id: number) => ["workflows", id] as const,
  workflowNodes: (id: number) => ["workflows", id, "nodes"] as const,
  executions: (filters: ExecutionFilters) => ["executions", "list", filters] as const,
  executionStats: ["executions", "stats"] as const,
  execution: (id: number) => ["executions", "detail", id] as const,
  approvals: ["approvals"] as const,
  approvalCount: ["approvals", "count"] as const,
  me: ["me"] as const,
  usersByRole: (role: Role) => ["users", "role", role] as const,
};

/** Poll every 3s while something is still running, then stop. */
const POLL_MS = 3000;
const isActive = (status?: ExecutionStatus) => !!status && ACTIVE_EXECUTION_STATUSES.includes(status);

export function useWorkflows() {
  return useQuery({ queryKey: queryKeys.workflows, queryFn: workflowsApi.list });
}

export function useWorkflow(id: number) {
  return useQuery({ queryKey: queryKeys.workflow(id), queryFn: () => workflowsApi.get(id) });
}

export function useWorkflowNodes(id: number) {
  return useQuery({ queryKey: queryKeys.workflowNodes(id), queryFn: () => workflowsApi.nodes(id) });
}

export function useExecutions(filters: ExecutionFilters) {
  return useQuery({
    queryKey: queryKeys.executions(filters),
    queryFn: () => executionsApi.list(filters),
    placeholderData: (previous) => previous,
    refetchInterval: (query) => (query.state.data?.content.some((e) => isActive(e.status)) ? POLL_MS : false),
  });
}

export function useExecutionStats() {
  return useQuery({ queryKey: queryKeys.executionStats, queryFn: executionsApi.stats });
}

export function useExecutionDetail(id: number) {
  return useQuery({
    queryKey: queryKeys.execution(id),
    queryFn: () => executionsApi.detail(id),
    refetchInterval: (query) => (isActive(query.state.data?.workflowInstance.status) ? POLL_MS : false),
    retry: (count, error) => (error as { response?: { status?: number } })?.response?.status !== 404 && count < 2,
  });
}

export function useMyApprovals() {
  return useQuery({ queryKey: queryKeys.approvals, queryFn: approvalsApi.mine, refetchInterval: 15_000 });
}

export function useApprovalCount() {
  return useQuery({ queryKey: queryKeys.approvalCount, queryFn: approvalsApi.count, refetchInterval: 30_000 });
}

export function useMe() {
  return useQuery({ queryKey: queryKeys.me, queryFn: usersApi.me });
}

export function useUsersByRole(role: Role, enabled = true) {
  return useQuery({ queryKey: queryKeys.usersByRole(role), queryFn: () => usersApi.byRole(role), enabled });
}

/** Everything an approval decision can change: the approval list, its count, and executions. */
export function useDecideApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ stepId, approve, comment }: { stepId: number; approve: boolean; comment?: string }) =>
      approve ? approvalsApi.approve(stepId, comment) : approvalsApi.reject(stepId, comment),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.approvals });
      queryClient.invalidateQueries({ queryKey: ["executions"] });
    },
  });
}

export function useInvalidateWorkflows() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.workflows });
}
