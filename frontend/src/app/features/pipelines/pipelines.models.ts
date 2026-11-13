import { BuildResult, Environment, Kpi } from '../../core/models';

export interface BuildView {
  number: number;
  result: BuildResult;
  startedAt: string | null;
  durationSeconds: number;
  triggeredBy: string | null;
  version: string | null;
  deployment: boolean;
  url: string | null;
}

/** One row of "EFT Pipeline Metrics": a project in one environment. */
export interface PipelineMetric {
  applicationId: number;
  project: string;
  client: string;
  owner: string | null;
  environment: Environment;
  jenkinsJob: string;
  totalBuilds: number;
  success: number;
  failed: number;
  aborted: number;
  unstable: number;
  inProgress: number;
  successRate: number | null;
  lastBuild: BuildView | null;
  error: string | null;
}

export interface DeploymentHistoryEntry {
  applicationId: number;
  project: string;
  client: string;
  environment: Environment;
  jenkinsJob: string;
  buildNumber: number;
  version: string | null;
  result: BuildResult;
  deployedAt: string | null;
  triggeredBy: string | null;
  url: string | null;
}

export interface PipelineTotals {
  projects: number;
  totalBuilds: number;
  success: number;
  failed: number;
  aborted: number;
  unstable: number;
  inProgress: number;
  successRate: number | null;
  days: number;
}

export interface DailyBuilds {
  date: string;
  success: number;
  failed: number;
  aborted: number;
  unstable: number;
  deployments: number;
}

export interface EnvironmentSummary {
  environment: Environment;
  pipelines: number;
  totalBuilds: number;
  successRate: number | null;
  redPipelines: number;
}

export interface ProblemPipeline {
  applicationId: number;
  project: string;
  client: string;
  owner: string | null;
  environment: Environment;
  jenkinsJob: string;
  failed: number;
  totalBuilds: number;
  successRate: number | null;
  lastResult: BuildResult | null;
  lastFailureAt: string | null;
  red: boolean;
}

export interface PipelineInsights {
  days: number;
  pipelines: number;
  redPipelines: number;
  totalBuilds: Kpi;
  successRate: Kpi;
  deploymentsPerDay: Kpi;
  failureRate: Kpi;
  meanTimeToRecoverHours: Kpi;
  averageBuildMinutes: Kpi;
  environments: EnvironmentSummary[];
  problems: ProblemPipeline[];
}
