import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

export type PortalRole = 'VIEWER' | 'EDITOR' | 'ADMIN';

export interface SessionUser {
  username: string;
  displayName: string;
  email: string | null;
  roles: PortalRole[];
  source: 'SSO' | 'LOCAL';
  mustChangePassword: boolean;
}

export type PortalModule =
  | 'DASHBOARD'
  | 'PIPELINES'
  | 'SOURCE_CODE'
  | 'ARTIFACTS'
  | 'QUALITY'
  | 'SBOM'
  | 'MONITORING'
  | 'INVENTORY';

export type AccessLevel = 'NONE' | 'VIEW' | 'EDIT';

export interface AuthSession {
  authenticated: boolean;
  user: SessionUser | null;
  providers: { ssoEnabled: boolean; ssoLabel: string; localEnabled: boolean };
  /** Effective access per module from the user's access groups, capped by role (set by the backend). */
  modules?: Partial<Record<PortalModule, AccessLevel>>;
}

const LEVEL: Record<AccessLevel, number> = { NONE: 0, VIEW: 1, EDIT: 2 };

const RANK: Record<PortalRole, number> = { VIEWER: 1, EDITOR: 2, ADMIN: 3 };

/**
 * Session state of the backend-for-frontend login. The browser only holds an HttpOnly session cookie; this service
 * asks the backend who is signed in and drives sign-in / sign-out. Tokens never reach the browser.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly state = signal<AuthSession | null>(null);
  readonly session = this.state.asReadonly();
  readonly user = computed(() => this.state()?.user ?? null);
  readonly authenticated = computed(() => !!this.state()?.authenticated);
  readonly providers = computed(
    () => this.state()?.providers ?? { ssoEnabled: false, ssoLabel: '', localEnabled: false },
  );
  private loading: Promise<AuthSession> | null = null;

  /** Loads the session once (also obtains the XSRF-TOKEN cookie); pass force to re-check. */
  load(force = false): Promise<AuthSession> {
    if (!force && this.state()) {
      return Promise.resolve(this.state()!);
    }
    if (!force && this.loading) {
      return this.loading;
    }
    this.loading = firstValueFrom(this.http.get<AuthSession>('/api/auth/session')).then((s) => {
      this.state.set(s);
      this.loading = null;
      return s;
    });
    return this.loading;
  }

  /** True when the user has the role or a higher one (ADMIN > EDITOR > VIEWER). */
  can(role: PortalRole): boolean {
    const roles = this.user()?.roles ?? [];
    return roles.some((r) => RANK[r] >= RANK[role]);
  }

  /** Access to a module. Without group data (older backend) the role decides, as before. */
  access(module: PortalModule): AccessLevel {
    const level = this.state()?.modules?.[module];
    if (level) {
      return level;
    }
    return this.can('EDITOR') ? 'EDIT' : this.can('VIEWER') ? 'VIEW' : 'NONE';
  }

  canView(module: PortalModule): boolean {
    return LEVEL[this.access(module)] >= LEVEL.VIEW;
  }

  /** May change data in this module (the backend enforces the same rule). */
  canEdit(module: PortalModule): boolean {
    return LEVEL[this.access(module)] >= LEVEL.EDIT;
  }

  ssoUrl(returnTo: string): string {
    return `/api/auth/sso?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`;
  }

  async loginLocal(username: string, password: string): Promise<SessionUser> {
    await this.load(); // make sure the XSRF cookie exists before the POST
    const user = await firstValueFrom(
      this.http.post<SessionUser>('/api/auth/login', { username, password }),
    );
    await this.load(true);
    return user;
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/auth/password', { currentPassword, newPassword }));
    await this.load(true);
  }

  async logout(): Promise<void> {
    let target = '/login?signedOut';
    try {
      const res = await firstValueFrom(
        this.http.post<{ logoutUrl: string }>('/api/auth/logout', {}),
      );
      target = res.logoutUrl;
    } finally {
      this.state.set(null);
      // full navigation: clears all in-memory data and lets the identity provider end its session too
      window.location.assign(target);
    }
  }

  /** Called by the interceptor when the backend says the session is gone. */
  sessionExpired(): void {
    this.state.set(null);
    const here = this.router.url;
    if (!here.startsWith('/login')) {
      void this.router.navigate(['/login'], {
        queryParams: { returnTo: safeReturnTo(here), expired: 1 },
      });
    }
  }
}

/** Same rule as the backend: only same-origin relative paths, never back to /login. */
export function safeReturnTo(path: string | null | undefined): string {
  if (
    !path ||
    !path.startsWith('/') ||
    path.startsWith('//') ||
    path.startsWith('/\\') ||
    path.startsWith('/login') ||
    path.startsWith('/api/') ||
    path.includes('://')
  ) {
    return '/';
  }
  return path;
}
