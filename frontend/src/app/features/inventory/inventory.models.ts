import { Environment } from '../../core/models';

export interface DeploymentView {
  id: number;
  applicationId: number;
  application: string;
  client: string;
  owner: string | null;
  environment: Environment;
  platform: string;
  contextName: string;
  baseUrl: string | null;
  contextUrl: string | null;
  tomcatVersion: string | null;
  javaVersion: string | null;
  tomcatAdminDone: boolean;
  buildXmlDone: boolean;
  jenkinsJob: string | null;
  effectiveJenkinsJob: string;
  repositories: string[];
  updatedAt: string;
}

export interface ApplicationSummary {
  id: number;
  name: string;
  client: string;
  owner: string | null;
  deploymentCount: number;
  environments: Environment[];
  repositories: string[];
  updatedAt: string;
}

export interface ImportResult {
  fileName: string;
  sheetsRead: number;
  rowsRead: number;
  applicationsCreated: number;
  applicationsUpdated: number;
  deploymentsCreated: number;
  deploymentsUpdated: number;
  newEnvironments: string[];
  warnings: string[];
}

export interface ChangeView {
  id: number;
  applicationId: number | null;
  application: string | null;
  client: string | null;
  deploymentId: number | null;
  environment: Environment | null;
  contextName: string | null;
  changeType: 'APPLICATION_ADDED' | 'DEPLOYMENT_ADDED' | 'DEPLOYMENT_REMOVED' | 'FIELD_CHANGED';
  field: string | null;
  oldValue: string | null;
  newValue: string | null;
  source: 'IMPORT' | 'PORTAL';
  changedAt: string;
}

/** Major Java line from the normalised version: "8u351" -> "Java 8", "17.0.12" -> "Java 17". */
export function javaLine(v: string | null): string {
  if (!v) {
    return 'Unknown';
  }
  const m = /^(\d+)/.exec(v);
  return m ? `Java ${m[1]}` : v;
}

/** Tomcat major line: "9.0.67" -> "Tomcat 9", "8.032" -> "Tomcat 8". */
export function tomcatLine(v: string | null): string {
  if (!v) {
    return 'Unknown';
  }
  const m = /^(\d+)/.exec(v.trim());
  return m ? `Tomcat ${m[1]}` : v;
}

export function countBy<T>(items: T[], key: (t: T) => string): { label: string; value: number }[] {
  const map = new Map<string, number>();
  items.forEach((i) => map.set(key(i), (map.get(key(i)) ?? 0) + 1));
  return [...map.entries()]
    .map(([label, value]) => ({ label, value }))
    .sort((a, b) => b.value - a.value);
}
