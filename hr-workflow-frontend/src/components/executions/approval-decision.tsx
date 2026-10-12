"use client";

import { useState } from "react";
import { Check, X } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDecideApproval } from "@/hooks/queries";
import { getErrorMessage } from "@/lib/errors";

/** Comment box with Approve / Reject; used on the approvals page and on an execution. */
export function ApprovalDecision({ stepId, onDecided }: { stepId: number; onDecided?: () => void }) {
  const [comment, setComment] = useState("");
  const decide = useDecideApproval();
  const [pending, setPending] = useState<"approve" | "reject" | null>(null);

  const submit = async (approve: boolean) => {
    setPending(approve ? "approve" : "reject");
    try {
      await decide.mutateAsync({ stepId, approve, comment: comment.trim() || undefined });
      toast.success(approve ? "Approved — the workflow continues" : "Rejected — the workflow has stopped");
      setComment("");
      onDecided?.();
    } catch (error) {
      toast.error(getErrorMessage(error, "Could not record your decision"));
    } finally {
      setPending(null);
    }
  };

  const fieldId = `approval-comment-${stepId}`;
  return (
    <div className="space-y-3">
      <div className="space-y-1.5">
        <Label htmlFor={fieldId}>Comment (optional)</Label>
        <Textarea id={fieldId} rows={2} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Explain your decision…" />
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button className="flex-1 bg-emerald-600 text-white hover:bg-emerald-700" loading={pending === "approve"} disabled={pending !== null} onClick={() => submit(true)}>
          {pending !== "approve" && <Check />} Approve &amp; continue
        </Button>
        <Button variant="outline" className="flex-1 text-destructive hover:text-destructive" loading={pending === "reject"} disabled={pending !== null} onClick={() => submit(false)}>
          {pending !== "reject" && <X />} Reject &amp; stop
        </Button>
      </div>
    </div>
  );
}
