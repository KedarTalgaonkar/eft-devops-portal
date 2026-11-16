/** Types shared by several features. They mirror the backend records. */

/** Any environment code from /api/environments (DEVELOP, SIT, UAT, PERF, PROD, ...). */
export type Environment = string;

export type BuildResult =
  'SUCCESS' | 'FAILURE' | 'ABORTED' | 'UNSTABLE' | 'NOT_BUILT' | 'IN_PROGRESS';

export type HealthState = 'UP' | 'DOWN' | 'UNKNOWN';

export type IntegrationMode = 'MOCK' | 'LIVE';

export type Rag = 'GREEN' | 'AMBER' | 'RED';

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface IntegrationStatus {
  key: string;
  name: string;
  mode: IntegrationMode;
  reachable: boolean;
  endpoint: string | null;
  message: string | null;
}

export interface Kpi {
  current: number | null;
  previous: number | null;
}

/** "3 h ago", "2 d ago" for support views. */
export function ago(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) {
    return '—';
  }
  const mins = Math.round((now - new Date(iso).getTime()) / 60000);
  if (mins < 1) {
    return 'just now';
  }
  if (mins < 60) {
    return `${mins} min ago`;
  }
  const hours = Math.round(mins / 60);
  if (hours < 48) {
    return `${hours} h ago`;
  }
  return `${Math.round(hours / 24)} d ago`;
}
