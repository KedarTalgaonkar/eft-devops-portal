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
