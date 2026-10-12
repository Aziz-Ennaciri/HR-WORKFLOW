"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { toast } from "sonner";
import { FormError, SettingsSection } from "@/components/settings/settings-section";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { queryKeys } from "@/hooks/queries";
import { getUser, setUser as storeUser } from "@/lib/auth";
import { getErrorMessage } from "@/lib/errors";
import { emailSchema, passwordSchema } from "@/lib/validation";
import { usersApi } from "@/services/users";
import type { User } from "@/types";

function useUserUpdated() {
  const queryClient = useQueryClient();
  return (user: User) => {
    queryClient.setQueryData(queryKeys.me, user);
    const stored = getUser();
    if (stored) storeUser({ ...stored, ...user });
  };
}

const nameSchema = z.object({
  firstName: z.string().trim().min(1, "First name is required").max(100),
  lastName: z.string().trim().min(1, "Last name is required").max(100),
});

export function NameForm({ user }: { user: User }) {
  const onUpdated = useUserUpdated();
  const form = useForm<z.infer<typeof nameSchema>>({
    resolver: zodResolver(nameSchema),
    values: { firstName: user.firstName ?? "", lastName: user.lastName ?? "" },
  });
  const { errors, isSubmitting, isDirty } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      onUpdated(await usersApi.updateProfile(values));
      toast.success("Name updated");
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) });
    }
  });

  return (
    <SettingsSection title="Name" description="How you appear to approvers and in execution history.">
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <FormError message={errors.root?.message} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="first-name" label="First name" error={errors.firstName?.message}>
            <Input autoComplete="given-name" {...form.register("firstName")} />
          </Field>
          <Field id="last-name" label="Last name" error={errors.lastName?.message}>
            <Input autoComplete="family-name" {...form.register("lastName")} />
          </Field>
        </div>
        <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
          Save name
        </Button>
      </form>
    </SettingsSection>
  );
}

const emailFormSchema = z.object({
  newEmail: emailSchema,
  currentPassword: z.string().min(1, "Enter your current password"),
});

export function EmailForm({ user }: { user: User }) {
  const onUpdated = useUserUpdated();
  const form = useForm<z.infer<typeof emailFormSchema>>({
    resolver: zodResolver(emailFormSchema),
    defaultValues: { newEmail: "", currentPassword: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      onUpdated(await usersApi.changeEmail(values));
      form.reset();
      toast.success("Email updated");
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) });
    }
  });

  return (
    <SettingsSection title="Email address" description={`You sign in with ${user.email}.`}>
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <FormError message={errors.root?.message} />
        <Field id="new-email" label="New email" error={errors.newEmail?.message}>
          <Input type="email" autoComplete="email" placeholder="new@company.com" {...form.register("newEmail")} />
        </Field>
        <Field id="email-password" label="Current password" error={errors.currentPassword?.message}>
          <Input type="password" autoComplete="current-password" {...form.register("currentPassword")} />
        </Field>
        <Button type="submit" loading={isSubmitting}>
          Change email
        </Button>
      </form>
    </SettingsSection>
  );
}

const passwordFormSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password"),
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords don't match" });

export function PasswordForm() {
  const form = useForm<z.infer<typeof passwordFormSchema>>({
    resolver: zodResolver(passwordFormSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      await usersApi.changePassword({ currentPassword, newPassword });
      form.reset();
      toast.success("Password changed. Other devices will need to sign in again.");
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) });
    }
  });

  return (
    <SettingsSection title="Password" description="At least 8 characters. Changing it signs out your other sessions.">
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <FormError message={errors.root?.message} />
        <Field id="current-password" label="Current password" error={errors.currentPassword?.message}>
          <Input type="password" autoComplete="current-password" {...form.register("currentPassword")} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="new-password" label="New password" error={errors.newPassword?.message}>
            <Input type="password" autoComplete="new-password" {...form.register("newPassword")} />
          </Field>
          <Field id="confirm-password" label="Confirm new password" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...form.register("confirmPassword")} />
          </Field>
        </div>
        <Button type="submit" loading={isSubmitting}>
          Change password
        </Button>
      </form>
    </SettingsSection>
  );
}
