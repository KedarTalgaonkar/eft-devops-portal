import { ToolName } from '../shared/tool-logo';
import { PortalModule, PortalRole } from './auth';

export interface NavItem {
  path: string;
  label: string;
  icon: string;
  logo?: ToolName;
  /** Hidden unless the user's access groups grant at least View (the backend enforces the same rule). */
  module?: PortalModule;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
  /** Only shown to users with this role (the backend enforces the same rule). */
  requires?: PortalRole;
}

/** Drives the sidebar. Add a route in app.routes.ts and an entry here for a new module. */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [{ path: 'dashboard', label: 'Command centre', icon: 'space_dashboard', module: 'DASHBOARD' }],
  },
  {
    label: 'Delivery',
    items: [
      { path: 'pipelines', label: 'Pipelines', icon: 'rocket_launch', logo: 'jenkins', module: 'PIPELINES' },
      { path: 'github', label: 'Source code', icon: 'code', logo: 'github', module: 'SOURCE_CODE' },
      { path: 'artifacts', label: 'Artifacts', icon: 'deployed_code', logo: 'jfrog', module: 'ARTIFACTS' },
    ],
  },
  {
    label: 'Quality & security',
    items: [
      { path: 'quality', label: 'Code quality', icon: 'verified', logo: 'sonarqube', module: 'QUALITY' },
      { path: 'sbom', label: 'SBOM & vulnerabilities', icon: 'security', module: 'SBOM' },
    ],
  },
  {
    label: 'Operations',
    items: [
      { path: 'monitoring', label: 'Monitoring', icon: 'monitoring', logo: 'grafana', module: 'MONITORING' },
      { path: 'inventory', label: 'Application inventory', icon: 'inventory_2', logo: 'tomcat', module: 'INVENTORY' },
    ],
  },
  {
    label: 'Administration',
    requires: 'ADMIN',
    items: [{ path: 'admin', label: 'Users, access & audit', icon: 'admin_panel_settings' }],
  },
];

export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((g) => g.items);
