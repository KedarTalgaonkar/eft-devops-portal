import { Component, computed, input, output } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { errorMessage } from '../core/api';

/** Loading bar, error with retry, or an illustrated empty state for a block of data. */
@Component({
  selector: 'app-load-state',
  imports: [MatProgressBarModule],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" aria-label="Loading" />
    }
    @if (error()) {
      <div class="card error" role="alert">
        <span>Could not load {{ what() }}: {{ message() }}</span>
        <button class="btn" type="button" (click)="retry.emit()">Retry</button>
      </div>
    } @else if (empty() && !loading()) {
      <div class="card empty">
        <img src="/img/empty-state.svg" alt="" width="120" height="90" />
        <span>{{ emptyText() }}</span>
      </div>
    }
  `,
  styles: `
    .error {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      color: var(--bad);
      background: var(--bad-soft);
    }
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
      padding: 28px;
      color: var(--app-muted);
      text-align: center;
    }
  `,
})
export class LoadState {
  readonly loading = input(false);
  readonly error = input<unknown>();
  readonly empty = input(false);
  readonly what = input('data');
  readonly emptyText = input('Nothing to show yet.');
  readonly retry = output<void>();

  protected readonly message = computed(() => errorMessage(this.error()));
}
