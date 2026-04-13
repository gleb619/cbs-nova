/**
 * Sidebar navigation types.
 * Menu content is defined in sidebarConfig.ts; presentational components consume these types.
 */

export interface SidebarItem {
  /** Unique key for the item */
  key: string;
  /** Display label (i18n key or plain text) */
  label: string;
  /** Vue Router route name for internal navigation */
  routeName?: string;
  /** External URL for links like DSL Rules, Temporal UI */
  externalUrl?: string;
  /** Optional role filter — if set, only users with one of these roles see the item */
  roles?: string[];
}

export interface SidebarGroup {
  /** Unique key for the group */
  key: string;
  /** Display label (i18n key or plain text) */
  label: string;
  /** Icon identifier (e.g. Heroicon name) */
  icon: string;
  /** Optional role filter — if set, only users with one of these roles see the group */
  roles?: string[];
  /** Navigation items within this group */
  items: SidebarItem[];
}
