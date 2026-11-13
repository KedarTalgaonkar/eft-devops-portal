export interface GrafanaDashboard {
  uid: string;
  title: string;
  folder: string;
  tags: string[];
  url: string;
}

export interface ScrapeTarget {
  job: string;
  instance: string;
  health: string;
  lastScrape: string;
  scrapeDurationSeconds: number;
  lastError: string | null;
}

export interface AlertInfo {
  name: string;
  severity: string;
  state: string;
  summary: string;
  source: string;
  startsAt: string;
  labels: Record<string, string>;
}

export interface MetricSample {
  name: string;
  instance: string;
  value: number;
  unit: string;
}

export interface HostRow {
  host: string;
  cpu: number;
  memory: number;
  disk: number;
  worst: number;
}

/** Pivot flat samples into one row per host, worst first. */
export function toHostRows(samples: MetricSample[]): HostRow[] {
  const rows = new Map<string, HostRow>();
  for (const m of samples) {
    const row = rows.get(m.instance) ?? { host: m.instance, cpu: 0, memory: 0, disk: 0, worst: 0 };
    if (m.name === 'cpu' || m.name === 'memory' || m.name === 'disk') {
      row[m.name] = m.value;
    }
    row.worst = Math.max(row.cpu, row.memory, row.disk);
    rows.set(m.instance, row);
  }
  return [...rows.values()].sort((a, b) => b.worst - a.worst);
}
