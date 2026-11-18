import { BuildResult, Environment, HealthState, Rag, Severity } from '../../core/models';
import { PipelineTotals } from '../pipelines/pipelines.models';

export interface EnvironmentCount {
  environment: Environment;
  name: string;
  category: 'PROD' | 'NON_PROD';
  applications: number;
  deployments: number;
}

export interface HealthSummary {
  up: number;
  down: number;
  unknown: number;
  checkedAt: string | null;
}

export interface DashboardOverview {
  applications: number;
  deployments: number;
  owners: number;
  clients: number;
  environments: EnvironmentCount[];
  /** null when the user has no access to Pipelines. */
  pipelines: PipelineTotals | null;
  services: HealthSummary;
  firingAlerts: number;
  failingQualityGates: number;
  integrationsDown: number;
  generatedAt: string;
}

export interface EnvironmentCell {
  environment: Environment;
  deployments: number;
  up: number;
  down: number;
  health: HealthState;
  lastBuildResult: BuildResult | null;
  lastBuildAt: string | null;
  successRate: number | null;
}

export interface EnvironmentStatusRow {
  applicationId: number;
  project: string;
  client: string;
  owner: string | null;
  cells: EnvironmentCell[];
}

export interface ServiceHealth {
  deploymentId: number;
  applicationId: number;
  project: string;
  client: string;
  owner: string | null;
  environment: Environment;
  contextName: string;
  url: string | null;
  state: HealthState;
  httpStatus: number | null;
  responseTimeMs: number | null;
  checkedAt: string;
  detail: string | null;
}

export interface AreaStatus {
  key: 'delivery' | 'services' | 'quality' | 'security' | 'operations';
  label: string;
  status: Rag;
  headline: string;
  metric: string;
  link: string;
}

export interface AttentionItem {
  severity: Severity;
  area: string;
  title: string;
  detail: string;
  owner: string | null;
  environment: Environment | null;
  since: string | null;
  link: string;
}

export interface AttentionReport {
  overall: Rag;
  areas: AreaStatus[];
  items: AttentionItem[];
  generatedAt: string;
}
