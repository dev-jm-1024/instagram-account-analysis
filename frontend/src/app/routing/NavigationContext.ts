import { createContext } from 'react';
import type { ActiveTab } from '@/domain/types';

export interface NavigationContextValue {
  activeTab: ActiveTab;
  setActiveTab: (tab: ActiveTab) => void;
}

export const NavigationContext = createContext<NavigationContextValue | null>(null);
