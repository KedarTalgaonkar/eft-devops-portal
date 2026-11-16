import { Component } from '@angular/core';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-admin-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell
      heading="Administration"
      subtitle="Local accounts, the security audit trail and AI usage (administrators only)"
      [tabs]="tabs"
    />
  `,
})
export class AdminShell {
  protected readonly tabs: ModuleTab[] = [
    { path: 'users', label: 'Local accounts', icon: 'manage_accounts' },
    { path: 'audit', label: 'Audit log', icon: 'policy' },
    { path: 'ai', label: 'AI usage', icon: 'auto_awesome' },
  ];
}
