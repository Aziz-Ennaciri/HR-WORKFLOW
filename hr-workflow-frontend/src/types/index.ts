export interface User {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
}

export interface Workflow {
  id: number;
  name: string;
  description: string;
  version: number;
  status: 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
  createdAt: string;
}

export interface Node {
  id: number;
  type: 'EMAIL' | 'GPT' | 'DRIVE' | 'EXCEL';
  order: number;
  configJson: string;
}

export interface WorkflowExecution {
  id: number;
  workflowId: number;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  startedAt: string;
  finishedAt?: string;
}