import { extractCandidates, type Candidate } from "@/lib/step-output";

export function CandidateList({ candidates }: { candidates: Candidate[] }) {
  return (
    <ul className="space-y-2">
      {candidates.map((candidate, index) => (
        <li key={index} className="flex items-start justify-between gap-4 rounded-lg border bg-muted/40 px-4 py-2.5">
          <div className="min-w-0">
            <p className="text-sm font-medium">
              {candidate.name ?? "Unnamed"}
              {candidate.email && <span className="ml-2 text-xs font-normal text-muted-foreground">{candidate.email}</span>}
            </p>
            {(candidate.experience !== undefined || candidate.skills) && (
              <p className="mt-0.5 text-xs text-muted-foreground">
                {candidate.experience !== undefined && <>{String(candidate.experience)} experience</>}
                {candidate.experience !== undefined && candidate.skills && " · "}
                {candidate.skills}
              </p>
            )}
            {candidate.summary && <p className="mt-1 text-xs text-muted-foreground">{candidate.summary}</p>}
          </div>
          {candidate.score !== undefined && (
            <span className="shrink-0 text-sm font-bold text-emerald-600 dark:text-emerald-400">{String(candidate.score)}</span>
          )}
        </li>
      ))}
    </ul>
  );
}

/** Renders an AI answer: a candidate list when it is structured, plain text otherwise. */
export function GptOutput({ text }: { text?: string | null }) {
  if (!text) return null;
  const candidates = extractCandidates(text);
  if (candidates.length > 0) return <CandidateList candidates={candidates} />;
  return <p className="whitespace-pre-wrap break-words text-xs leading-relaxed text-muted-foreground">{text}</p>;
}
