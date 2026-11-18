import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { Environments } from '../../core/environments';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { ChangeView } from './inventory.models';

const TYPE_LABEL: Record<ChangeView['changeType'], string> = {
  APPLICATION_ADDED: 'Application added',
  DEPLOYMENT_ADDED: 'Path added',
  DEPLOYMENT_REMOVED: 'Path removed',
  FIELD_CHANGED: 'Changed',
};

/** Support view: who/what changed in the inventory (owner moves, URL and version changes, portal edits). */
@Component({
  selector: 'app-inventory-changes',
  imports: [DatePipe, LoadState, StatusChip],
  template: `
    <div class="filters">
      <input
        class="search"
        type="search"
        placeholder="Search application, field or value"
        aria-label="Search changes"
        [value]="search()"
        (input)="search.set($any($event.target).value)"
      />
      <div class="chips" role="group" aria-label="Change type">
        <button
          type="button"
          class="chip-btn"
          [attr.aria-pressed]="type() === ''"
          (click)="type.set('')"
        >
          All
        </button>
        <button
          type="button"
          class="chip-btn"
          [attr.aria-pressed]="type() === 'owner'"
          (click)="type.set('owner')"
        >
          Owner changes
        </button>
        <button
          type="button"
          class="chip-btn"
          [attr.aria-pressed]="type() === 'DEPLOYMENT_ADDED'"
          (click)="type.set('DEPLOYMENT_ADDED')"
        >
          New paths
        </button>
        <button
          type="button"
          class="chip-btn"
          [attr.aria-pressed]="type() === 'PORTAL'"
          (click)="type.set('PORTAL')"
        >
          Portal edits
        </button>
      </div>
    </div>
    <app-load-state
      [loading]="changes.isLoading()"
      [error]="changes.error()"
      [empty]="changes.hasValue() && rows().length === 0"
      what="the change log"
      emptyText="No changes recorded yet. Changes appear after imports and portal edits."
      (retry)="changes.reload()"
    />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="Inventory change log">
          <thead>
            <tr>
              <th>When</th>
              <th>Type</th>
              <th>Application</th>
              <th>Environment</th>
              <th>Field</th>
              <th>Before</th>
              <th>After</th>
              <th>Source</th>
            </tr>
          </thead>
          <tbody>
            @for (c of rows(); track c.id) {
              <tr>
                <td>{{ c.changedAt | date: 'dd MMM yyyy, HH:mm' }}</td>
                <td>
                  <app-status-chip
                    [status]="c.changeType === 'FIELD_CHANGED' ? 'MEDIUM' : c.changeType === 'DEPLOYMENT_REMOVED' ? 'HIGH' : 'SUCCESS'"
                    [text]="label[c.changeType]"
                  />
                </td>
                <td>
                  <strong>{{ c.application ?? '—' }}</strong>
                  <span class="muted">· {{ c.client ?? '' }}</span>
                </td>
                <td>
                  {{ c.environment ? envs.name(c.environment) : '—' }}
                  <span class="mono muted">{{ c.contextName ?? '' }}</span>
                </td>
                <td class="mono">{{ c.field ?? '—' }}</td>
                <td class="wrap muted">{{ c.oldValue ?? '—' }}</td>
                <td class="wrap">{{ c.newValue ?? '—' }}</td>
                <td>{{ c.source === 'IMPORT' ? 'Excel import' : 'Portal' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class InventoryChangesPage {
  /** ?q=application deep link from the overview. */
  readonly q = input<string>();
  protected readonly envs = inject(Environments);
  protected readonly label = TYPE_LABEL;
  protected readonly search = linkedSignal(() => this.q() ?? '');
  protected readonly type = signal('');
  protected readonly changes = httpResource<ChangeView[]>(
    () => apiUrl('inventory/changes', { limit: 1000 }),
    { defaultValue: [] },
  );
  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const t = this.type();
    return this.changes
      .value()
      .filter(
        (c) =>
          !t ||
          (t === 'owner'
            ? c.field === 'owner'
            : t === 'PORTAL'
              ? c.source === 'PORTAL'
              : c.changeType === t),
      )
      .filter(
        (c) =>
          !q ||
          [c.application ?? '', c.field ?? '', c.oldValue ?? '', c.newValue ?? ''].some((v) =>
            v.toLowerCase().includes(q),
          ),
      );
  });
}
