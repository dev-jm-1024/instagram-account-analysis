import type { NavGroup } from '@/domain/types';
import { TAB_REGISTRY, type TabDefinition } from '@/app/routing/tabRegistry';

export interface NavSection {
  group: NavGroup;
  items: TabDefinition[];
}

const GROUP_ORDER: NavGroup[] = ['핵심', '관계', '활동', '계정', '도구'];

/** Groups the tab registry into ordered sidebar sections (derived, not duplicated). */
export const NAV_SECTIONS: NavSection[] = GROUP_ORDER.map((group) => ({
  group,
  items: TAB_REGISTRY.filter((tab) => tab.group === group),
})).filter((section) => section.items.length > 0);
