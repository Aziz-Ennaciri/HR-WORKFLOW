"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthGuard, getUser, setAuth, clearAuth } from "@/lib/auth";
import api from "@/lib/api";
import Sidebar from "@/components/layouts/sidebar";
import { getWorkflowsUrl } from "@/lib/workflows";

type Tab = "account" | "preferences" | "security" | "danger";

export default function SettingsPage() {
  const router = useRouter();
  const authReady = useAuthGuard();
  const [activeTab, setActiveTab] = useState<Tab>("account");
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [user, setUser] = useState<any>(null);

  // Account – name form
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [nameSaving, setNameSaving] = useState(false);
  const [nameMsg, setNameMsg] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  // Account – email form
  const [newEmail, setNewEmail] = useState("");
  const [emailPassword, setEmailPassword] = useState("");
  const [emailSaving, setEmailSaving] = useState(false);
  const [emailMsg, setEmailMsg] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  // Account – password form
  const [currentPw, setCurrentPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [confirmPw, setConfirmPw] = useState("");
  const [pwSaving, setPwSaving] = useState(false);
  const [pwMsg, setPwMsg] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  // Preferences
  const [theme, setTheme] = useState("SYSTEM");
  const [language, setLanguage] = useState("EN");
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [prefSaving, setPrefSaving] = useState(false);
  const [prefMsg, setPrefMsg] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  // Danger zone
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!authReady) return;
    api.get(getWorkflowsUrl()).then((r) => setWorkflows(r.data)).catch(() => {});
    api.get("/users/me").then((r) => {
      const u = r.data;
      setUser(u);
      setFirstName(u.firstName || "");
      setLastName(u.lastName || "");
      setTheme(u.theme || "SYSTEM");
      setLanguage(u.language || "EN");
      setEmailNotifications(u.emailNotificationsEnabled ?? true);
    }).catch(() => {});
  }, [authReady]);

  const inputCls = "w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm text-gray-900 focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400 focus:bg-white outline-none transition-all placeholder:text-gray-400";
  const labelCls = "block text-sm font-medium text-gray-700 mb-1.5";

  const flash = (
    setter: (v: { type: "ok" | "err"; text: string } | null) => void,
    type: "ok" | "err",
    text: string,
  ) => {
    setter({ type, text });
    setTimeout(() => setter(null), 4000);
  };

  const handleNameSave = async () => {
    if (!firstName.trim() || !lastName.trim()) return;
    setNameSaving(true);
    try {
      const res = await api.patch("/users/me", { firstName: firstName.trim(), lastName: lastName.trim() });
      setUser((u: any) => ({ ...u, firstName: res.data.firstName, lastName: res.data.lastName }));
      // update sessionStorage user
      const stored = getUser();
      if (stored) setAuth(sessionStorage.getItem("token")!, { ...stored, firstName: res.data.firstName, lastName: res.data.lastName });
      flash(setNameMsg, "ok", "Name updated successfully.");
    } catch (e: any) {
      flash(setNameMsg, "err", e.response?.data?.message || "Failed to update name.");
    } finally {
      setNameSaving(false);
    }
  };

  const handleEmailSave = async () => {
    if (!newEmail.trim() || !emailPassword.trim()) return;
    setEmailSaving(true);
    try {
      const res = await api.put("/users/me/email", { newEmail: newEmail.trim(), currentPassword: emailPassword });
      setUser((u: any) => ({ ...u, email: res.data.email }));
      setNewEmail("");
      setEmailPassword("");
      flash(setEmailMsg, "ok", "Email updated. Please log in again.");
      setTimeout(() => { clearAuth(); router.push("/login"); }, 2000);
    } catch (e: any) {
      flash(setEmailMsg, "err", e.response?.data?.message || "Failed to update email.");
    } finally {
      setEmailSaving(false);
    }
  };

  const handlePasswordSave = async () => {
    if (!currentPw || !newPw || !confirmPw) return;
    if (newPw !== confirmPw) { flash(setPwMsg, "err", "New passwords don't match."); return; }
    if (newPw.length < 8) { flash(setPwMsg, "err", "Password must be at least 8 characters."); return; }
    setPwSaving(true);
    try {
      await api.put("/users/me/password", { currentPassword: currentPw, newPassword: newPw });
      setCurrentPw(""); setNewPw(""); setConfirmPw("");
      flash(setPwMsg, "ok", "Password changed successfully.");
    } catch (e: any) {
      flash(setPwMsg, "err", e.response?.data?.message || "Failed to change password.");
    } finally {
      setPwSaving(false);
    }
  };

  const handlePrefSave = async () => {
    setPrefSaving(true);
    try {
      const res = await api.put("/users/me/preferences", { theme, language, emailNotificationsEnabled: emailNotifications });
      setUser((u: any) => ({ ...u, theme: res.data.theme, language: res.data.language, emailNotificationsEnabled: res.data.emailNotificationsEnabled }));
      flash(setPrefMsg, "ok", "Preferences saved.");
    } catch (e: any) {
      flash(setPrefMsg, "err", e.response?.data?.message || "Failed to save preferences.");
    } finally {
      setPrefSaving(false);
    }
  };

  const handleDeleteAccount = async () => {
    if (deleteConfirmText !== "DELETE") return;
    setDeleting(true);
    try {
      await api.delete("/users/me");
      clearAuth();
      router.push("/login");
    } catch (e: any) {
      alert(e.response?.data?.message || "Failed to delete account.");
      setDeleting(false);
    }
  };

  if (!authReady) return null;

  const tabs: { id: Tab; label: string; icon: JSX.Element }[] = [
    {
      id: "account", label: "Account",
      icon: <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" /></svg>,
    },
    {
      id: "preferences", label: "Preferences",
      icon: <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" /></svg>,
    },
    {
      id: "security", label: "Security",
      icon: <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" /></svg>,
    },
    {
      id: "danger", label: "Danger Zone",
      icon: <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg>,
    },
  ];

  const Alert = ({ msg }: { msg: { type: "ok" | "err"; text: string } | null }) =>
    msg ? (
      <div className={`mt-3 px-4 py-2.5 rounded-xl text-sm font-medium ${msg.type === "ok" ? "bg-green-50 text-green-700 border border-green-100" : "bg-red-50 text-red-600 border border-red-100"}`}>
        {msg.text}
      </div>
    ) : null;

  const Section = ({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) => (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 mb-5">
      <div className="mb-5">
        <h3 className="text-base font-semibold text-gray-900">{title}</h3>
        {description && <p className="text-sm text-gray-500 mt-0.5">{description}</p>}
      </div>
      {children}
    </div>
  );

  const SaveBtn = ({ onClick, saving, disabled }: { onClick: () => void; saving: boolean; disabled?: boolean }) => (
    <button
      onClick={onClick}
      disabled={saving || disabled}
      className="mt-5 px-5 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white text-sm font-semibold rounded-xl transition-all shadow-sm flex items-center gap-2"
    >
      {saving && <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />}
      {saving ? "Saving…" : "Save changes"}
    </button>
  );

  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
        onWorkflowDeleted={() => api.get(getWorkflowsUrl()).then((r) => setWorkflows(r.data))}
      />

      <div className="flex-1 overflow-auto">
        <div className="max-w-4xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
            <p className="text-sm text-gray-500 mt-1">Manage your account and preferences.</p>
          </div>

          <div className="flex gap-6">
            {/* Left nav */}
            <nav className="w-44 flex-shrink-0">
              <ul className="space-y-1">
                {tabs.map((t) => (
                  <li key={t.id}>
                    <button
                      onClick={() => setActiveTab(t.id)}
                      className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
                        activeTab === t.id
                          ? "bg-blue-50 text-blue-700"
                          : "text-gray-600 hover:bg-gray-100 hover:text-gray-900"
                      } ${t.id === "danger" ? (activeTab === t.id ? "bg-red-50 text-red-700" : "text-red-500 hover:bg-red-50 hover:text-red-700") : ""}`}
                    >
                      {t.icon}
                      {t.label}
                    </button>
                  </li>
                ))}
              </ul>
            </nav>

            {/* Content */}
            <div className="flex-1 min-w-0">

              {/* ── ACCOUNT ── */}
              {activeTab === "account" && (
                <div>
                  <Section title="Full Name" description="Update your display name.">
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className={labelCls}>First name</label>
                        <input type="text" value={firstName} onChange={(e) => setFirstName(e.target.value)} className={inputCls} placeholder="First name" />
                      </div>
                      <div>
                        <label className={labelCls}>Last name</label>
                        <input type="text" value={lastName} onChange={(e) => setLastName(e.target.value)} className={inputCls} placeholder="Last name" />
                      </div>
                    </div>
                    <Alert msg={nameMsg} />
                    <SaveBtn onClick={handleNameSave} saving={nameSaving} disabled={!firstName.trim() || !lastName.trim()} />
                  </Section>

                  <Section title="Email Address" description="Change your login email. You'll be signed out after changing.">
                    <div className="space-y-4">
                      <div>
                        <label className={labelCls}>Current email</label>
                        <input type="email" value={user?.email || ""} disabled className={inputCls + " opacity-50 cursor-not-allowed"} />
                      </div>
                      <div>
                        <label className={labelCls}>New email</label>
                        <input type="email" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} className={inputCls} placeholder="new@example.com" />
                      </div>
                      <div>
                        <label className={labelCls}>Current password (to confirm)</label>
                        <input type="password" value={emailPassword} onChange={(e) => setEmailPassword(e.target.value)} className={inputCls} placeholder="••••••••" />
                      </div>
                    </div>
                    <Alert msg={emailMsg} />
                    <SaveBtn onClick={handleEmailSave} saving={emailSaving} disabled={!newEmail.trim() || !emailPassword.trim()} />
                  </Section>

                  <Section title="Password" description="Use a strong password of at least 8 characters.">
                    <div className="space-y-4">
                      <div>
                        <label className={labelCls}>Current password</label>
                        <input type="password" value={currentPw} onChange={(e) => setCurrentPw(e.target.value)} className={inputCls} placeholder="••••••••" />
                      </div>
                      <div>
                        <label className={labelCls}>New password</label>
                        <input type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} className={inputCls} placeholder="Min. 8 characters" />
                      </div>
                      <div>
                        <label className={labelCls}>Confirm new password</label>
                        <input type="password" value={confirmPw} onChange={(e) => setConfirmPw(e.target.value)} className={inputCls} placeholder="Repeat new password" />
                      </div>
                    </div>
                    <Alert msg={pwMsg} />
                    <SaveBtn onClick={handlePasswordSave} saving={pwSaving} disabled={!currentPw || !newPw || !confirmPw} />
                  </Section>
                </div>
              )}

              {/* ── PREFERENCES ── */}
              {activeTab === "preferences" && (
                <div>
                  <Section title="Appearance & Language" description="Customize how the app looks and behaves for you.">
                    <div className="space-y-5">
                      <div>
                        <label className={labelCls}>Theme</label>
                        <div className="grid grid-cols-3 gap-3">
                          {(["LIGHT", "DARK", "SYSTEM"] as const).map((t) => (
                            <button
                              key={t}
                              onClick={() => setTheme(t)}
                              className={`py-2.5 px-3 rounded-xl border text-sm font-medium transition-all ${
                                theme === t
                                  ? "border-blue-500 bg-blue-50 text-blue-700"
                                  : "border-gray-200 bg-gray-50 text-gray-600 hover:border-gray-300"
                              }`}
                            >
                              {t === "LIGHT" ? "☀️ Light" : t === "DARK" ? "🌙 Dark" : "💻 System"}
                            </button>
                          ))}
                        </div>
                      </div>
                      <div>
                        <label className={labelCls}>Language</label>
                        <div className="grid grid-cols-2 gap-3">
                          {(["EN", "FR"] as const).map((l) => (
                            <button
                              key={l}
                              onClick={() => setLanguage(l)}
                              className={`py-2.5 px-3 rounded-xl border text-sm font-medium transition-all ${
                                language === l
                                  ? "border-blue-500 bg-blue-50 text-blue-700"
                                  : "border-gray-200 bg-gray-50 text-gray-600 hover:border-gray-300"
                              }`}
                            >
                              {l === "EN" ? "🇬🇧 English" : "🇫🇷 Français"}
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>
                    <Alert msg={prefMsg} />
                    <SaveBtn onClick={handlePrefSave} saving={prefSaving} />
                  </Section>

                  <Section title="Notifications" description="Choose when you want to receive email alerts.">
                    <label className="flex items-center justify-between cursor-pointer">
                      <div>
                        <p className="text-sm font-medium text-gray-900">Workflow execution alerts</p>
                        <p className="text-xs text-gray-500 mt-0.5">Email me when a workflow completes or fails.</p>
                      </div>
                      <button
                        onClick={() => setEmailNotifications((v) => !v)}
                        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${emailNotifications ? "bg-blue-600" : "bg-gray-200"}`}
                      >
                        <span className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${emailNotifications ? "translate-x-6" : "translate-x-1"}`} />
                      </button>
                    </label>
                    <Alert msg={prefMsg} />
                    <SaveBtn onClick={handlePrefSave} saving={prefSaving} />
                  </Section>
                </div>
              )}

              {/* ── SECURITY ── */}
              {activeTab === "security" && (
                <div>
                  <Section title="Session Info" description="Details about your current account activity.">
                    <div className="space-y-3">
                      <div className="flex items-center justify-between py-3 border-b border-gray-100">
                        <div>
                          <p className="text-sm font-medium text-gray-700">Last login</p>
                          <p className="text-xs text-gray-500 mt-0.5">
                            {user?.lastLoginAt
                              ? new Date(user.lastLoginAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })
                              : "No record"}
                          </p>
                        </div>
                        <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                      </div>
                      <div className="flex items-center justify-between py-3 border-b border-gray-100">
                        <div>
                          <p className="text-sm font-medium text-gray-700">Account email</p>
                          <p className="text-xs text-gray-500 mt-0.5">{user?.email}</p>
                        </div>
                        <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" /></svg>
                      </div>
                    </div>
                  </Section>

                  <Section title="Sign Out" description="End your current session or all active sessions.">
                    <button
                      onClick={() => { clearAuth(); router.push("/login"); }}
                      className="px-5 py-2.5 bg-gray-900 hover:bg-gray-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm"
                    >
                      Sign out
                    </button>
                  </Section>

                  <Section title="Two-Factor Authentication" description="Add an extra layer of security to your account.">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium text-gray-900">Authenticator app</p>
                        <p className="text-xs text-gray-500 mt-0.5">Use an app like Google Authenticator or Authy.</p>
                      </div>
                      <span className="px-3 py-1 text-xs font-medium bg-gray-100 text-gray-500 rounded-full">Coming soon</span>
                    </div>
                  </Section>
                </div>
              )}

              {/* ── DANGER ZONE ── */}
              {activeTab === "danger" && (
                <div>
                  <div className="bg-white rounded-2xl border border-red-100 shadow-sm p-6">
                    <div className="flex items-start gap-4">
                      <div className="w-10 h-10 rounded-xl bg-red-50 flex items-center justify-center flex-shrink-0">
                        <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg>
                      </div>
                      <div className="flex-1">
                        <h3 className="text-base font-semibold text-gray-900">Delete Account</h3>
                        <p className="text-sm text-gray-500 mt-1">
                          Permanently disable your account and remove access. Your workflows will remain but cannot be managed. This action cannot be undone.
                        </p>
                        {!showDeleteConfirm ? (
                          <button
                            onClick={() => setShowDeleteConfirm(true)}
                            className="mt-5 px-5 py-2.5 bg-red-600 hover:bg-red-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm"
                          >
                            Delete my account
                          </button>
                        ) : (
                          <div className="mt-5 space-y-3">
                            <p className="text-sm font-medium text-gray-700">
                              Type <span className="font-mono bg-gray-100 px-1.5 py-0.5 rounded text-red-600">DELETE</span> to confirm:
                            </p>
                            <input
                              type="text"
                              value={deleteConfirmText}
                              onChange={(e) => setDeleteConfirmText(e.target.value)}
                              className={inputCls + " border-red-200 focus:border-red-400 focus:ring-red-500/20"}
                              placeholder="Type DELETE"
                            />
                            <div className="flex gap-3">
                              <button
                                onClick={() => { setShowDeleteConfirm(false); setDeleteConfirmText(""); }}
                                className="px-5 py-2.5 bg-gray-50 hover:bg-gray-100 text-gray-700 text-sm font-medium rounded-xl border border-gray-200 transition-all"
                              >
                                Cancel
                              </button>
                              <button
                                onClick={handleDeleteAccount}
                                disabled={deleteConfirmText !== "DELETE" || deleting}
                                className="px-5 py-2.5 bg-red-600 hover:bg-red-700 disabled:bg-red-300 text-white text-sm font-semibold rounded-xl transition-all shadow-sm flex items-center gap-2"
                              >
                                {deleting && <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />}
                                {deleting ? "Deleting…" : "Confirm delete"}
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
