import { Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Auth } from '../../core/auth';

/** Shown when the user's access groups grant no module at all. */
@Component({
  selector: 'app-no-access-page',
  imports: [MatIconModule],
  template: `
    <section class="card empty" role="status">
      <mat-icon aria-hidden="true">lock_person</mat-icon>
      <h1>No modules are open to you yet</h1>
      <p>
        You are signed in as <strong>{{ auth.user()?.displayName }}</strong>, but none of your access groups grants a
        module. Ask a portal administrator to add you to an access group (Administration → Access groups).
      </p>
    </section>
  `,
  styles: `
    .empty { max-width: 640px; margin: 48px auto; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 32px; }
    .empty mat-icon { font-size: 40px; width: 40px; height: 40px; color: var(--app-muted); }
    h1 { font-size: 20px; margin: 4px 0; }
    p { color: var(--app-muted); margin: 0; }
  `,
})
export class NoAccessPage {
  protected readonly auth = inject(Auth);
}
