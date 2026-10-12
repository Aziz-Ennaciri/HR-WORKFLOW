import api from "@/lib/api";
import type { Workflow, WorkflowNode, WorkflowStatus } from "@/types";

export interface GraphNodePayload {
  id: string;
  type: string;
  configJson: string;
}

export interface SaveGraphPayload {
  nodes: GraphNodePayload[];
  edges: unknown[];
  layout: Record<string, { x: number; y: number }>;
}

export const workflowsApi = {
  list: async () => (await api.get<Workflow[]>("/workflows")).data,

  get: async (id: number) => (await api.get<Workflow>(`/workflows/${id}`)).data,

  nodes: async (id: number) => (await api.get<WorkflowNode[]>(`/nodes/workflow/${id}`)).data,

  create: async (body: { name: string; description?: string }) =>
    (await api.post<Workflow>("/workflows", body)).data,

  /** Renames a DRAFT workflow. */
  patch: async (id: number, body: { name: string; description?: string }) =>
    (await api.patch<Workflow>(`/workflows/${id}`, body)).data,

  setStatus: async (workflow: Workflow, status: WorkflowStatus) =>
    (
      await api.put<Workflow>(`/workflows/${workflow.id}`, {
        name: workflow.name,
        description: workflow.description,
        status,
      })
    ).data,

  /** Saves the whole canvas in one transaction; the server derives the run order from the edges. */
  saveGraph: async (id: number, payload: SaveGraphPayload) =>
    (await api.put<Workflow>(`/workflows/${id}/graph`, payload)).data,

  duplicate: async (id: number) => (await api.post<Workflow>(`/workflows/${id}/duplicate`)).data,

  remove: async (id: number) => {
    await api.delete(`/workflows/${id}`);
  },

  createNodes: async (id: number, nodes: { type: string; order: number; configJson: string }[]) =>
    (await api.post<WorkflowNode[]>(`/nodes/workflow/${id}/batch`, nodes)).data,
};
