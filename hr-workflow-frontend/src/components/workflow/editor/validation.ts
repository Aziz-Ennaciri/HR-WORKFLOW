import type { Edge, Node } from "reactflow";
import type { EditorNodeData, NodeConfigs } from "@/types";

const text = (value: unknown) => (typeof value === "string" ? value.trim() : "");

/**
 * Settings each step needs before the workflow can be activated. Mirrors the backend's
 * NodeConfigValidator (minus the "approver is an active user" check, which only it can do).
 */
export function validateNodeConfig(data: EditorNodeData): string[] {
  const errors: string[] = [];
  switch (data.type) {
    case "EMAIL": {
      const c = data.config as NodeConfigs["EMAIL"];
      if (!text(c.to)) errors.push("Add at least one recipient");
      if (!text(c.subject)) errors.push("Add a subject");
      break;
    }
    case "GPT": {
      const c = data.config as NodeConfigs["GPT"];
      if (!text(c.prompt)) errors.push("Write the instructions for the AI");
      if ((c.provider === "openai" || c.provider === "anthropic") && !text(c.apiKey)) errors.push("Add an API key for this provider");
      break;
    }
    case "DRIVE": {
      const c = data.config as NodeConfigs["DRIVE"];
      if (!text(c.folderId)) errors.push("Choose a folder");
      break;
    }
    case "EXCEL": {
      const c = data.config as NodeConfigs["EXCEL"];
      if (!text(c.fileName)) errors.push(c.operation === "READ" ? "Choose the file to read" : "Name the report");
      break;
    }
    case "APPROVAL": {
      const c = data.config as NodeConfigs["APPROVAL"];
      if (!text(c.approverEmail)) errors.push("Choose an approver");
      break;
    }
  }
  return errors;
}

/**
 * Connection problems that would block activation: several steps must form one chain
 * (one start, each step feeding exactly one next step, no loops). Mirrors WorkflowGraph.
 */
export function validateGraph(nodes: Node[], edges: Edge[]): string[] {
  if (nodes.length <= 1) return [];
  const incoming = new Map<string, number>();
  const outgoing = new Map<string, number>();
  for (const edge of edges) {
    incoming.set(edge.target, (incoming.get(edge.target) ?? 0) + 1);
    outgoing.set(edge.source, (outgoing.get(edge.source) ?? 0) + 1);
  }
  const problems: string[] = [];
  if ([...outgoing.values()].some((n) => n > 1)) problems.push("A step can only lead to one next step (branching isn't supported)");
  if ([...incoming.values()].some((n) => n > 1)) problems.push("A step can only have one previous step");

  const starts = nodes.filter((n) => !incoming.has(n.id));
  if (starts.length === 0) problems.push("The steps form a loop; the workflow needs a first step");
  else if (starts.length > 1) problems.push(`Connect all steps into one chain (${starts.length} steps have nothing before them)`);
  else if (problems.length === 0) {
    const next = new Map(edges.map((e) => [e.source, e.target]));
    const seen = new Set<string>();
    for (let id: string | undefined = starts[0].id; id && !seen.has(id); id = next.get(id)) seen.add(id);
    if (seen.size !== nodes.length) problems.push("Some steps are not connected to the chain");
  }
  return problems;
}

