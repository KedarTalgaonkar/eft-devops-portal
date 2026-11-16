import { Component, inject, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { Auth, PortalRole } from '../../core/auth';
import { ago } from '../../core/models';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { generateTemporaryPassword, LocalUserView } from './admin.models';

/**
 * Local (database) accounts: create with a temporary password, change roles, enable / disable, unlock, reset.
 * SSO users are managed in Microsoft Entra ID (app role assignments) and do not appear here.
 */
@Component({
  selector: 'app-admin-users',
  imports: [DatePipe, TitleCasePipe, MatIconModule, MatTooltipModule, LoadState, StatusChip],
  templateUrl: './admin-users.html',
  styles: `
    .create {
      display: grid;
      gap: 12px;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      align-items: end;
    }
    .create label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 13px;
      font-weight: 600;
    }
    .field {
      height: 38px;
      padding: 0 10px;
      border: 1px solid var(--app-line-strong);
      border-radius: 10px;
      font: inherit;
      font-weight: 400;
    }
    .roles {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
    .secret {
      display: flex;
      flex-direction: column;
      gap: 6px;
      background: var(--warn-soft);
      color: var(--warn);
    }
    .secret code {
      font-family: var(--font-mono);
      font-size: 16px;
      color: var(--app-text);
      background: #fff;
      padding: 6px 10px;
      border-radius: 8px;
      user-select: all;
    }
    .actions {
      display: flex;
      gap: 4px;
    }
    .icon-btn {
      display: inline-grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border: 1px solid var(--app-line);
      border-radius: 8px;
      background: #fff;
      cursor: pointer;
      color: var(--app-muted);
    }
    .icon-btn:hover:not(:disabled) {
      color: var(--eu-navy-600);
      border-color: var(--eu-navy-400);
    }
    .icon-btn:disabled {
      opacity: 0.4;
      cursor: default;
    }
    .icon-btn mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .role-select {
      height: 32px;
      border: 1px solid var(--app-line-strong);
      border-radius: 8px;
      font: inherit;
    }
  `,
})
export class AdminUsersPage {
  protected readonly auth = inject(Auth);
  protected readonly ago = ago;
  protected readonly roleOptions: PortalRole[] = ['VIEWER', 'EDITOR', 'ADMIN'];
  protected readonly users = httpResource<LocalUserView[]>(() => apiUrl('admin/users'), {
    defaultValue: [],
  });
  protected readonly issued = signal<{ username: string; password: string } | null>(null);
  protected readonly busy = signal(false);
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);

  protected create(username: string, displayName: string, email: string, role: string): void {
    const password = generateTemporaryPassword();
    this.busy.set(true);
    this.http
      .post<LocalUserView>(apiUrl('admin/users'), {
        username,
        displayName,
        email,
        roles: [role],
        temporaryPassword: password,
      })
      .subscribe({
        next: (u) => {
          this.busy.set(false);
          this.issued.set({ username: u.username, password });
          this.users.reload();
        },
        error: () => this.busy.set(false),
      });
  }

  protected setRole(u: LocalUserView, role: PortalRole): void {
    this.update(u, { roles: [role] }, `${u.username} is now ${role.toLowerCase()}`);
  }

  protected toggle(u: LocalUserView): void {
    this.update(
      u,
      { enabled: !u.enabled },
      `${u.username} ${u.enabled ? 'disabled; sessions ended' : 'enabled'}`,
    );
  }

  protected unlock(u: LocalUserView): void {
    this.http.post(apiUrl(`admin/users/${u.id}/unlock`), {}).subscribe(() => {
      this.snackBar.open(`${u.username} unlocked`, 'OK', { duration: 4000 });
      this.users.reload();
    });
  }

  protected reset(u: LocalUserView): void {
    const password = generateTemporaryPassword();
    this.http
      .post(apiUrl(`admin/users/${u.id}/reset-password`), { temporaryPassword: password })
      .subscribe(() => {
        this.issued.set({ username: u.username, password });
        this.users.reload();
      });
  }

  protected isSelf(u: LocalUserView): boolean {
    return u.username.toLowerCase() === (this.auth.user()?.username ?? '').toLowerCase();
  }

  protected topRole(u: LocalUserView): PortalRole {
    return u.roles.includes('ADMIN') ? 'ADMIN' : u.roles.includes('EDITOR') ? 'EDITOR' : 'VIEWER';
  }

  private update(u: LocalUserView, change: object, message: string): void {
    this.http.put(apiUrl(`admin/users/${u.id}`), change).subscribe(() => {
      this.snackBar.open(message, 'OK', { duration: 4000 });
      this.users.reload();
    });
  }
}
