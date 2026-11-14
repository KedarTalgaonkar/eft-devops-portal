import { Component } from '@angular/core';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-sbom-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell heading="SBOM & vulnerabilities"
                      subtitle="Software bills of materials (CycloneDX) and the known vulnerabilities in them"
                      [logos]="['java']" [tabs]="tabs" />
  `,
})
export class SbomShell {
  protected readonly tabs: ModuleTab[] = [
    { path: 'overview', label: 'Overview', icon: 'insights', audience: 'overview' },
    { path: 'scans', label: 'Scans & findings', icon: 'policy' },
  ];
}
