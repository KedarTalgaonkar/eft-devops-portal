export interface TeamSummary {
  slug: string;
  name: string;
  members: number;
  repositoryCount: number;
  openPullRequests: number;
}

export interface RepositoryInfo {
  name: string;
  fullName: string;
  team: string;
  defaultBranch: string;
  language: string;
  visibility: string;
  archived: boolean;
  openPullRequests: number;
  commitsLast30Days: number;
  lastPushAt: string;
  url: string;
}

export interface CommitActivity {
  repository: string;
  days: number;
  totalCommits: number;
  perDay: { date: string; commits: number }[];
  topAuthors: { author: string; commits: number }[];
}

export interface PullRequestInfo {
  repository: string;
  number: number;
  title: string;
  author: string;
  state: string;
  draft: boolean;
  reviewers: number;
  createdAt: string;
  updatedAt: string;
  url: string;
}

const DAY_MS = 86_400_000;

export type AgeBucket = '0-2' | '2-7' | '7-14' | '14+' | '7+';

/** Open-PR age buckets, shared by the overview chart and the pull request filter (?age=). */
export const AGE_BUCKETS: { key: AgeBucket; label: string; color: string }[] = [
  { key: '0-2', label: 'Under 2 days', color: 'var(--c-success)' },
  { key: '2-7', label: '2–7 days', color: 'var(--eu-teal)' },
  { key: '7-14', label: '7–14 days', color: 'var(--c-aborted)' },
  { key: '14+', label: 'Over 14 days', color: 'var(--c-failed)' },
];

export function ageDays(p: PullRequestInfo): number {
  return (Date.now() - new Date(p.createdAt).getTime()) / DAY_MS;
}

export function inBucket(days: number, bucket: AgeBucket): boolean {
  switch (bucket) {
    case '0-2': return days < 2;
    case '2-7': return days >= 2 && days < 7;
    case '7-14': return days >= 7 && days < 14;
    case '14+': return days >= 14;
    case '7+': return days >= 7;
  }
}
