import { Component, inject, model } from '@angular/core';
import { Environments } from '../core/environments';

/** Environment chips built from the catalogue ("All" + every active environment). */
@Component({
  selector: 'app-env-filter',
  template: `
    <div class="chips" role="group" aria-label="Environment">
      <button
        type="button"
        class="chip-btn"
        [attr.aria-pressed]="value() === ''"
        (click)="value.set('')"
      >
        All
      </button>
      @for (e of envs.active(); track e.code) {
        <button
          type="button"
          class="chip-btn"
          [attr.aria-pressed]="value() === e.code"
          (click)="value.set(e.code)"
        >
          {{ e.name }}
        </button>
      }
    </div>
  `,
})
export class EnvFilter {
  readonly value = model('');
  protected readonly envs = inject(Environments);
}
