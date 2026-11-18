import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { Auth } from '../../core/auth';
import { Environments } from '../../core/environments';
import { EnvFilter } from '../../shared/env-filter';
import { LoadState } from '../../shared/load-state';
import { ApplicationSummary, DeploymentView, ImportResult, javaLine, tomcatLine } from './inventory.models';
import { InventoryEditData, InventoryEditDialog, InventoryEditResult } from './inventory-edit-dialog';

/** Support view: every deployment path with versions, setup status, Jenkins job and repositories; Excel import. */
@Component({
  selector: 'app-inventory-deployments',
  imports: [MatIconModule, MatTooltipModule, EnvFilter, LoadState],
  templateUrl: './inventory-deployments.html',
  styles: `
    .actions {
      white-space: nowrap;
    }
    .icon-btn.danger:hover {
      background: var(--bad-soft, #fdecec);
      color: var(--bad);
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
    .inline {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .inline input {
      border: 1px solid var(--app-line-strong);
      border-radius: 8px;
      padding: 6px 8px;
      min-width: 220px;
      font: inherit;
    }
    .icon-btn {
      background: none;
      border: 0;
      padding: 4px;
      cursor: pointer;
      color: var(--app-muted);
      display: inline-flex;
      border-radius: 6px;
    }
    .icon-btn:hover {
      background: var(--neutral-soft);
      color: var(--eu-navy-600);
    }
    .icon-btn mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .result {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .result ul {
      margin: 0;
      padding-left: 18px;
    }
    .tag {
      display: inline-block;
      padding: 1px 8px;
      border-radius: 6px;
      background: var(--neutral-soft);
      font-size: 12px;
      margin-right: 4px;
    }
  `,
})
export class InventoryDeploymentsPage {
  readonly q = input<string>();
  /** ?env=SIT&java=Java 8&tomcat=Tomcat 9&pending=1 deep links from the overview charts. */
  readonly envParam = input<string | undefined>(undefined, { alias: 'env' });
  readonly java = input<string>();
  readonly tomcat = input<string>();
  readonly pending = input<string>();
  protected readonly envs = inject(Environments);
  /** Editors and administrators may import and edit; the backend enforces the same rule. */
  protected readonly canEdit = inject(Auth).canEdit('INVENTORY');
  protected readonly search = linkedSignal(() => this.q() ?? '');
  protected readonly env = linkedSignal(() => this.envParam() ?? '');
  protected readonly javaFilter = linkedSignal(() => this.java() ?? '');
  protected readonly tomcatFilter = linkedSignal(() => this.tomcat() ?? '');
  protected readonly onlyPending = linkedSignal(() => !!this.pending() && this.pending() !== '0');
  protected readonly uploading = signal(false);
  protected readonly lastImport = signal<ImportResult | null>(null);
  protected readonly editingJob = signal<number | null>(null);
  protected readonly editingRepos = signal<number | null>(null);
  protected readonly deployments = httpResource<DeploymentView[]>(
    () => apiUrl('inventory/deployments', { env: this.env() }),
    {
      defaultValue: [],
    },
  );
  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.deployments
      .value()
      .filter((d) => !this.onlyPending() || !d.tomcatAdminDone || !d.buildXmlDone)
      .filter((d) => !this.javaFilter() || javaLine(d.javaVersion) === this.javaFilter())
      .filter((d) => !this.tomcatFilter() || tomcatLine(d.tomcatVersion) === this.tomcatFilter())
      .filter(
        (d) =>
          !q ||
          [
            d.application,
            d.client,
            d.owner ?? '',
            d.contextName,
            d.baseUrl ?? '',
            d.effectiveJenkinsJob,
            ...d.repositories,
          ].some((v) => v.toLowerCase().includes(q)),
      );
  });
  /** Applications for the "add path" picker (loaded only for editors). */
  protected readonly applications = httpResource<ApplicationSummary[]>(
    () => (this.canEdit ? apiUrl('inventory/applications') : undefined),
    { defaultValue: [] },
  );
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  protected addApplication(): void {
    this.openEditor({ kind: 'application' }, (r) => {
      // offer the obvious next step: give the new application its first path
      const ref = this.snackBar.open(r.message, 'Add a path', { duration: 8000 });
      ref.onAction().subscribe(() => this.addPath(r.applicationId));
    });
  }

  protected editApplication(d: DeploymentView): void {
    this.openEditor({
      kind: 'application',
      app: { id: d.applicationId, name: d.application, client: d.client, owner: d.owner, repositories: d.repositories },
    });
  }

  protected addPath(applicationId?: number): void {
    this.openEditor({ kind: 'path', applicationId, applications: this.applications.value });
  }

  protected editPath(d: DeploymentView): void {
    this.openEditor({ kind: 'path', path: d, applications: this.applications.value });
  }

  protected removePath(d: DeploymentView): void {
    const what = `${d.application}${d.client ? ' · ' + d.client : ''} – ${d.environment} ${d.contextName || '(root)'}`;
    if (!window.confirm(`Remove the deployment path ${what}?\n\nIt stops being probed and tracked. The change log keeps a record.`)) {
      return;
    }
    this.http.delete(apiUrl(`inventory/deployments/${d.id}`)).subscribe(() => {
      this.snackBar.open(`Removed ${what}`, 'OK', { duration: 4000 });
      this.reloadAll();
    });
  }

  private openEditor(data: InventoryEditData, after?: (r: InventoryEditResult) => void): void {
    this.dialog
      .open<InventoryEditDialog, InventoryEditData, InventoryEditResult>(InventoryEditDialog, {
        data,
        width: '640px',
        maxWidth: '95vw',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((r) => {
        if (r) {
          this.reloadAll();
          if (after) {
            after(r);
          } else {
            this.snackBar.open(r.message, 'OK', { duration: 4000 });
          }
        }
      });
  }

  private reloadAll(): void {
    this.deployments.reload();
    this.applications.reload();
  }

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
        this.snackBar.open(
          `Imported ${r.rowsRead} deployment paths (${r.deploymentsCreated} new, ${r.deploymentsUpdated} updated)`,
          'OK',
          {
            duration: 6000,
          },
        );
        this.deployments.reload();
        if (r.newEnvironments.length) {
          this.envs.reload();
        }
      },
      error: () => this.uploading.set(false),
    });
  }

  protected saveJob(row: DeploymentView, value: string): void {
    this.http
      .put(apiUrl(`inventory/deployments/${row.id}/jenkins-job`), { jenkinsJob: value })
      .subscribe(() => {
        this.editingJob.set(null);
        this.deployments.reload();
      });
  }

  protected saveRepos(row: DeploymentView, value: string): void {
    const repositories = value
      .split(/[,\n;]/)
      .map((s) => s.trim())
      .filter(Boolean);
    this.http
      .put(apiUrl(`inventory/applications/${row.applicationId}/repositories`), { repositories })
      .subscribe(() => {
        this.editingRepos.set(null);
        this.deployments.reload();
      });
  }
}
