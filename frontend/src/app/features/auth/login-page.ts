import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { Auth, safeReturnTo } from '../../core/auth';
import { BrandMark } from '../../shared/brand-mark';
import { ToolLogo } from '../../shared/tool-logo';

/** Sign-in: single sign-on first (Microsoft Entra ID), local account form for break-glass / non-SSO users. */
@Component({
  selector: 'app-login-page',
  imports: [MatIconModule, BrandMark, ToolLogo],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage implements OnInit {
  // query parameters
  readonly returnTo = input<string>();
  readonly error = input<string>();
  readonly expired = input<string>();
  readonly signedOut = input<string>();
  protected readonly ready = signal(false);
  protected readonly busy = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly showLocal = signal(false);
  protected readonly notice = computed(() => {
    if (this.error() === 'sso') {
      return {
        tone: 'bad',
        text: 'Single sign-on did not complete, or your account has no access to this portal.',
      };
    }
    if (this.error() === 'sso-disabled') {
      return { tone: 'bad', text: 'Single sign-on is not configured on this server.' };
    }
    if (this.expired() !== undefined) {
      return { tone: 'warn', text: 'Your session has ended. Please sign in again.' };
    }
    if (this.signedOut() !== undefined) {
      return { tone: 'ok', text: 'You have signed out.' };
    }
    return null;
  });
  private readonly auth = inject(Auth);
  protected readonly providers = this.auth.providers;
  private readonly router = inject(Router);

  async ngOnInit(): Promise<void> {
    const session = await this.auth.load(true).catch(() => null);
    if (session?.authenticated) {
      await this.router.navigateByUrl(safeReturnTo(this.returnTo()));
      return;
    }
    // local form open by default only when SSO is not available
    this.showLocal.set(!session?.providers.ssoEnabled);
    this.ready.set(true);
  }

  protected sso(): void {
    window.location.assign(this.auth.ssoUrl(safeReturnTo(this.returnTo())));
  }

  protected async signIn(username: string, password: string): Promise<void> {
    if (!username.trim() || !password) {
      this.formError.set('Enter your username and password.');
      return;
    }
    this.busy.set(true);
    this.formError.set(null);
    try {
      const user = await this.auth.loginLocal(username.trim(), password);
      const target = safeReturnTo(this.returnTo());
      await this.router.navigateByUrl(
        user.mustChangePassword
          ? `/account/password?returnTo=${encodeURIComponent(target)}`
          : target,
      );
    } catch (e) {
      const status = e instanceof HttpErrorResponse ? e.status : 0;
      this.formError.set(
        status === 401
          ? 'Invalid username or password.'
          : status === 429
            ? 'Too many attempts. Wait a minute and try again.'
            : 'Sign-in is not available right now. Try again later.',
      );
    } finally {
      this.busy.set(false);
    }
  }
}
