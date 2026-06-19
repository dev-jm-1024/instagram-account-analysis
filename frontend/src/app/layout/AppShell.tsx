import { Suspense, useMemo, useState } from 'react';
import { Loader2 } from 'lucide-react';
import type { ActiveTab } from '@/domain/types';
import { NavigationContext, type NavigationContextValue } from '@/app/routing/NavigationContext';
import { getTabDefinition } from '@/app/routing/tabRegistry';
import Sidebar from '@/features/navigation/Sidebar';
import { AmbientBackground, EmptyState } from '@/components/ui';

/** App layout shell: backdrop, sidebar, and the active view from the registry. */
export function AppShell() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('overview');

  const navigation = useMemo<NavigationContextValue>(
    () => ({ activeTab, setActiveTab }),
    [activeTab],
  );

  const ActiveView = getTabDefinition(activeTab).Component;

  return (
    <NavigationContext value={navigation}>
      <div className="h-screen bg-ig-bg dark:bg-ig-bg-dark text-ig-text dark:text-slate-100 font-sans relative overflow-hidden antialiased transition-colors">
        <AmbientBackground />

        <div className="relative z-10 flex h-full p-6 gap-6 w-full overflow-hidden">
          <Sidebar />

          <main className="flex-1 h-full overflow-y-auto w-full pr-1">
            <div className="max-w-7xl mx-auto min-h-full">
              <div className="bg-ig-surface dark:bg-ig-surface-dark rounded-2xl border border-ig-border dark:border-ig-border-dark p-8 relative">
                <Suspense
                  fallback={<EmptyState icon={Loader2} spin title="불러오는 중…" className="min-h-[60vh]" />}
                >
                  <ActiveView />
                </Suspense>
              </div>
            </div>
          </main>
        </div>
      </div>
    </NavigationContext>
  );
}
