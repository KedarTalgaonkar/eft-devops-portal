import { Severity } from '../../core/models';

export interface ScanView {
  id: number;
  projectName: string;
  projectVersion: string;
  format: string;
  generator: string;
  status: string;
  componentCount: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
  scannedAt: string;
}

export interface VulnerabilityView {
  vulnId: string;
  severity: Severity;
  component: string;
  version: string | null;
  fixedVersion: string | null;
  title: string | null;
}

export interface ProjectRisk {
  projectName: string;
  projectVersion: string;
  scanId: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
  componentCount: number;
  scannedAt: string;
}

export interface ComponentRisk {
  vulnId: string;
  severity: Severity;
  component: string;
  version: string | null;
  fixedVersion: string | null;
  title: string | null;
  projects: string[];
}

export interface SbomSummary {
  projects: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
  projectRisks: ProjectRisk[];
  topComponents: ComponentRisk[];
}
