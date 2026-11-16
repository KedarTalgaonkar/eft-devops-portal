import { Component } from '@angular/core';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-artifacts-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell
      heading="Artifacts"
      subtitle="Artifactory repositories, storage and published builds"
      [logos]="['jfrog', 'docker']"
      [tabs]="tabs"
    />
  `,
})
export class ArtifactsShell {
  protected readonly tabs: ModuleTab[] = [
    { path: 'overview', label: 'Overview', icon: 'insights', audience: 'overview' },
    { path: 'browse', label: 'Browse', icon: 'folder_open' },
  ];
}
