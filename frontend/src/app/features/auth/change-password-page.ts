import { Component, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { errorMessage } from '../../core/api';
import { Auth, safeReturnTo } from '../../core/auth';
import { passwordChecks } from './password-policy';

/** Change the password of a local account; forced after an administrator sets a temporary password. */
@Component({
  selector: 'app-change-password-page',
  imports: [MatIconModule, RouterLink],
  template: `
    <div class="wrap">
      <div class="card box">
        <img src="/favicon.svg" alt="" width="40" height="40" />
        <h1>{{ forced() ? 'Set your password' : 'Change password' }}</h1>
        @if (forced()) {
          <p class="notice warn">You signed in with a temporary password. Choose your own before continuing.</p>
        }
        @if (isSso()) {
          <p class="notice">Your account uses single sign-on: change your password in Microsoft Entra ID.</p>
          <a class="btn" routerLink="/dashboard">Back to the portal</a>
        } @else {
          <form (submit)="$event.preventDefault(); save()" novalidate>
            <label for="current">{{ forced() ? 'Temporary password' : 'Current password' }}</label>
            <input id="current" class="field" type="password" autocomplete="current-password" maxlength="200"
                   [value]="current()" (input)="current.set($any($event.target).value)" />
            <label for="next">New password</label>
            <input id="next" class="field" type="password" autocomplete="new-password" maxlength="200"
                   [value]="next()" (input)="next.set($any($event.target).value)" />
            <ul class="checks" aria-live="polite">
              @for (c of checks(); track c.label) {
                <li [class.ok]="c.ok"><mat-icon aria-hidden="true">{{ c.ok ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon>{{ c.label }}</li>
              }
            </ul>
            <label for="confirm">Repeat new password</label>
            <input id="confirm" class="field" type="password" autocomplete="new-password" maxlength="200"
                   [value]="confirm()" (input)="confirm.set($any($event.target).value)" />
            @if (confirm() && confirm() !== next()) {
              <p class="mismatch">The passwords do not match.</p>
            }
            @if (error()) {
              <p class="notice bad" role="alert">{{ error() }}</p>
            }
            <button class="btn primary" type="submit" [disabled]="!valid() || busy()">{{ busy() ? 'Saving…' : 'Save password' }}</button>
            @if (!forced()) {
              <a class="btn" routerLink="/dashboard">Cancel</a>
            }
          </form>
        }
      </div>
    </div>
  `,
  styles: `
    .wrap { min-height: 100dvh; display: grid; place-items: center; padding: 24px; background: var(--eu-navy-800) url('/img/network-pattern.svg') right bottom / 90% auto no-repeat; }
    .box { width: 100%; max-width: 440px; padding: 32px; display: flex; flex-direction: column; gap: 12px; }
    h1 { font-size: 22px; }
    form { display: flex; flex-direction: column; gap: 8px; }
    label { font-size: 13px; font-weight: 600; margin-top: 4px; }
    .field { height: 42px; padding: 0 12px; border: 1px solid var(--app-line-strong); border-radius: 10px; font: inherit; }
    .btn { justify-content: center; height: 44px; margin-top: 6px; }
    .checks { list-style: none; margin: 4px 0; padding: 0; display: grid; gap: 4px; font-size: 13px; color: var(--app-muted); }
    .checks li { display: flex; align-items: center; gap: 6px; }
    .checks li.ok { color: var(--ok); }
    .checks mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .notice { margin: 0; padding: 10px 12px; border-radius: 10px; font-size: 13.5px; background: var(--info-soft); color: var(--info); }
    .notice.warn { background: var(--warn-soft); color: var(--warn); }
    .notice.bad { background: var(--bad-soft); color: var(--bad); }
    .mismatch { margin: 0; font-size: 13px; color: var(--bad); }
  `,
})
export class ChangePasswordPage {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  readonly returnTo = input<string>();

  protected readonly current = signal('');
  protected readonly next = signal('');
  protected readonly confirm = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly forced = computed(() => !!this.auth.user()?.mustChangePassword);
  protected readonly isSso = computed(() => this.auth.user()?.source === 'SSO');
  protected readonly checks = computed(() => passwordChecks(this.next(), this.auth.user()?.username ?? ''));
  protected readonly valid = computed(
    () => !!this.current() && this.checks().every((c) => c.ok) && this.next() === this.confirm() && this.next() !== this.current(),
  );

  protected async save(): Promise<void> {
    if (!this.valid()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.changePassword(this.current(), this.next());
      await this.router.navigateByUrl(safeReturnTo(this.returnTo()));
    } catch (e) {
      this.error.set(e instanceof HttpErrorResponse && e.status === 400 ? errorMessage(e) : 'The password could not be changed. Try again.');
    } finally {
      this.busy.set(false);
    }
  }
}
