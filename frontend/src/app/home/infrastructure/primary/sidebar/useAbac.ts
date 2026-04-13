/**
 * ABAC (Attribute-Based Access Control) composable for sidebar navigation.
 * Evaluates client-side from JWT token claims per TDD §12.3.
 */

import { useRoles } from '@cbs/admin-plugin/composables/auth/useRoles';
import type { SidebarGroup } from './types';

export function useAbac() {
  const roles = useRoles();

  /**
   * Check if the user has at least one of the required roles.
   * Normalizes both "ADMIN" and "ROLE_ADMIN" formats.
   */
  function hasRole(requiredRoles?: string[]): boolean {
    if (!requiredRoles || requiredRoles.length === 0) return true;
    const normalizedUserRoles = roles.map((r) => normalizeRole(r));
    const normalizedRequired = requiredRoles.map((r) => normalizeRole(r));
    return normalizedRequired.some((r) => normalizedUserRoles.includes(r));
  }

  /**
   * Filter sidebar groups by role, also filtering items within each group.
   * Returns only groups the user has at least one matching role for,
   * with only the items they can access.
   */
  function visibleGroups(groups: SidebarGroup[]): SidebarGroup[] {
    return groups
      .filter((g) => hasRole(g.roles))
      .map((g) => ({
        ...g,
        items: g.items.filter((item) => hasRole(item.roles)),
      }))
      .filter((g) => g.items.length > 0);
  }

  /**
   * Check if the user can perform an action on an entity.
   * Stub — will be extended when entity-level permissions are defined.
   */
  function can(_action: 'READ' | 'CREATE' | 'UPDATE' | 'DELETE', _entity: string): boolean {
    return true;
  }

  return { visibleGroups, hasRole, can };
}

/**
 * Normalize role strings to handle both "ADMIN" and "ROLE_ADMIN" formats.
 */
function normalizeRole(role: string): string {
  const upper = role.toUpperCase();
  if (upper.startsWith('ROLE_')) return upper;
  return `ROLE_${upper}`;
}
