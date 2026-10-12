"use client";

import { Settings2, ShieldAlert, SlidersHorizontal, UserRound } from "lucide-react";
import { EmailForm, NameForm, PasswordForm } from "@/components/settings/account-forms";
import { DeleteAccountSection, SessionSection } from "@/components/settings/danger-zone";
import { PreferencesForm } from "@/components/settings/preferences-form";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useMe } from "@/hooks/queries";
import { getErrorMessage } from "@/lib/errors";

export default function SettingsPage() {
  const { data: user, isLoading, isError, error } = useMe();

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 sm:px-8 sm:py-8">
      <PageHeader title="Settings" description="Manage your account and preferences." />

      {isLoading ? (
        <Skeleton className="h-96 w-full rounded-xl" />
      ) : isError || !user ? (
        <EmptyState icon={Settings2} title="Could not load your profile" description={getErrorMessage(error)} />
      ) : (
        <Tabs defaultValue="account">
          <TabsList className="flex w-full overflow-x-auto sm:w-auto">
            <TabsTrigger value="account">
              <UserRound /> Account
            </TabsTrigger>
            <TabsTrigger value="preferences">
              <SlidersHorizontal /> Preferences
            </TabsTrigger>
            <TabsTrigger value="security">
              <ShieldAlert /> Security
            </TabsTrigger>
          </TabsList>
          <TabsContent value="account" className="space-y-5">
            <NameForm user={user} />
            <EmailForm user={user} />
          </TabsContent>
          <TabsContent value="preferences">
            <PreferencesForm user={user} />
          </TabsContent>
          <TabsContent value="security" className="space-y-5">
            <PasswordForm />
            <SessionSection user={user} />
            <DeleteAccountSection />
          </TabsContent>
        </Tabs>
      )}
    </div>
  );
}
