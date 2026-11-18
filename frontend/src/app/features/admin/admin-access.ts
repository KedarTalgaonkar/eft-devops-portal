import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { AccessLevel, Auth, PortalModule, PortalRole } from '../../core/auth';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';

interface ModuleView {
  key: PortalModule;
  label: string;
  description: string;
}

interface GroupView {
  id: number;
  name: string;
  description: string | null;
  builtin: boolean;
  modules: Record<PortalModule, AccessLevel>;
  ssoGroups: string[];
  members: string[];
  updatedAt: string;
  updatedBy: string | null;
}

interface Preview {
  modules: Record<PortalModule, AccessLevel>;
  groups: string[];
}

const LEVELS: { value: AccessLevel; label: string }[] = [
  { value: 'NONE', label: 'Hidden' },
  { value: 'VIEW', label: 'View' },
  { value: 'EDIT', label: 'Edit' },
];

const split = (s: string) =>
  s
    .split(/[,\n;]/)
    .map((x) => x.trim())
    .filter(Boolean);

/**
 * Administration → Access groups: which groups see or edit which modules. The matrix saves as you change it;
 * the backend enforces the result on every API call.
 */
@Component({
  selector: 'app-admin-access',
  imports: [CardGroup, DatePipe, LoadState, MatIconModule, MatTooltipModule],
  template: `
    <app-card-group heading="How access works" icon="info"
                    description="Group access decides which modules people see; their role still decides the most they can do.">
      <ul class="rules">
        <li><strong>Everyone</strong> applies to every signed-in user. Narrow it, then open modules again through other groups.</li>
        <li>A user gets the <strong>best level</strong> any of their groups grants: from their SSO groups (Entra / Keycloak
          <code>groups</code> claim) or because their username is listed.</li>
        <li>The <strong>role is the ceiling</strong>: a Viewer never gets more than View. <strong>Admins</strong> always have full access.</li>
        <li>Changes apply within 30 seconds; people see the new menu the next time they load the portal.</li>
      </ul>
    </app-card-group>

    <app-load-state [loading]="groups.isLoading()" [error]="groups.error()" what="access groups" (retry)="groups.reload()" />

    @if (groups.value().length && modules.value().length) {
      <app-card-group heading="Module access by group" icon="grid_on"
                      description="Pick Hidden, View or Edit for each group and module. Each change is saved and audited straight away.">
        <div class="table-wrap">
          <table class="data matrix" aria-label="Module access by group">
            <thead>
              <tr>
                <th scope="col">Module</th>
                @for (g of groups.value(); track g.id) {
                  <th scope="col">
                    <span class="g-name">{{ g.name }}</span>
                    <span class="g-who">{{ who(g) }}</span>
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              @for (m of modules.value(); track m.key) {
                <tr>
                  <th scope="row"><span class="m-name">{{ m.label }}</span><span class="m-what">{{ m.description }}</span></th>
                  @for (g of groups.value(); track g.id) {
                    <td>
                      <select class="lvl" [class]="'lvl ' + g.modules[m.key].toLowerCase()"
                              [attr.aria-label]="m.label + ' for ' + g.name" [disabled]="busy() === g.id"
                              (change)="setLevel(g, m.key, $any($event.target).value)">
                        @for (l of levels; track l.value) {
                          <option [value]="l.value" [selected]="l.value === g.modules[m.key]">{{ l.label }}</option>
                        }
                      </select>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
      </app-card-group>
    }

    <app-card-group heading="Groups and members" icon="groups"
                    description="Link each group to SSO groups (names or Entra object IDs) and/or list usernames, e.g. break-glass local accounts.">
      <form class="card new" (submit)="$event.preventDefault(); create(n.value, d.value); n.value = ''; d.value = ''">
        <strong>New access group</strong>
        <div class="row-fields">
          <label>Name <input #n class="field" required maxlength="100" placeholder="e.g. QA team" autocomplete="off" /></label>
          <label class="grow">Description <input #d class="field" maxlength="300" placeholder="Who is in it and why" autocomplete="off" /></label>
          <button class="btn primary" type="submit"><mat-icon aria-hidden="true">add</mat-icon>Create</button>
        </div>
        <span class="hint">A new group starts with every module Hidden; then set its levels in the matrix above.</span>
      </form>

      @for (g of groups.value(); track g.id) {
        <section class="card group" [attr.aria-label]="'Access group ' + g.name">
          <div class="g-head">
            <mat-icon aria-hidden="true">{{ g.builtin ? 'public' : 'group' }}</mat-icon>
            <strong>{{ g.name }}</strong>
            @if (g.builtin) {
              <span class="badge">built in · all signed-in users</span>
            }
            <span class="spacer"></span>
            <span class="muted small">changed {{ g.updatedAt | date: 'dd MMM yyyy, HH:mm' }}{{ g.updatedBy ? ' by ' + g.updatedBy : '' }}</span>
          </div>
          <label>Description
            <input #gd class="field" [value]="g.description ?? ''" maxlength="300" />
          </label>
          @if (!g.builtin) {
            <label>Name
              <input #gn class="field name" [value]="g.name" maxlength="100" />
            </label>
            <div class="two">
              <label>SSO groups
                <textarea #gs class="field mono" rows="2" placeholder="EFT-Delivery, 0f1c…-entra-object-id">{{ g.ssoGroups.join(', ') }}</textarea>
                <span class="hint">Matched against the ID token's groups claim, case-insensitive.</span>
              </label>
              <label>Usernames
                <textarea #gm class="field mono" rows="2" placeholder="portal-admin, jenkins.breakglass">{{ g.members.join(', ') }}</textarea>
                <span class="hint">Local accounts, or individual SSO users by their sign-in name.</span>
              </label>
            </div>
            <div class="actions">
              <button class="btn danger" type="button" (click)="remove(g)"><mat-icon aria-hidden="true">delete</mat-icon>Delete group</button>
              <span class="spacer"></span>
              <button class="btn primary" type="button" [disabled]="busy() === g.id"
                      (click)="saveDetails(g, gn.value, gd.value, gs.value, gm.value)">
                <mat-icon aria-hidden="true">save</mat-icon>Save details
              </button>
            </div>
          } @else {
            <div class="actions">
              <span class="spacer"></span>
              <button class="btn primary" type="button" [disabled]="busy() === g.id" (click)="saveDetails(g, g.name, gd.value, '', '')">
                <mat-icon aria-hidden="true">save</mat-icon>Save description
              </button>
            </div>
          }
        </section>
      }
    </app-card-group>

    <app-card-group heading="Check someone's access" icon="person_search"
                    description="See what a user would get and which groups give it, before you tell them.">
      <form class="card preview" (submit)="$event.preventDefault(); check(pu.value, $any(pr).value, ps.value)">
        <div class="row-fields">
          <label>Username <input #pu class="field" maxlength="200" placeholder="e.g. priya.k" autocomplete="off" /></label>
          <label>Role
            <select #pr class="field">
              @for (r of roles; track r) { <option [value]="r">{{ r.charAt(0) + r.slice(1).toLowerCase() }}</option> }
            </select>
          </label>
          <label class="grow">SSO groups <input #ps class="field mono" placeholder="EFT-QA, EFT-Delivery" autocomplete="off" /></label>
          <button class="btn" type="submit"><mat-icon aria-hidden="true">search</mat-icon>Check</button>
        </div>
        @if (preview(); as p) {
          <div class="result" role="status">
            <span class="muted">Groups that apply: {{ p.groups.join(', ') || 'none' }}</span>
            <div class="chips">
              @for (m of modules.value(); track m.key) {
                <span class="chip" [class]="'chip ' + p.modules[m.key].toLowerCase()">{{ m.label }}: {{ label(p.modules[m.key]) }}</span>
              }
            </div>
          </div>
        }
      </form>
    </app-card-group>
  `,
  styles: `
    .rules { margin: 0; padding-left: 18px; display: grid; gap: 4px; font-size: 13.5px; }
    .matrix th[scope='row'] { min-width: 220px; }
    .matrix th[scope='col'] { min-width: 130px; vertical-align: bottom; }
    .g-name, .m-name { display: block; font-weight: 600; }
    .g-who, .m-what { display: block; font-size: 11.5px; font-weight: 400; color: var(--app-muted); text-transform: none; letter-spacing: 0; }
    .lvl { font: inherit; font-size: 13px; border-radius: 8px; padding: 5px 8px; border: 1px solid var(--app-line-strong); }
    .lvl.none { background: var(--neutral-soft); color: var(--app-muted); }
    .lvl.view { background: var(--info-soft); color: var(--eu-navy-800); }
    .lvl.edit { background: var(--ok-soft, #e6f6ee); color: #0b5d36; font-weight: 600; }
    .card.new, .card.group, .card.preview { display: flex; flex-direction: column; gap: 10px; margin-bottom: 12px; }
    .row-fields { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 10px 14px; }
    .row-fields .grow { flex: 1 1 260px; }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; }
    .field { font: inherit; font-weight: 400; border: 1px solid var(--app-line-strong); border-radius: 8px; padding: 7px 10px; background: var(--app-surface, #fff); color: var(--app-text); }
    .field.name { font-weight: 600; min-width: 220px; }
    textarea.field { resize: vertical; }
    .two { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; }
    @media (max-width: 720px) { .two { grid-template-columns: 1fr; } }
    .g-head { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
    .badge { font-size: 11.5px; padding: 2px 8px; border-radius: 6px; background: var(--neutral-soft); color: var(--app-muted); }
    .actions { display: flex; align-items: center; gap: 8px; }
    .btn.danger { color: var(--bad); }
    .hint { font-size: 12px; font-weight: 400; color: var(--app-muted); }
    .small { font-size: 12px; }
    .mono { font-family: var(--font-mono); }
    .result { display: flex; flex-direction: column; gap: 8px; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    .chip { font-size: 12.5px; padding: 3px 9px; border-radius: 999px; }
    .chip.none { background: var(--neutral-soft); color: var(--app-muted); text-decoration: line-through; }
    .chip.view { background: var(--info-soft); color: var(--eu-navy-800); }
    .chip.edit { background: var(--ok-soft, #e6f6ee); color: #0b5d36; font-weight: 600; }
  `,
})
export class AdminAccessPage {
  protected readonly levels = LEVELS;
  protected readonly roles: PortalRole[] = ['VIEWER', 'EDITOR', 'ADMIN'];
  protected readonly modules = httpResource<ModuleView[]>(() => apiUrl('admin/access-groups/modules'), { defaultValue: [] });
  protected readonly groups = httpResource<GroupView[]>(() => apiUrl('admin/access-groups'), { defaultValue: [] });
  protected readonly busy = signal<number | null>(null);
  protected readonly preview = signal<Preview | null>(null);
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(Auth);
  private readonly moduleLabel = computed(() => new Map(this.modules.value().map((m) => [m.key, m.label])));

