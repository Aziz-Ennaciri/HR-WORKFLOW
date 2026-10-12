"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Input, Textarea } from "@/components/ui/input";
import { queryKeys } from "@/hooks/queries";
import { getErrorMessage } from "@/lib/errors";
import { workflowsApi } from "@/services/workflows";
import type { Workflow } from "@/types";

const schema = z.object({
  name: z.string().trim().min(1, "Name is required").max(255),
  description: z.string().max(255).optional(),
});

export function EditWorkflowDialog({ workflow, open, onOpenChange }: { workflow: Workflow; open: boolean; onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient();
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    values: { name: workflow.name, description: workflow.description ?? "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const updated = await workflowsApi.patch(workflow.id, values);
      queryClient.setQueryData(queryKeys.workflow(workflow.id), updated);
      await queryClient.invalidateQueries({ queryKey: queryKeys.workflows, exact: true });
      toast.success("Workflow renamed");
      onOpenChange(false);
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) });
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Rename workflow</DialogTitle>
          <DialogDescription>Only draft workflows can be renamed.</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {errors.root && <p role="alert" className="text-sm text-destructive">{errors.root.message}</p>}
          <Field id="edit-name" label="Name" required error={errors.name?.message}>
            <Input {...form.register("name")} />
          </Field>
          <Field id="edit-description" label="Description" error={errors.description?.message}>
            <Textarea rows={3} {...form.register("description")} />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={isSubmitting}>
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
