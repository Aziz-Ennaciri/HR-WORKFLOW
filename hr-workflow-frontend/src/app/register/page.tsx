"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { AuthLayout } from "@/components/layout/auth-layout";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { isAuthenticated } from "@/lib/auth";
import { getErrorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";
import { emailSchema, passwordSchema } from "@/lib/validation";
import { usersApi } from "@/services/users";

const schema = z
  .object({
    firstName: z.string().trim().min(1, "First name is required").max(100),
    lastName: z.string().trim().min(1, "Last name is required").max(100),
    email: emailSchema,
    password: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords don't match" });

function strength(password: string): { score: number; label: string } {
  let score = 0;
  if (password.length >= 8) score++;
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score++;
  if (/\d/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password) || password.length >= 14) score++;
  return { score, label: ["Too short", "Weak", "Fair", "Good", "Strong"][score] };
}

export default function RegisterPage() {
  const router = useRouter();
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: "", lastName: "", email: "", password: "", confirmPassword: "" },
  });
  const { errors, isSubmitting } = form.formState;
  const password = form.watch("password");
  const meter = strength(password);

  useEffect(() => {
    if (isAuthenticated()) router.replace("/dashboard");
  }, [router]);

  const onSubmit = form.handleSubmit(async ({ confirmPassword: _confirm, ...values }) => {
    try {
      await usersApi.register(values);
      toast.success("Account created. You can sign in now.");
      router.push("/login");
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error, "Registration failed") });
    }
  });

  return (
    <AuthLayout tagline="Start automating your HR processes.">
      <h1 className="text-2xl font-bold tracking-tight">Create your account</h1>
      <p className="mb-6 mt-1 text-sm text-muted-foreground">It only takes a minute.</p>
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        {errors.root && (
          <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {errors.root.message}
          </p>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="firstName" label="First name" error={errors.firstName?.message}>
            <Input autoComplete="given-name" autoFocus {...form.register("firstName")} />
          </Field>
          <Field id="lastName" label="Last name" error={errors.lastName?.message}>
            <Input autoComplete="family-name" {...form.register("lastName")} />
          </Field>
        </div>
        <Field id="email" label="Work email" error={errors.email?.message}>
          <Input type="email" autoComplete="email" placeholder="you@company.com" {...form.register("email")} />
        </Field>
        <Field id="password" label="Password" error={errors.password?.message} hint="At least 8 characters.">
          <PasswordInput autoComplete="new-password" {...form.register("password")} />
        </Field>
        {password && (
          <div aria-live="polite">
            <div className="flex gap-1" aria-hidden>
              {[1, 2, 3, 4].map((level) => (
                <span
                  key={level}
                  className={cn(
                    "h-1 flex-1 rounded-full bg-muted",
                    meter.score >= level && ["bg-red-500", "bg-amber-500", "bg-blue-500", "bg-emerald-500"][meter.score - 1],
                  )}
                />
              ))}
            </div>
            <p className="mt-1 text-xs text-muted-foreground">Strength: {meter.label}</p>
          </div>
        )}
        <Field id="confirmPassword" label="Confirm password" error={errors.confirmPassword?.message}>
          <PasswordInput autoComplete="new-password" {...form.register("confirmPassword")} />
        </Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link href="/login" className="font-medium text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  );
}
