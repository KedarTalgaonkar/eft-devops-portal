import { Component } from '@angular/core';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-inventory-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell title="Application inventory"
                      subtitle="Applications, clients, owners and every deployment path, from the Tomcat projects workbook"
                      [logos]="['tomcat', 'java']" [tabs]="tabs" />
  `,
})
export class InventoryShell {
  protected readonly tabs: ModuleTab[] = [
    { path: 'overview', label: 'Overview', icon: 'insights', audience: 'overview' },
    { path: 'deployments', label: 'Deployment paths', icon: 'dns' },
    { path: 'changes', label: 'Change log', icon: 'history' },
    { path: 'environments', label: 'Environments', icon: 'tune' },
  ];
}
