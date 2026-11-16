export type GateStatus = 'OK' | 'WARN' | 'ERROR' | 'NONE';

export interface QualityProject {
  key: string;
  name: string;
  qualityGate: GateStatus;
  bugs: number;
  vulnerabilities: number;
  securityHotspots: number;
  codeSmells: number;
  coverage: number;
  duplication: number;
  reliabilityRating: string;
  securityRating: string;
  maintainabilityRating: string;
  linesOfCode: number;
  lastAnalysis: string;
  url: string;
}

export const GATE_LABEL: Record<GateStatus, string> = {
  OK: 'Passed',
  WARN: 'Warning',
  ERROR: 'Failed',
  NONE: 'Not computed',
};
