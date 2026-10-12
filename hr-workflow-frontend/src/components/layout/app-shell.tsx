"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { Menu } from "lucide-react";
import { useTheme } from "next-themes";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { CreateWorkflowDialog } from "@/components/workflow/create-workflow-dialog";
import { useMe } from "@/hooks/queries";
import { useAuthGuard } from "@/lib/auth";

const CreateWorkflowContext = createContext<() => void>(() => {});

/** Opens the "New workflow" dialog from anywhere inside the app shell. */
export const useCreateWorkflow = () => useContext(CreateWorkflowContext);

/** Applies the theme saved in the user's preferences once their profile loads. */
function ThemePreferenceSync() {
  const { data: me } = useMe();
  const { setTheme } = useTheme();
  useEffect(() => {
    if (me?.theme) setTheme(me.theme.toLowerCase());
  }, [me?.theme, setTheme]);
  return null;
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const authReady = useAuthGuard();
  const pathname = usePathname();
  const [createOpen, setCreateOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Close the mobile drawer whenever the route changes
  useEffect(() => setDrawerOpen(false), [pathname]);

  if (!authReady) {
    return (
      <div className="flex h-screen">
        <div className="hidden w-64 border-r p-4 md:block">
          <Skeleton className="h-8 w-32" />
        </div>
        <div className="flex-1 p-8">
          <Skeleton className="h-8 w-48" />
        </div>
      </div>
    );
  }

  const openCreate = () => {
    setDrawerOpen(false);
    setCreateOpen(true);
  };

  return (
    <CreateWorkflowContext.Provider value={openCreate}>
      <ThemePreferenceSync />
      <div className="flex h-screen overflow-hidden bg-muted/30">
        <aside className="hidden w-64 shrink-0 border-r md:block">
          <AppSidebar onCreateWorkflow={openCreate} />
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex h-14 shrink-0 items-center gap-3 border-b bg-card px-4 md:hidden">
            <DialogPrimitive.Root open={drawerOpen} onOpenChange={setDrawerOpen}>
              <DialogPrimitive.Trigger asChild>
                <Button variant="ghost" size="icon" aria-label="Open navigation">
                  <Menu />
                </Button>
              </DialogPrimitive.Trigger>
              <DialogPrimitive.Portal>
                <DialogPrimitive.Overlay className="fixed inset-0 z-40 bg-black/40 data-[state=open]:animate-in data-[state=open]:fade-in-0" />
                <DialogPrimitive.Content
                  aria-describedby={undefined}
                  className="fixed inset-y-0 left-0 z-50 w-72 max-w-[85vw] border-r shadow-xl data-[state=open]:animate-in data-[state=open]:slide-in-from-left"
                >
                  <DialogPrimitive.Title className="sr-only">Navigation</DialogPrimitive.Title>
                  <AppSidebar onCreateWorkflow={openCreate} onNavigate={() => setDrawerOpen(false)} />
                </DialogPrimitive.Content>
              </DialogPrimitive.Portal>
            </DialogPrimitive.Root>
            <span className="font-semibold">HR Workflow</span>
          </header>

          <main id="main" className="min-h-0 flex-1 overflow-y-auto">
            {children}
          </main>
        </div>
      </div>
      <CreateWorkflowDialog open={createOpen} onOpenChange={setCreateOpen} />
    </CreateWorkflowContext.Provider>
  );
}
