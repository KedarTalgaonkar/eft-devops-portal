import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { Auth } from '../../core/auth';
import { Environments } from '../../core/environments';
import { EnvFilter } from '../../shared/env-filter';
import { LoadState } from '../../shared/load-state';
import { DeploymentView, ImportResult } from './inventory.models';

/** Support view: every deployment path with versions, setup status, Jenkins job and repositories; Excel import. */
@Component({
  selector: 'app-inventory-deployments',
  imports: [MatIconModule, MatTooltipModule, EnvFilter, LoadState],
  templateUrl: './inventory-deployments.html',
  styles: `
    .inline { display: inline-flex; align-items: center; gap: 4px; }
    .inline input { border: 1px solid var(--app-line-strong); border-radius: 8px; padding: 6px 8px; min-width: 220px; font: inherit; }
    .icon-btn { background: none; border: 0; padding: 4px; cursor: pointer; color: var(--app-muted); display: inline-flex; border-radius: 6px; }
    .icon-btn:hover { background: var(--neutral-soft); color: var(--eu-navy-600); }
    .icon-btn mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .result { display: flex; flex-direction: column; gap: 6px; }
    .result ul { margin: 0; padding-left: 18px; }
    .tag { display: inline-block; padding: 1px 8px; border-radius: 6px; background: var(--neutral-soft); font-size: 12px; margin-right: 4px; }
  `,
})
export class InventoryDeploymentsPage {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly envs = inject(Environments);
  /** Editors and administrators may import and edit; the backend enforces the same rule. */
  protected readonly canEdit = inject(Auth).can('EDITOR');

  readonly q = input<string>();
  protected readonly search = linkedSignal(() => this.q() ?? '');
  protected readonly env = signal('');
  protected readonly onlyPending = signal(false);
  protected readonly uploading = signal(false);
  protected readonly lastImport = signal<ImportResult | null>(null);
  protected readonly editingJob = signal<number | null>(null);
  protected readonly editingRepos = signal<number | null>(null);

  protected readonly deployments = httpResource<DeploymentView[]>(() => apiUrl('inventory/deployments', { env: this.env() }), {
    defaultValue: [],
  });

  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.deployments
      .value()
      .filter((d) => !this.onlyPending() || !d.tomcatAdminDone || !d.buildXmlDone)
      .filter(
        (d) =>
          !q ||
          [d.application, d.client, d.owner ?? '', d.contextName, d.baseUrl ?? '', d.effectiveJenkinsJob, ...d.repositories].some((v) =>
            v.toLowerCase().includes(q),
          ),
      );
  });

  protected upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const form = new FormData();
    form.append('file', file);
    this.uploading.set(true);
    this.http.post<ImportResult>(apiUrl('inventory/import'), form).subscribe({
      next: (r) => {
        this.uploading.set(false);
        this.lastImport.set(r);
        this.snackBar.open(`Imported ${r.rowsRead} deployment paths (${r.deploymentsCreated} new, ${r.deploymentsUpdated} updated)`, 'OK', {
          duration: 6000,
        });
        this.deployments.reload();
        if (r.newEnvironments.length) {
          this.envs.reload();
        }
      },
      error: () => this.uploading.set(false),
    });
  }

  protected saveJob(row: DeploymentView, value: string): void {
    this.http.put(apiUrl(`inventory/deployments/${row.id}`), { jenkinsJob: value }).subscribe(() => {
      this.editingJob.set(null);
      this.deployments.reload();
    });
  }

  protected saveRepos(row: DeploymentView, value: string): void {
    const repositories = value.split(/[,\n;]/).map((s) => s.trim()).filter(Boolean);
    this.http.put(apiUrl(`inventory/applications/${row.applicationId}/repositories`), { repositories }).subscribe(() => {
      this.editingRepos.set(null);
      this.deployments.reload();
    });
  }
}
