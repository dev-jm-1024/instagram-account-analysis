import { useContext } from 'react';
import { NavigationContext, type NavigationContextValue } from './NavigationContext';

/** Access the active tab and the navigation setter. */
export function useNavigation(): NavigationContextValue {
  const ctx = useContext(NavigationContext);
  if (!ctx) {
    throw new Error('useNavigation must be used within a <NavigationContext>');
  }
  return ctx;
}
