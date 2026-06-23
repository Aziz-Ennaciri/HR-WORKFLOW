"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import api from "@/lib/api";
import { isAuthenticated } from "@/lib/auth";
import AuthLayout from "@/components/layout/AuthLayout";

export default function RegisterPage() {
  const [formData, setFormData] = useState({
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    role: "RH",
  });
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  useEffect(() => {
    if (isAuthenticated()) router.replace("/dashboard");
  }, [router]);

  const getPasswordStrength = (pw: string) => {
    if (!pw) return { level: 0, label: "", color: "" };
    let score = 0;
    if (pw.length >= 8) score++;
    if (/[A-Z]/.test(pw)) score++;
    if (/[0-9]/.test(pw)) score++;
    if (/[^A-Za-z0-9]/.test(pw)) score++;
    if (score <= 1) return { level: 1, label: "Weak", color: "bg-red-400" };
    if (score === 2) return { level: 2, label: "Fair", color: "bg-amber-400" };
    if (score === 3) return { level: 3, label: "Good", color: "bg-blue-400" };
    return { level: 4, label: "Strong", color: "bg-emerald-400" };
  };

  const strength = getPasswordStrength(formData.password);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (formData.password.length < 6) {
      setError("Password must be at least 6 characters");
      return;
    }
    if (formData.password !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }
    setLoading(true);
    try {
      await api.post("/users/register", formData);
      router.push("/login");
    } catch (err: any) {
      setError(err.response?.data?.message || "Registration failed");
    } finally {
      setLoading(false);
    }
  };

  /* shared input class */
  const inputBase =
    "w-full text-sm border border-gray-200 rounded-[6px] bg-gray-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#1a365d]/20 focus:border-[#1a365d] placeholder:text-gray-400 transition-all";

  return (
    <AuthLayout tagline="Create workflows in minutes">
      {/* Heading */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-[#1a202c] tracking-tight">
          Create your account
        </h1>
        <p className="text-[#4a5568] text-sm mt-1">
          Get started with HR Workflow
        </p>
      </div>

      {/* Error */}
      {error && (
        <div className="mb-5 flex items-start gap-2.5 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-3">
          <svg
            className="w-4 h-4 shrink-0 mt-0.5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* First + Last Name */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-medium text-[#2d3748] mb-1.5">
              First name
            </label>
            <input
              type="text"
              value={formData.firstName}
              onChange={(e) =>
                setFormData({ ...formData, firstName: e.target.value })
              }
              className={`${inputBase} px-3.5 py-2.5`}
              placeholder="First"
              required
              autoFocus
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-[#2d3748] mb-1.5">
              Last name
            </label>
            <input
              type="text"
              value={formData.lastName}
              onChange={(e) =>
                setFormData({ ...formData, lastName: e.target.value })
              }
              className={`${inputBase} px-3.5 py-2.5`}
              placeholder="Last"
              required
            />
          </div>
        </div>

        {/* Work email */}
        <div>
          <label className="block text-sm font-medium text-[#2d3748] mb-1.5">
            Work email
          </label>
          <div className="relative">
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#a0aec0]">
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <rect
                  x="2"
                  y="4"
                  width="20"
                  height="16"
                  rx="3"
                  strokeWidth="1.8"
                />
                <path
                  d="M2 7l8.9 5.17a2 2 0 002.2 0L22 7"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                />
              </svg>
            </div>
            <input
              type="email"
              value={formData.email}
              onChange={(e) =>
                setFormData({ ...formData, email: e.target.value })
              }
              className={`${inputBase} pl-9 pr-4 py-2.5`}
              placeholder="you@company.com"
              required
            />
          </div>
        </div>

        {/* Role */}
        <div>
          <label className="block text-sm font-medium text-[#2d3748] mb-1.5">
            Role
          </label>
          <div className="relative">
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#a0aec0]">
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.8}
                  d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                />
              </svg>
            </div>
            <select
              value={formData.role}
              onChange={(e) =>
                setFormData({ ...formData, role: e.target.value })
              }
              className={`${inputBase} pl-9 pr-8 py-2.5 cursor-pointer appearance-none text-[#2d3748]`}
            >
              <option value="RH">HR User — Create and run workflows</option>
              <option value="ADMIN">Admin — Approve workflow steps</option>
            </select>
            <div className="absolute right-3 top-1/2 -translate-y-1/2 text-[#a0aec0] pointer-events-none">
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 9l-7 7-7-7"
                />
              </svg>
            </div>
          </div>
        </div>

        {/* Password */}
        <div>
          <label className="block text-sm font-medium text-[#2d3748] mb-1.5">
            Password
          </label>
          <div className="relative">
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#a0aec0]">
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <rect
                  x="3"
                  y="11"
                  width="18"
                  height="11"
                  rx="2"
                  strokeWidth="1.8"
                />
                <path
                  d="M7 11V7a5 5 0 0110 0v4"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                />
              </svg>
            </div>
            <input
              type={showPassword ? "text" : "password"}
              value={formData.password}
              onChange={(e) =>
                setFormData({ ...formData, password: e.target.value })
              }
              className={`${inputBase} pl-9 pr-10 py-2.5`}
              placeholder="Min. 6 characters"
              required
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-[#a0aec0] hover:text-[#718096] transition-colors"
              aria-label={showPassword ? "Hide password" : "Show password"}
            >
              {showPassword ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          </div>

          {/* Password strength bar */}
          {formData.password && (
            <div className="mt-2">
              <div className="flex gap-1">
                {[1, 2, 3, 4].map((i) => (
                  <div
                    key={i}
                    className={`h-1.5 flex-1 rounded-full transition-colors ${
                      i <= strength.level ? strength.color : "bg-gray-100"
                    }`}
                  />
                ))}
              </div>
              <p
                className={`text-[11px] mt-1 font-medium ${
                  strength.level <= 1
                    ? "text-red-500"
                    : strength.level === 2
                      ? "text-amber-500"
                      : strength.level === 3
                        ? "text-blue-500"
                        : "text-emerald-500"
                }`}
              >
                {strength.label} password
              </p>
            </div>
          )}
        </div>

        {/* Confirm password */}
        <div>
          <label className="block text-sm font-medium text-[#2d3748] mb-1.5">
            Confirm password
          </label>
          <div className="relative">
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#a0aec0]">
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <rect
                  x="3"
                  y="11"
                  width="18"
                  height="11"
                  rx="2"
                  strokeWidth="1.8"
                />
                <path
                  d="M7 11V7a5 5 0 0110 0v4"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                />
              </svg>
            </div>
            <input
              type={showPassword ? "text" : "password"}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={`${inputBase} pl-9 pr-10 py-2.5 ${
                confirmPassword && confirmPassword !== formData.password
                  ? "border-red-300 focus:border-red-400"
                  : confirmPassword && confirmPassword === formData.password
                    ? "border-emerald-300 focus:border-emerald-400"
                    : ""
              }`}
              placeholder="Re-enter your password"
              required
            />
            {/* Match indicator */}
            {confirmPassword && (
              <div className="absolute right-3 top-1/2 -translate-y-1/2">
                {confirmPassword === formData.password ? (
                  <svg
                    className="w-4 h-4 text-emerald-500"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                ) : (
                  <svg
                    className="w-4 h-4 text-red-400"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2.5}
                      d="M6 18L18 6M6 6l12 12"
                    />
                  </svg>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Submit */}
        <button
          type="submit"
          disabled={loading}
          className="w-full py-2.5 px-4 bg-[#1a365d] hover:bg-[#2d4a7c] text-white text-sm font-semibold rounded-[6px] transition-colors shadow-sm hover:shadow-md disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 mt-1"
        >
          {loading ? (
            <>
              <svg
                className="animate-spin w-4 h-4"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                />
              </svg>
              Creating account…
            </>
          ) : (
            "Create Account"
          )}
        </button>
      </form>

      {/* Footer link */}
      <p className="text-sm text-center mt-6 text-[#4a5568]">
        Already have an account?{" "}
        <Link
          href="/login"
          className="text-[#3182ce] hover:text-[#2b6cb0] font-medium transition-colors"
        >
          Sign in
        </Link>
      </p>
    </AuthLayout>
  );
}

function EyeIcon() {
  return (
    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.8}
        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
      />
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.8}
        d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
      />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.8}
        d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"
      />
    </svg>
  );
}
