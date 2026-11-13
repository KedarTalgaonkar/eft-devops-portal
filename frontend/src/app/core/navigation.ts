import { ToolName } from '../shared/tool-logo';

export interface NavItem {
  path: string;
  label: string;
  icon: string;
  logo?: ToolName;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

/** Drives the sidebar. Add a route in app.routes.ts and an entry here for a new module. */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [{ path: 'dashboard', label: 'Command centre', icon: 'space_dashboard' }],
  },
  {
    label: 'Delivery',
    items: [
      { path: 'pipelines', label: 'Pipelines', icon: 'rocket_launch', logo: 'jenkins' },
      { path: 'github', label: 'Source code', icon: 'code', logo: 'github' },
      { path: 'artifacts', label: 'Artifacts', icon: 'deployed_code', logo: 'jfrog' },
    ],
  },
  {
    label: 'Quality & security',
    items: [
      { path: 'quality', label: 'Code quality', icon: 'verified', logo: 'sonarqube' },
      { path: 'sbom', label: 'SBOM & vulnerabilities', icon: 'security' },
    ],
  },
  {
    label: 'Operations',
    items: [
      { path: 'monitoring', label: 'Monitoring', icon: 'monitoring', logo: 'grafana' },
      { path: 'inventory', label: 'Application inventory', icon: 'inventory_2', logo: 'tomcat' },
    ],
  },
];

export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((g) => g.items);
