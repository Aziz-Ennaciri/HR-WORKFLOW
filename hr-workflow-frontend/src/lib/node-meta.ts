import { Bot, FileSpreadsheet, FolderOpen, Mail, ShieldCheck, type LucideIcon } from "lucide-react";
import type { NodeType } from "@/types";

export interface NodeMeta {
  label: string;
  description: string;
  icon: LucideIcon;
  /** Tailwind classes for the icon tile, readable in light and dark mode. */
  tile: string;
  /** Hex accent used by the canvas (React Flow styles need raw colors). */
  accent: string;
}

/** One source of truth for how each node type looks and is described across the app. */
export const NODE_META: Record<NodeType, NodeMeta> = {
  DRIVE: {
    label: "Drive",
    description: "Read files from a storage folder, or save the previous step's output as a file.",
    icon: FolderOpen,
    tile: "bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300",
    accent: "#2563eb",
  },
  GPT: {
    label: "AI Agent",
    description: "Ask an AI model to analyse the previous step's data with your instructions.",
    icon: Bot,
    tile: "bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300",
    accent: "#7c3aed",
  },
  EXCEL: {
    label: "Excel",
    description: "Generate an Excel report from the previous step, or read rows from a spreadsheet.",
    icon: FileSpreadsheet,
    tile: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300",
    accent: "#059669",
  },
  EMAIL: {
    label: "Email",
    description: "Send the results to one or more recipients.",
    icon: Mail,
    tile: "bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300",
    accent: "#d97706",
  },
  APPROVAL: {
    label: "Approval",
    description: "Pause the workflow until the assigned approver approves or rejects.",
    icon: ShieldCheck,
    tile: "bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300",
    accent: "#ea580c",
  },
};
