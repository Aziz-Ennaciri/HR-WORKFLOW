import React from "react";

function stripMarkdownFences(text: string): string {
  return text
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```\s*$/, "")
    .trim();
}

function tryParseJson(text: string): any {
  try {
    return JSON.parse(stripMarkdownFences(text.trim()));
  } catch {
    return null;
  }
}

function isCandidateArray(parsed: any): boolean {
  return (
    Array.isArray(parsed) &&
    parsed.length > 0 &&
    typeof parsed[0] === "object" &&
    ("name" in parsed[0] || "email" in parsed[0])
  );
}

export function extractCandidates(analysisText: string): any[] {
  if (!analysisText) return [];
  const parsed = tryParseJson(analysisText);
  return isCandidateArray(parsed) ? parsed : [];
}

function CandidateList({ candidates }: { candidates: any[] }) {
  return (
    <div className="space-y-2">
      {candidates.map((c: any, i: number) => (
        <div
          key={i}
          className="flex items-start justify-between bg-gray-50 border border-gray-100 rounded-lg px-4 py-2.5 gap-4"
        >
          <div className="min-w-0">
            <span className="text-gray-900 text-sm font-medium">{c.name}</span>
            {c.email && (
              <span className="text-gray-400 text-xs ml-2">{c.email}</span>
            )}
            {c.experience !== undefined && (
              <span className="text-gray-400 text-xs ml-2">
                · {c.experience} yrs exp
              </span>
            )}
            {c.skills && (
              <p className="text-gray-400 text-xs mt-0.5">
                Skills: {c.skills}
              </p>
            )}
          </div>
          {c.score !== undefined && (
            <span className="text-emerald-600 text-sm font-bold flex-shrink-0">
              {c.score}/10
            </span>
          )}
        </div>
      ))}
    </div>
  );
}

export function FormatGptOutput({ text }: { text: string }) {
  if (!text) return null;

  const parsed = tryParseJson(text);

  if (isCandidateArray(parsed)) {
    return <CandidateList candidates={parsed} />;
  }

  return (
    <p className="text-gray-600 text-xs whitespace-pre-wrap leading-relaxed break-words">
      {typeof parsed === "string" ? parsed : text}
    </p>
  );
}
