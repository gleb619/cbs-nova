import { useRoles } from '@cbs/admin-plugin/composables/auth/useRoles';
import type { SidebarGroup } from './types';

export function useAbac() {
  const roles = useRoles();

  function hasRole(requiredRoles?: string[]): boolean {
    if (!requiredRoles || requiredRoles.length === 0) return true;
    const normalized = roles.map(normalizeRole);
    return requiredRoles.map(normalizeRole).some(r => normalized.includes(r));
  }

  function visibleGroups(groups: SidebarGroup[]): SidebarGroup[] {
    return groups
      .filter(g => hasRole(g.roles))
      .map(g => ({ ...g, items: g.items.filter(i => hasRole(i.roles)) }))
      .filter(g => g.items.length > 0);
  }

  function can(_action: 'READ' | 'CREATE' | 'UPDATE' | 'DELETE', _entity: string): boolean {
    return true;
  }

  return { visibleGroups, hasRole, can };
}

function normalizeRole(role: string): string {
  const u = role.toUpperCase();
  return u.startsWith('ROLE_') ? u : `ROLE_${u}`;
}
