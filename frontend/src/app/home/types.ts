export interface SidebarItem {
  key: string;
  label: string;
  routeName?: string;
  externalUrl?: string;
  roles?: string[];
}

export interface SidebarGroup {
  key: string;
  label: string;
  icon: string;
  roles?: string[];
  items: SidebarItem[];
}
