"use client";

import { Suspense, useEffect } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { AuthLayout } from "@/components/layout/auth-layout";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { isAuthenticated, setAuth } from "@/lib/auth";
import { getErrorMessage } from "@/lib/errors";
import { emailSchema } from "@/lib/validation";
import { usersApi } from "@/services/users";

const schema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Enter your password"),
});

/** Only same-site paths, so ?next= cannot redirect to another website. */
function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/dashboard";
}

function LoginForm() {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "" } });
  const { errors, isSubmitting } = form.formState;

  useEffect(() => {
    if (isAuthenticated()) router.replace(next);
  }, [router, next]);

  const onSubmit = form.handleSubmit(async ({ email, password }) => {
    try {
      const response = await usersApi.login(email, password);
      setAuth(response.token, response.user, response.refreshToken);
      router.replace(next);
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error, "Invalid email or password") });
    }
  });

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {errors.root && (
        <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {errors.root.message}
        </p>
      )}
      <Field id="email" label="Email" error={errors.email?.message}>
        <Input type="email" autoComplete="email" placeholder="you@company.com" autoFocus {...form.register("email")} />
      </Field>
      <Field id="password" label="Password" error={errors.password?.message}>
        <PasswordInput autoComplete="current-password" {...form.register("password")} />
      </Field>
      <Button type="submit" className="w-full" loading={isSubmitting}>
        Sign in
      </Button>
    </form>
  );
}

export default function LoginPage() {
  return (
    <AuthLayout tagline="Automate your HR workflows.">
      <h1 className="text-2xl font-bold tracking-tight">Welcome back</h1>
      <p className="mb-6 mt-1 text-sm text-muted-foreground">Sign in to your account</p>
      {/* useSearchParams needs a Suspense boundary for static rendering */}
      <Suspense>
        <LoginForm />
      </Suspense>
      <p className="mt-6 text-center text-sm text-muted-foreground">
        No account yet?{" "}
        <Link href="/register" className="font-medium text-primary hover:underline">
          Create one
        </Link>
      </p>
    </AuthLayout>
  );
}
