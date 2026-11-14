import { Injectable, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { apiUrl } from './api';

export type AiMode = 'OFF' | 'MOCK' | 'LIVE';

export interface AiStatus {
  enabled: boolean;
  mode: AiMode;
  provider: 'ANTHROPIC' | 'BEDROCK' | 'FOUNDRY';
  model: string;
  configured: boolean;
  digestEnabled: boolean;
  userDailyLimit: number;
  label: string;
}

/** A stored AI answer with its provenance and feedback counts. */
export interface StoredInsight<T> {
  id: number;
  feature: string;
  subject: string;
  content: T;
  mode: AiMode;
  model: string;
  masking: string | null;
  createdAt: string;
  createdBy: string;
  helpful: number;
  notHelpful: number;
  myVote: number | null;
}

export type FailureCategory =
  | 'COMPILE' | 'TEST' | 'DEPENDENCY' | 'QUALITY_GATE' | 'DEPLOYMENT' | 'INFRASTRUCTURE' | 'TIMEOUT' | 'CONFIGURATION' | 'UNKNOWN';

export interface BuildDiagnosis {
  category: FailureCategory;
  summary: string;
  likelyCause: string;
  evidence: string[];
  fixSteps: string[];
  owner: 'DEVELOPER' | 'DEVOPS' | 'MIDDLEWARE' | 'QA';
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  notACodeProblem: boolean;
}

export interface BuildExplanation {
  result: StoredInsight<BuildDiagnosis>;
  job: string;
  buildNumber: number;
  logBytes: number;
  logTruncated: boolean;
  similar: { buildNumber: number; summary: string; explainedAt: string }[];
}

export type Rag = 'GREEN' | 'AMBER' | 'RED';

export interface ExecutiveDigest {
  headline: string;
  overall: Rag;
  summary: string;
  improvements: string[];
  risks: { title: string; area: string; severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'; detail: string }[];
  actions: { action: string; owner: string; area: string }[];
  areas: { area: string; status: Rag; note: string }[];
}

export const CATEGORY_LABEL: Record<FailureCategory, string> = {
  COMPILE: 'Compile error',
  TEST: 'Test failure',
  DEPENDENCY: 'Dependency / Artifactory',
  QUALITY_GATE: 'Quality gate',
  DEPLOYMENT: 'Deployment',
  INFRASTRUCTURE: 'Build agent / infrastructure',
  TIMEOUT: 'Timeout',
  CONFIGURATION: 'Configuration',
  UNKNOWN: 'Unclear',
};

export const OWNER_LABEL: Record<BuildDiagnosis['owner'], string> = {
  DEVELOPER: 'Developers',
  DEVOPS: 'DevOps',
  MIDDLEWARE: 'Middleware',
  QA: 'QA',
};

/** Whether AI features are on (and how), loaded once per session. */
@Injectable({ providedIn: 'root' })
export class Ai {
  readonly status = httpResource<AiStatus>(() => apiUrl('ai/status'));
  readonly enabled = computed(() => !!this.status.value()?.enabled);
  readonly mode = computed<AiMode>(() => this.status.value()?.mode ?? 'OFF');
}

/** "Claude claude-opus-5-5" or "Sample answer" for display. */
export function modelLabel(r: { mode: AiMode; model: string }): string {
  return r.mode === 'LIVE' ? `Claude · ${r.model}` : 'Sample answer (mock mode)';
}
