import { filterAndSort, totals } from './pipeline-table';
import { PipelineMetric } from './pipelines.models';

function row(project: string, environment: string, total: number, success: number, failed: number, aborted: number): PipelineMetric {
  return {
    applicationId: 1, project, client: 'C', owner: 'O', environment, jenkinsJob: `${project}-${environment}`,
    totalBuilds: total, success, failed, aborted, unstable: 0, inProgress: 0,
    successRate: success + failed + aborted ? (success * 100) / (success + failed + aborted) : null,
    lastBuild: null, error: null,
  };
}

describe('pipeline table helpers', () => {
  const order = ['DEVELOP', 'SIT', 'UAT', 'PERF'];
  const rows = [
    row('Recon', 'SIT', 10, 5, 4, 1),
    row('Prepaid QR', 'UAT', 4, 4, 0, 0),
    row('Prepaid QR', 'PERF', 2, 2, 0, 0),
    row('Prepaid QR', 'DEVELOP', 20, 15, 3, 2),
  ];

  it('sorts by project then environment in catalogue order (new environments included)', () => {
    const sorted = filterAndSort(rows, '', { key: 'project', dir: 1 }, order);
    expect(sorted.map((r) => `${r.project}/${r.environment}`)).toEqual([
      'Prepaid QR/DEVELOP', 'Prepaid QR/UAT', 'Prepaid QR/PERF', 'Recon/SIT',
    ]);
  });

  it('puts environments missing from the catalogue last', () => {
    const sorted = filterAndSort([row('A', 'DR', 1, 1, 0, 0), row('A', 'SIT', 1, 1, 0, 0)], '', { key: 'environment', dir: 1 }, order);
    expect(sorted.map((r) => r.environment)).toEqual(['SIT', 'DR']);
  });

  it('sorts numerically descending', () => {
    expect(filterAndSort(rows, '', { key: 'failed', dir: -1 }, order)[0].project).toBe('Recon');
  });

  it('filters case-insensitively on project, client, owner and job', () => {
    expect(filterAndSort(rows, 'recon', { key: 'project', dir: 1 })).toHaveLength(1);
    expect(filterAndSort(rows, 'qr-uat', { key: 'project', dir: 1 })).toHaveLength(1);
  });

  it('adds up totals and the overall success rate', () => {
    expect(totals(rows)).toEqual({ totalBuilds: 36, success: 26, failed: 7, aborted: 3, unstable: 0, successRate: 72.2 });
    expect(totals([]).successRate).toBeNull();
  });
});
