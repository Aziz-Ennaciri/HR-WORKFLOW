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
  createdById?: number;
  createdByEmail?: string;
  /** React Flow edges, serialized. */
  edgesJson?: string | null;
  /** Canvas positions keyed by node id: { "<id>": { x, y } }. */
  layoutJson?: string | null;
}

export interface Node {
  id: number;
  type: 'EMAIL' | 'GPT' | 'DRIVE' | 'EXCEL' | 'APPROVAL';
  order: number;
  configJson: string;
}

