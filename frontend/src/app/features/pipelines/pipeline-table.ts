import { PipelineMetric } from './pipelines.models';

export type SortKey = 'project' | 'environment' | 'totalBuilds' | 'success' | 'failed' | 'aborted' | 'successRate';

export interface SortState {
  key: SortKey;
  dir: 1 | -1;
}

/**
 * Filters by free text (project, client, owner, job) and sorts. Environments sort in catalogue order
 * ({@code envOrder}); unknown ones go last. Kept pure so it is easy to unit test.
 */
export function filterAndSort(rows: PipelineMetric[], search: string, sort: SortState, envOrder: string[] = []): PipelineMetric[] {
  const q = search.trim().toLowerCase();
  const filtered = q
    ? rows.filter((r) => [r.project, r.client, r.owner ?? '', r.jenkinsJob].some((v) => v.toLowerCase().includes(q)))
    : rows;
  const rank = (env: string) => {
    const i = envOrder.indexOf(env);
    return i < 0 ? envOrder.length : i;
  };
  return [...filtered].sort((a, b) => sort.dir * compare(a, b, sort.key, rank));
}

function compare(a: PipelineMetric, b: PipelineMetric, key: SortKey, rank: (env: string) => number): number {
  switch (key) {
    case 'project':
      return a.project.localeCompare(b.project) || a.client.localeCompare(b.client) || rank(a.environment) - rank(b.environment);
    case 'environment':
      return rank(a.environment) - rank(b.environment) || a.environment.localeCompare(b.environment) || a.project.localeCompare(b.project);
    case 'successRate':
      return (a.successRate ?? -1) - (b.successRate ?? -1);
    default:
      return a[key] - b[key];
  }
}

export interface Totals {
  totalBuilds: number;
  success: number;
  failed: number;
  aborted: number;
  unstable: number;
  successRate: number | null;
}

export function totals(rows: PipelineMetric[]): Totals {
  const t = rows.reduce(
    (acc, r) => ({
      totalBuilds: acc.totalBuilds + r.totalBuilds,
      success: acc.success + r.success,
      failed: acc.failed + r.failed,
      aborted: acc.aborted + r.aborted,
      unstable: acc.unstable + r.unstable,
    }),
    { totalBuilds: 0, success: 0, failed: 0, aborted: 0, unstable: 0 },
  );
  const finished = t.success + t.failed + t.aborted + t.unstable;
  return {
    totalBuilds: t.totalBuilds,
    success: t.success,
    failed: t.failed,
    aborted: t.aborted,
    unstable: t.unstable,
    successRate: finished ? Math.round((t.success * 1000) / finished) / 10 : null,
  };
}
