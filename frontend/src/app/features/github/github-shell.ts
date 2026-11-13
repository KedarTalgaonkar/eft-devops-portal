import { Component } from '@angular/core';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-github-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell title="Source code" subtitle="Team-wise repositories, commit activity and pull requests"
                      [logos]="['github']" [tabs]="tabs" />
  `,
})
export class GithubShell {
  protected readonly tabs: ModuleTab[] = [
    { path: 'overview', label: 'Overview', icon: 'insights', audience: 'overview' },
    { path: 'repositories', label: 'Repositories', icon: 'folder_code' },
    { path: 'pulls', label: 'Pull requests', icon: 'merge' },
  ];
}