  protected who(g: GroupView): string {
    if (g.builtin) {
      return 'all signed-in users';
    }
    const parts = [];
    if (g.ssoGroups.length) {
      parts.push(g.ssoGroups.length + ' SSO group' + (g.ssoGroups.length === 1 ? '' : 's'));
    }
    if (g.members.length) {
      parts.push(g.members.length + ' user' + (g.members.length === 1 ? '' : 's'));
    }
    return parts.join(' · ') || 'no members yet';
  }

  protected label(l: AccessLevel): string {
    return LEVELS.find((x) => x.value === l)?.label ?? l;
  }

  protected setLevel(g: GroupView, module: PortalModule, level: AccessLevel): void {
    const modules = { ...g.modules, [module]: level };
    this.save(g, { ...this.body(g), modules }, `${g.name}: ${this.moduleLabel().get(module)} → ${this.label(level)}`);
  }

  protected saveDetails(g: GroupView, name: string, description: string, sso: string, members: string): void {
    this.save(
      g,
      { ...this.body(g), name: name.trim() || g.name, description: description.trim() || null, ssoGroups: split(sso), members: split(members) },
      `${name.trim() || g.name} saved`,
    );
  }

  protected create(name: string, description: string): void {
    if (!name.trim()) {
      return;
    }
    const modules = Object.fromEntries(this.modules.value().map((m) => [m.key, 'NONE']));
    this.http
      .post(apiUrl('admin/access-groups'), { name: name.trim(), description: description.trim() || null, modules, ssoGroups: [], members: [] })
      .subscribe(() => {
        this.snackBar.open(`Group ${name.trim()} created: set its levels in the matrix`, 'OK', { duration: 5000 });
        this.groups.reload();
      });
  }

