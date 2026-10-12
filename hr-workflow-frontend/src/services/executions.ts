import api from "@/lib/api";
import type {
  Execution,
  ExecutionDetail,
  ExecutionStats,
  ExecutionStatus,
  ExecutionStep,
  Page,
  PendingApproval,
} from "@/types";

export interface ExecutionFilters {
  page?: number;
  size?: number;
  status?: ExecutionStatus;
  workflowId?: number;
}

export const executionsApi = {
  list: async (filters: ExecutionFilters = {}) =>
    (await api.get<Page<Execution>>("/executions", { params: filters })).data,

  stats: async () => (await api.get<ExecutionStats>("/executions/stats")).data,

  detail: async (id: number) => (await api.get<ExecutionDetail>(`/executions/${id}/detail`)).data,

  trigger: async (workflowId: number, inputData = "{}") =>
    (await api.post<Execution>("/executions/trigger", { workflowId, inputData })).data,

  remove: async (id: number) => {
    await api.delete(`/executions/${id}`);
  },

  retryStep: async (stepId: number) =>
    (await api.post<ExecutionStep>(`/executions/nodes/${stepId}/retry`)).data,

  /** Downloads a generated report through the API (the output only carries its link). */
  downloadReport: async (webFileUrl: string) =>
    (await api.get<Blob>(webFileUrl.replace(/^\/api\/v1/, ""), { responseType: "blob" })).data,
};

export const approvalsApi = {
  mine: async () => (await api.get<PendingApproval[]>("/executions/nodes/my-approvals")).data,

  count: async () => (await api.get<number>("/executions/nodes/my-approvals/count")).data,

  approve: async (stepId: number, comment?: string) =>
    (await api.post<ExecutionStep>(`/executions/nodes/${stepId}/approve`, { comment: comment || "Approved" })).data,

  reject: async (stepId: number, comment?: string) =>
    (await api.post<ExecutionStep>(`/executions/nodes/${stepId}/reject`, { comment: comment || "Rejected" })).data,
};
