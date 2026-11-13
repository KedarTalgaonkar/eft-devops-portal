export interface ArtifactRepository {
  key: string;
  type: string;
  packageType: string;
  description: string;
  artifactCount: number;
  sizeBytes: number;
  lastUpdated: string | null;
  url: string;
}

export interface ArtifactInfo {
  repository: string;
  path: string;
  name: string;
  version: string;
  sizeBytes: number;
  createdAt: string;
  createdBy: string;
  downloads: number;
  sha256: string;
}
