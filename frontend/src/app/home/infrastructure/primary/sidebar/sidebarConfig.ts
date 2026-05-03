/**
 * Sidebar navigation configuration.
 * Defines the complete menu structure with ABAC role filters per TDD §12.1–§12.2.
 */

import type { SidebarGroup } from './types';

export const sidebarGroups: SidebarGroup[] = [
  {
    key: 'finance',
    label: 'sidebar.finance',
    icon: 'banknotes',
    roles: ['ROLE_ADMIN', 'ROLE_BA'],
    items: [
      { key: 'loans', label: 'sidebar.loans', routeName: 'Loans' },
      { key: 'currencies', label: 'sidebar.currencies', routeName: 'Currencies' },
      { key: 'accounts', label: 'sidebar.accounts', routeName: 'Accounts' },
    ],
  },
  {
    key: 'operations',
    label: 'sidebar.operations',
    icon: 'building',
    roles: ['ROLE_ADMIN', 'ROLE_OPS'],
    items: [
      { key: 'branches', label: 'sidebar.branches', routeName: 'Branches' },
      { key: 'calendar', label: 'sidebar.calendar', routeName: 'Calendar' },
      { key: 'dictionaries', label: 'sidebar.dictionaries', routeName: 'Dictionaries' },
    ],
  },
  {
    key: 'orchestration',
    label: 'sidebar.orchestration',
    icon: 'cpu-chip',
    roles: ['ROLE_ADMIN', 'ROLE_OPS', 'ROLE_DEVELOPER'],
    items: [
      { key: 'executions', label: 'sidebar.executions', routeName: 'ExecutionList' },
      { key: 'event-log', label: 'sidebar.eventLog', routeName: 'EventLog' },
      {
        key: 'dsl-rules',
        label: 'sidebar.dslRules',
        externalUrl: 'https://gitea/cbs-rules',
        roles: ['ROLE_DEVELOPER'],
      },
      {
        key: 'temporal-ui',
        label: 'sidebar.temporalUi',
        externalUrl: 'http://localhost:8080',
        roles: ['ROLE_OPS', 'ROLE_ADMIN'],
      },
    ],
  },
  {
    key: 'system',
    label: 'sidebar.system',
    icon: 'cog',
    roles: ['ROLE_ADMIN'],
    items: [
      { key: 'settings', label: 'sidebar.settings', routeName: 'Settings' },
      { key: 'dictionaries', label: 'sidebar.dictionaries', routeName: 'Dictionaries' },
      { key: 'users', label: 'sidebar.users', routeName: 'Users' },
    ],
  },
];
