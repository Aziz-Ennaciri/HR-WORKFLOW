// Mirrors the backend DTOs. Dates are ISO-8601 strings.

export type Role = "ROLE_ADMIN" | "ROLE_RH";
export type Theme = "LIGHT" | "DARK" | "SYSTEM";

export interface User {
  id: number;
  email: string;
  firstName: string | null;
  lastName: string | null;
  roles: Role[];
  theme: Theme;
  emailNotificationsEnabled: boolean;
  lastLoginAt: string | null;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export type WorkflowStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type NodeType = "EMAIL" | "GPT" | "DRIVE" | "EXCEL" | "APPROVAL";
export const NODE_TYPES: NodeType[] = ["DRIVE", "GPT", "EXCEL", "EMAIL", "APPROVAL"];

export interface Workflow {
  id: number;
  name: string;
  description: string | null;
  version: number;
  status: WorkflowStatus;
  createdById: number;
  createdByEmail: string;
  createdAt: string;
  /** React Flow edges, serialized. */
  edgesJson: string | null;
  /** Canvas positions keyed by node id: { "<id>": { x, y } }. */
  layoutJson: string | null;
}

export interface WorkflowNode {
  id: number;
  type: NodeType;
  order: number;
  /** Secrets (e.g. GPT apiKey) come back as SECRET_MASK. */
  configJson: string;
}

/** What the API returns in place of a stored secret; sending it back keeps the stored value. */
export const SECRET_MASK = "********";

// ── Node configs, one shape per node type ─────────────────────────

export interface EmailConfig {
  to?: string;
  subject?: string;
  body?: string;
}

export type GptProvider = "ollama" | "openai" | "anthropic";

export interface GptConfig {
  provider?: GptProvider;
  model?: string;
  prompt?: string;
  temperature?: number;
  outputFormat?: "text" | "json";
  apiKey?: string;
}

export interface DriveConfig {
  action?: "read" | "write";
  folderId?: string;
  fileName?: string;
}

export interface ExcelConfig {
  operation?: "READ" | "WRITE";
  fileName?: string;
  folderId?: string;
  fileFormat?: string;
}

export interface ApprovalConfig {
  approverEmail?: string;
  instructions?: string;
}

export interface NodeConfigs {
  EMAIL: EmailConfig;
  GPT: GptConfig;
  DRIVE: DriveConfig;
  EXCEL: ExcelConfig;
  APPROVAL: ApprovalConfig;
}

/** Data carried by a node on the editor canvas. */
export type EditorNodeData = {
  [T in NodeType]: { type: T; config: NodeConfigs[T]; invalid?: boolean };
}[NodeType];

// ── Executions ────────────────────────────────────────────────────

export type ExecutionStatus = "PENDING" | "RUNNING" | "PAUSED" | "COMPLETED" | "FAILED" | "CANCELLED";
export type StepStatus =
  | "PENDING"
  | "IN_PROGRESS"
  | "WAITING_APPROVAL"
  | "COMPLETED"
  | "FAILED"
  | "REJECTED"
  | "SKIPPED"
  | "RETRYING";

export const ACTIVE_EXECUTION_STATUSES: ExecutionStatus[] = ["PENDING", "RUNNING"];

export interface Execution {
  id: number;
  workflowId: number;
  workflowName: string;
  triggeredById: number;
  triggeredByEmail: string;
  currentNodeId: number | null;
  currentNodeType: NodeType | null;
  status: ExecutionStatus;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  inputData: string | null;
  outputData: string | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface ExecutionStep {
  id: number;
  workflowInstanceId: number;
  /** Null when the workflow node was deleted after this run. */
  nodeId: number | null;
  nodeType: NodeType;
  executionOrder: number;
  status: StepStatus;
  actor: string | null;
  comment: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  inputData: string | null;
  outputData: string | null;
  errorMessage: string | null;
  retryCount: number;
  createdAt: string;
  assignedToId: number | null;
  assignedToEmail: string | null;
  assignedToName: string | null;
}

export interface ExecutionDetail {
  workflowInstance: Execution;
  nodeInstances: ExecutionStep[];
}

export interface ExecutionStats {
  total: number;
  byStatus: Record<ExecutionStatus, number>;
  byWorkflow: { workflowId: number; workflowName: string; total: number }[];
}

export interface PendingApproval {
  nodeInstanceId: number;
  status: StepStatus;
  inputData: string | null;
  createdAt: string;
  instructions: string | null;
  workflowInstanceId: number;
  workflowInstanceStatus: ExecutionStatus;
  workflowId: number;
  workflowName: string;
  triggeredById: number;
  triggeredByEmail: string;
  triggeredByName: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
