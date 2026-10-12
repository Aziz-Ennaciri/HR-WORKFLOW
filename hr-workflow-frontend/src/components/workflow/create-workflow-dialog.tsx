"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Input, Textarea } from "@/components/ui/input";
import { useInvalidateWorkflows } from "@/hooks/queries";
import { getErrorMessage } from "@/lib/errors";
import { workflowsApi } from "@/services/workflows";

const schema = z.object({
  name: z.string().trim().min(1, "Name is required").max(255),
  description: z.string().max(255).optional(),
});
type FormValues = z.infer<typeof schema>;

export function CreateWorkflowDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const router = useRouter();
  const invalidateWorkflows = useInvalidateWorkflows();
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { name: "", description: "" } });

  const onSubmit = async (values: FormValues) => {
    try {
      const workflow = await workflowsApi.create(values);
      await invalidateWorkflows();
      toast.success(`"${workflow.name}" created`);
      onOpenChange(false);
      form.reset();
      router.push(`/workflows/${workflow.id}`);
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error, "Could not create the workflow") });
    }
  };

  const { errors, isSubmitting } = form.formState;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New workflow</DialogTitle>
          <DialogDescription>Give it a name; you will add steps in the editor next.</DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {errors.root && (
            <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {errors.root.message}
            </p>
          )}
          <Field id="workflow-name" label="Name" required error={errors.name?.message}>
            <Input placeholder="e.g. Candidate screening" autoFocus {...form.register("name")} />
          </Field>
          <Field id="workflow-description" label="Description" error={errors.description?.message}>
            <Textarea rows={3} placeholder="What does this workflow do?" {...form.register("description")} />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={isSubmitting}>
              Create workflow
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
