/** Helpers to read the JSON a step produced, which differs per node type. */

export interface Candidate {
  name?: string;
  email?: string;
  experience?: string | number;
  skills?: string;
  score?: string | number;
  summary?: string;
  [key: string]: unknown;
}

export function parseJson<T = unknown>(text?: string | null): T | null {
  if (!text) return null;
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

function stripFences(text: string): string {
  return text.replace(/^```(?:json)?\s*/i, "").replace(/\s*```\s*$/, "").trim();
}

/** Candidates when the AI answered with a JSON array of people, otherwise an empty list. */
export function extractCandidates(analysis?: string | null): Candidate[] {
  if (!analysis) return [];
  const parsed = parseJson<unknown>(stripFences(analysis.trim()));
  if (Array.isArray(parsed) && parsed.length > 0 && typeof parsed[0] === "object" && parsed[0] !== null) {
    const first = parsed[0] as Record<string, unknown>;
    if ("name" in first || "email" in first) return parsed as Candidate[];
  }
  return [];
}

/** Readable text for data a step received (shown to approvers). */
export function describeInput(inputData?: string | null): string {
  if (!inputData) return "";
  const parsed = parseJson<Record<string, unknown>>(inputData);
  if (parsed && typeof parsed === "object") {
    if (typeof parsed.analysis === "string") return parsed.analysis;
    return JSON.stringify(parsed, null, 2);
  }
  return inputData.slice(0, 4000);
}
