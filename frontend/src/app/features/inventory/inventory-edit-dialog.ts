import { Component, computed, inject, Signal, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { apiUrl } from '../../core/api';
import { Environments } from '../../core/environments';
import { ApplicationSummary, DeploymentView } from './inventory.models';

/** What the dialog edits. A new path either picks an existing application or names a new one. */
export type InventoryEditData =
  | { kind: 'application'; app?: { id: number; name: string; client: string; owner: string | null; repositories: string[] } }
  | { kind: 'path'; path?: DeploymentView; applicationId?: number; applications: Signal<ApplicationSummary[]> };

/** Result: what was saved, so the page can reload and say so. */
export interface InventoryEditResult {
  message: string;
  /** Set when an application was created or edited. */
  applicationId?: number;
}

/** Add or edit an application, or add or edit a deployment path. Editors only; the API enforces the same rule. */
@Component({
  selector: 'app-inventory-edit-dialog',
  imports: [MatDialogModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ title() }}</h2>
    <form (submit)="$event.preventDefault(); save()" class="form" novalidate>
      <mat-dialog-content>
        @if (data.kind === 'application') {
          <div class="grid">
            <label><span>Application name <span class="req" aria-hidden="true">*</span></span>
              <input class="field" [value]="name()" (input)="name.set(val($event))" maxlength="200" required
                     placeholder="e.g. IMPS Portal" autocomplete="off" />
            </label>
            <label>Client
              <input class="field" [value]="client()" (input)="client.set(val($event))" maxlength="200"
                     placeholder="e.g. COSMOS BANK (blank if shared)" autocomplete="off" />
            </label>
            <label>Owner
              <input class="field" [value]="owner()" (input)="owner.set(val($event))" maxlength="100"
                     placeholder="Team or person" autocomplete="off" />
            </label>
            <label class="wide">Repositories
              <input class="field mono" [value]="repos()" (input)="repos.set(val($event))"
                     placeholder="org/repo, org/other" autocomplete="off" />
              <span class="hint">Comma-separated GitHub repositories.</span>
            </label>
          </div>
          @if (data.app) {
            <p class="hint">Renaming changes the default Jenkins job name ({{ '{' }}app{{ '}' }}-ENV) for paths without their own job.</p>
          }
        } @else {
          <div class="grid">
            @if (!data.path) {
              <label class="wide"><span>Application <span class="req" aria-hidden="true">*</span></span>
                <select class="field" [value]="appId() ?? ''" (change)="appId.set(num($event))" required>
                  <option value="" disabled>Choose an application…</option>
                  @for (a of sortedApps(); track a.id) {
                    <option [value]="a.id" [selected]="a.id === appId()">{{ a.name }}{{ a.client ? ' · ' + a.client : '' }}</option>
                  }
                </select>
                <span class="hint">Not listed? Close this and use <strong>Add application</strong> first.</span>
              </label>
            } @else {
              <p class="wide app-line"><strong>{{ data.path.application }}</strong>{{ data.path.client ? ' · ' + data.path.client : '' }}</p>
            }
            <label><span>Environment <span class="req" aria-hidden="true">*</span></span>
              <select class="field" (change)="environment.set(val($event))" required>
                @for (e of envs.all.value(); track e.code) {
                  <option [value]="e.code" [selected]="e.code === environment()">{{ e.name }}{{ e.active ? '' : ' (hidden)' }}</option>
                }
              </select>
            </label>
            <label>Platform
              <input class="field" [value]="platform()" (input)="platform.set(val($event))" maxlength="30"
                     list="platforms" placeholder="TOMCAT" autocomplete="off" />
              <datalist id="platforms"><option value="TOMCAT"></option><option value="OPENSHIFT"></option><option value="KUBERNETES"></option><option value="VM"></option></datalist>
            </label>
            <label>Context
              <input class="field mono" [value]="contextName()" (input)="contextName.set(val($event))" maxlength="200"
                     placeholder="e.g. SITCBCQRAPI (blank = root)" autocomplete="off" />
            </label>
            <label>Base URL
              <input class="field mono" [value]="baseUrl()" (input)="baseUrl.set(val($event))" maxlength="500"
                     placeholder="http://host:port" autocomplete="off" inputmode="url" />
              @if (urlProblem()) { <span class="err">{{ urlProblem() }}</span> }
            </label>
            <label>Tomcat version
              <input class="field mono" [value]="tomcatVersion()" (input)="tomcatVersion.set(val($event))" maxlength="50"
                     placeholder="e.g. 9.0.85" autocomplete="off" />
            </label>
            <label>Java version
              <input class="field mono" [value]="javaVersion()" (input)="javaVersion.set(val($event))" maxlength="50"
                     placeholder="e.g. 17 or 8u321" autocomplete="off" />
            </label>
            <label class="wide">Jenkins job
              <input class="field mono" [value]="jenkinsJob()" (input)="jenkinsJob.set(val($event))" maxlength="300"
                     placeholder="Blank = naming pattern ({app}-ENV)" autocomplete="off" />
            </label>
            <label class="check"><input type="checkbox" [checked]="tomcatAdminDone()" (change)="tomcatAdminDone.set(checked($event))" /> Tomcat admin set up</label>
            <label class="check"><input type="checkbox" [checked]="buildXmlDone()" (change)="buildXmlDone.set(checked($event))" /> build.xml done</label>
          </div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button class="btn" type="button" mat-dialog-close>Cancel</button>
        <button class="btn primary" type="submit" [disabled]="!valid() || saving()">
          <mat-icon aria-hidden="true">save</mat-icon>{{ saving() ? 'Saving…' : 'Save' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; padding-top: 4px; }
    @media (max-width: 640px) { .grid { grid-template-columns: 1fr; } }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; color: var(--app-text); }
    .wide { grid-column: 1 / -1; }
    .field { font: inherit; font-weight: 400; border: 1px solid var(--app-line-strong); border-radius: 8px; padding: 8px 10px;
      background: var(--app-surface, #fff); color: var(--app-text); }
    .field:focus { outline: 2px solid var(--eu-teal, #00a3a1); outline-offset: 1px; }
    .check { flex-direction: row; align-items: center; gap: 8px; font-weight: 500; }
    .hint { font-size: 12px; font-weight: 400; color: var(--app-muted); margin: 0; }
    .err { font-size: 12px; font-weight: 500; color: var(--bad); }
    .req { color: var(--bad); }
    .app-line { margin: 0; font-size: 14px; }
    .mono { font-family: var(--font-mono); }
  `,
})
export class InventoryEditDialog {
  protected readonly data = inject<InventoryEditData>(MAT_DIALOG_DATA);
  protected readonly envs = inject(Environments);
  private readonly ref = inject(MatDialogRef<InventoryEditDialog, InventoryEditResult>);
  private readonly http = inject(HttpClient);
  protected readonly saving = signal(false);

  // application fields
  private readonly app = this.data.kind === 'application' ? this.data.app : undefined;
  protected readonly name = signal(this.app?.name ?? '');
  protected readonly client = signal(this.app?.client ?? '');
  protected readonly owner = signal(this.app?.owner ?? '');
  protected readonly repos = signal(this.app?.repositories.join(', ') ?? '');

  // path fields
  private readonly path = this.data.kind === 'path' ? this.data.path : undefined;
  protected readonly appId = signal<number | null>(
    this.data.kind === 'path' ? (this.data.path?.applicationId ?? this.data.applicationId ?? null) : null,
  );
  protected readonly environment = signal(this.path?.environment ?? this.envs.active()[0]?.code ?? 'SIT');
  protected readonly platform = signal(this.path?.platform ?? 'TOMCAT');
  protected readonly contextName = signal(this.path?.contextName ?? '');
  protected readonly baseUrl = signal(this.path?.baseUrl ?? '');
  protected readonly tomcatVersion = signal(this.path?.tomcatVersion ?? '');
  protected readonly javaVersion = signal(this.path?.javaVersion ?? '');
  protected readonly jenkinsJob = signal(this.path?.jenkinsJob ?? '');
  protected readonly tomcatAdminDone = signal(this.path?.tomcatAdminDone ?? false);
  protected readonly buildXmlDone = signal(this.path?.buildXmlDone ?? false);

  protected readonly sortedApps = computed(() =>
    this.data.kind === 'path'
      ? [...this.data.applications()].sort((a, b) => (a.name + a.client).localeCompare(b.name + b.client))
      : [],
  );

  protected readonly title = computed(() => {
    if (this.data.kind === 'application') {
      return this.app ? `Edit ${this.app.name}` : 'Add application';
    }
    return this.path ? `Edit ${this.path.environment} path` : 'Add deployment path';
  });

  protected readonly urlProblem = computed(() => {
    const u = this.baseUrl().trim();
    return !u || /^https?:\/\/[^\s/?#]+(\/[^\s?#]*)?$/i.test(u) ? '' : 'Use http://host:port or https://host';
  });

  protected readonly valid = computed(() =>
    this.data.kind === 'application'
      ? this.name().trim().length > 0
      : this.appId() != null && !!this.environment() && !this.urlProblem(),
  );

  protected save(): void {
    if (!this.valid() || this.saving()) {
      return;
    }
    this.saving.set(true);
    const done = (message: string, applicationId?: number) => {
      this.saving.set(false);
      this.ref.close({ message, applicationId });
    };
    const failed = () => this.saving.set(false); // the error interceptor shows the reason

    if (this.data.kind === 'application') {
      const body = {
        name: this.name().trim(),
        client: this.client().trim(),
        owner: this.owner().trim() || null,
        repositories: this.repos().split(/[,\n;]/).map((s) => s.trim()).filter(Boolean),
      };
      const call = this.app
        ? this.http.put<{ id: number }>(apiUrl(`inventory/applications/${this.app.id}`), body)
        : this.http.post<{ id: number }>(apiUrl('inventory/applications'), body);
      call.subscribe({ next: (a) => done(`${body.name} ${this.app ? 'updated' : 'added'}`, a.id), error: failed });
      return;
    }

    const body = {
      environment: this.environment(),
      contextName: this.contextName().trim(),
      platform: this.platform().trim() || 'TOMCAT',
      baseUrl: this.baseUrl().trim() || null,
      tomcatVersion: this.tomcatVersion().trim() || null,
      javaVersion: this.javaVersion().trim() || null,
      tomcatAdminDone: this.tomcatAdminDone(),
      buildXmlDone: this.buildXmlDone(),
      jenkinsJob: this.jenkinsJob().trim() || null,
    };
    const call = this.path
      ? this.http.put(apiUrl(`inventory/deployments/${this.path.id}`), body)
      : this.http.post(apiUrl(`inventory/applications/${this.appId()}/deployments`), body);
    call.subscribe({
      next: () => done(`${body.environment} path ${this.path ? 'updated' : 'added'}`),
      error: failed,
    });
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  protected num(e: Event): number | null {
    const v = (e.target as HTMLSelectElement).value;
    return v ? Number(v) : null;
  }

  protected checked(e: Event): boolean {
    return (e.target as HTMLInputElement).checked;
  }
}