  protected remove(g: GroupView): void {
    if (!window.confirm(`Delete the access group "${g.name}"? Its members lose what only this group gave them.`)) {
      return;
    }
    this.http.delete(apiUrl(`admin/access-groups/${g.id}`)).subscribe(() => {
      this.snackBar.open(`Group ${g.name} deleted`, 'OK', { duration: 4000 });
      this.groups.reload();
    });
  }

  protected check(username: string, role: PortalRole, sso: string): void {
    this.http
      .post<Preview>(apiUrl('admin/access-groups/preview'), { username: username.trim() || null, role, ssoGroups: split(sso) })
      .subscribe((p) => this.preview.set(p));
  }

  private body(g: GroupView) {
    return { name: g.name, description: g.description, modules: g.modules, ssoGroups: g.ssoGroups, members: g.members };
  }

  private save(g: GroupView, body: ReturnType<AdminAccessPage['body']>, message: string): void {
    this.busy.set(g.id);
    this.http.put<GroupView>(apiUrl(`admin/access-groups/${g.id}`), body).subscribe({
      next: () => {
        this.busy.set(null);
        this.snackBar.open(message, 'OK', { duration: 3000 });
        this.groups.reload();
        void this.auth.load(true); // the admin's own menu follows straight away
      },
      error: () => {
        this.busy.set(null);
        this.groups.reload(); // put the matrix back to what is stored
      },
    });
  }
}
