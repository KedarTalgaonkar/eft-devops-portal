import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { apiUrl } from '../../core/api';
import {
  Announcement,
  AnnouncementFeed,
  AnnouncementKind,
  AnnouncementRequest,
  KIND_META,
  toLocalInput,
  windowText,
} from '../../core/announcements';
import { Auth } from '../../core/auth';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';

const KINDS: AnnouncementKind[] = ['RELEASE', 'MAINTENANCE', 'INFO', 'INCIDENT'];
const MAX = 500;

/**
 * Command centre → Announcements: time-bound messages in the header of every page (releases, deployments,
 * maintenance, incidents). Everyone sees what is running and scheduled; editors and administrators publish.
 */
@Component({
  selector: 'app-dashboard-announcements',
  imports: [DatePipe, MatIconModule, CardGroup, LoadState, StatusChip],
  template: `
    @if (canEdit) {
      <app-card-group
        [heading]="editing() ? 'Change announcement' : 'Post an announcement'"
        icon="campaign"
        description="Shown at the top of every page for all signed-in users between the start and end time, then removed automatically."
      >
        <form class="card form" (submit)="$event.preventDefault(); save()">
          <div class="kinds" role="radiogroup" aria-label="Type">
            @for (k of kinds; track k) {
              <button
                type="button"
                class="kind-btn"
                [class]="'kind-btn ' + k.toLowerCase()"
                [attr.aria-checked]="kind() === k"
                role="radio"
                (click)="kind.set(k)"
              >
                <mat-icon aria-hidden="true">{{ meta[k].icon }}</mat-icon>
                <span
                  ><strong>{{ meta[k].label }}</strong
                  ><small>{{ meta[k].hint }}</small></span
                >
              </button>
            }
          </div>

          <label class="field">
            <span
              >Message <small class="muted">{{ message().length }}/{{ max }}</small></span
            >
            <textarea
              rows="2"
              [maxLength]="max"
              required
              [value]="message()"
              (input)="message.set($any($event.target).value)"
              placeholder="e.g. IMPS 2.4 release to UAT tonight 21:00–23:00 IST. UAT URLs may be unavailable."
            ></textarea>
          </label>

          <div class="row-fields">
            <label class="field">
              <span>Starts</span>
              <input
                type="datetime-local"
                [value]="starts()"
                (change)="starts.set($any($event.target).value)"
              />
            </label>
            <label class="field">
              <span>Ends</span>
              <input
                type="datetime-local"
                required
                [value]="ends()"
                (change)="ends.set($any($event.target).value)"
              />
            </label>
            <div class="field">
              <span>Quick end</span>
              <div class="chips" role="group" aria-label="Quick end time">
                @for (q of quick; track q.label) {
                  <button type="button" class="chip-btn" (click)="setDuration(q.hours)">
                    {{ q.label }}
                  </button>
                }
                <button type="button" class="chip-btn" (click)="endOfDay()">End of today</button>
              </div>
            </div>
          </div>

          <div class="row-fields">
            <label class="field grow">
              <span
                >Link
                <small class="muted"
                  >optional: a portal page such as /pipelines/deployments, or an https://
                  address</small
                ></span
              >
              <input
                type="text"
                [value]="linkUrl()"
                (input)="linkUrl.set($any($event.target).value)"
                placeholder="/pipelines/deployments"
                maxlength="500"
              />
            </label>
            <label class="field">
              <span>Link text</span>
              <input
                type="text"
                [value]="linkLabel()"
                (input)="linkLabel.set($any($event.target).value)"
                placeholder="Details"
                maxlength="60"
              />
            </label>
          </div>

          <div class="preview" [class]="'preview ' + kind().toLowerCase()" aria-label="Preview">
            <mat-icon aria-hidden="true">{{ meta[kind()].icon }}</mat-icon>
            <span class="p-kind">{{ meta[kind()].label }}</span>
            <span class="p-msg">{{ message() || 'Your message appears here' }}</span>
            <span class="p-when">{{ previewWindow() }}</span>
          </div>

          @if (formError()) {
            <p class="error" role="alert">{{ formError() }}</p>
          }
          <div class="actions">
            <button class="btn primary" type="submit" [disabled]="saving()">
              <mat-icon>{{ editing() ? 'save' : 'campaign' }}</mat-icon>{{
                saving() ? 'Saving…' : editing() ? 'Save changes' : 'Publish'
              }}
            </button>
            @if (editing()) {
              <button class="btn" type="button" (click)="reset()">Cancel</button>
            }
          </div>
        </form>
      </app-card-group>
    }

    <app-card-group
      heading="Running and scheduled"
      icon="schedule"
      description="What users see now and what is coming up. Ended announcements stay here for 30 days."
    >
      <app-load-state
        [loading]="list.isLoading()"
        [error]="list.error()"
        [empty]="list.hasValue() && list.value().length === 0"
        what="announcements"
        emptyText="No announcements in the last 30 days."
        (retry)="list.reload()"
      />
      @if (list.value().length) {
        <div class="table-wrap">
          <table class="data" aria-label="Announcements">
            <thead>
              <tr>
                <th>Status</th>
                <th>Type</th>
                <th>Message</th>
                <th>When</th>
                <th>By</th>
                @if (canEdit) {
                  <th></th>
                }
              </tr>
            </thead>
            <tbody>
              @for (a of sorted(); track a.id) {
                <tr>
                  <td>
                    <app-status-chip
                      [status]="
                        a.status === 'ACTIVE'
                          ? 'LIVE'
                          : a.status === 'SCHEDULED'
                            ? 'PENDING'
                            : 'LOW'
                      "
                      [text]="
                        a.status === 'ACTIVE'
                          ? 'Showing now'
                          : a.status === 'SCHEDULED'
                            ? 'Scheduled'
                            : 'Ended'
                      "
                    />
                  </td>
                  <td>
                    <span class="k" [class]="'k ' + a.kind.toLowerCase()"
                      ><mat-icon aria-hidden="true">{{ meta[a.kind].icon }}</mat-icon
                      >{{ meta[a.kind].label }}</span
                    >
                  </td>
                  <td class="wrap">
                    {{ a.message }}
                    @if (a.linkUrl) {
                      <span class="muted"> · {{ a.linkLabel }}: {{ a.linkUrl }}</span>
                    }
                  </td>
                  <td>
                    {{ a.startsAt | date: 'dd MMM, HH:mm' }} –
                    {{ a.endsAt | date: 'dd MMM, HH:mm' }}
                  </td>
                  <td>{{ a.updatedBy ?? a.createdBy }}</td>
                  @if (canEdit) {
                    <td class="row-actions">
                      @if (a.status !== 'ENDED') {
                        <button class="btn" type="button" (click)="edit(a)">Edit</button>
                        <button class="btn" type="button" (click)="end(a)">End now</button>
                      }
                      <button class="btn" type="button" (click)="remove(a)">Delete</button>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </app-card-group>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 14px;
    }
    .kinds {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 8px;
    }
    .kind-btn {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      text-align: left;
      padding: 10px 12px;
      border-radius: 10px;
      border: 1px solid var(--app-line);
      background: var(--app-card);
      cursor: pointer;
      font: inherit;
      color: var(--app-text);
    }
    .kind-btn span {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .kind-btn small {
      color: var(--app-muted);
      font-size: 12px;
    }
    .kind-btn[aria-checked='true'] {
      border-width: 2px;
      padding: 9px 11px;
    }
    .kind-btn.info[aria-checked='true'] {
      border-color: var(--info);
      background: var(--info-soft);
    }
    .kind-btn.release[aria-checked='true'] {
      border-color: var(--eu-teal);
      background: var(--eu-teal-soft);
    }
    .kind-btn.maintenance[aria-checked='true'] {
      border-color: var(--warn);
      background: var(--warn-soft);
    }
    .kind-btn.incident[aria-checked='true'] {
      border-color: var(--bad);
      background: var(--bad-soft);
    }
    .field {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 13px;
      font-weight: 600;
    }
    .field small {
      font-weight: 400;
    }
    .field textarea,
    .field input {
      font: inherit;
      font-weight: 400;
      padding: 8px 10px;
      border: 1px solid var(--app-line-strong);
      border-radius: 8px;
    }
    .row-fields {
      display: flex;
      flex-wrap: wrap;
      gap: 12px 16px;
      align-items: flex-end;
    }
    .grow {
      flex: 1 1 320px;
    }
    .preview {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px 10px;
      padding: 9px 14px;
      border-radius: 8px;
      font-size: 13.5px;
    }
    .preview.info {
      background: var(--info-soft);
      color: var(--eu-navy-800);
    }
    .preview.release {
      background: var(--eu-teal-soft);
      color: #064e4d;
    }
    .preview.maintenance {
      background: var(--warn-soft);
      color: #6b4300;
    }
    .preview.incident {
      background: var(--bad);
      color: #fff;
    }
    .p-kind {
      font-weight: 700;
      text-transform: uppercase;
      font-size: 11.5px;
      letter-spacing: 0.06em;
    }
    .p-msg {
      flex: 1;
      font-weight: 500;
    }
    .p-when {
      font-size: 12.5px;
      opacity: 0.85;
    }
    .error {
      margin: 0;
      color: var(--bad);
      font-weight: 600;
    }
    .actions {
      display: flex;
      gap: 8px;
    }
    .k {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-weight: 600;
      font-size: 12.5px;
    }
    .k mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }
    .k.release {
      color: #0b7a78;
    }
    .k.maintenance {
      color: var(--warn);
    }
    .k.incident {
      color: var(--bad);
    }
    .k.info {
      color: var(--info);
    }
    .row-actions {
      white-space: nowrap;
      display: flex;
      gap: 6px;
    }
  `,
})
export class DashboardAnnouncementsPage {
  protected readonly canEdit = inject(Auth).canEdit('DASHBOARD');
  protected readonly meta = KIND_META;
  protected readonly kinds = KINDS;
  protected readonly max = MAX;
  protected readonly quick = [
    { label: '1 h', hours: 1 },
    { label: '2 h', hours: 2 },
    { label: '4 h', hours: 4 },
    { label: '1 day', hours: 24 },
    { label: '1 week', hours: 168 },
  ];
  protected readonly list = httpResource<Announcement[]>(
    () => apiUrl('announcements', { limit: 100 }),
    { defaultValue: [] },
  );
  protected readonly sorted = computed(() => {
    const order = { ACTIVE: 0, SCHEDULED: 1, ENDED: 2 } as const;
    return [...this.list.value()].sort(
      (a, b) => order[a.status] - order[b.status] || b.startsAt.localeCompare(a.startsAt),
    );
  });
  protected readonly editing = signal<Announcement | null>(null);
  protected readonly kind = signal<AnnouncementKind>('RELEASE');
  protected readonly message = signal('');
  protected readonly starts = signal(toLocalInput(new Date()));
  protected readonly ends = signal(toLocalInput(new Date(Date.now() + 2 * 3_600_000)));
  protected readonly linkUrl = signal('');
  protected readonly linkLabel = signal('');
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly previewWindow = computed(() => {
    const s = this.starts() ? new Date(this.starts()) : new Date();
    const e = this.ends() ? new Date(this.ends()) : null;
    return e && !isNaN(e.getTime())
      ? windowText({ startsAt: s.toISOString(), endsAt: e.toISOString() })
      : '';
  });
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly feed = inject(AnnouncementFeed);

  protected setDuration(hours: number): void {
    const from = this.starts() ? new Date(this.starts()) : new Date();
    this.ends.set(toLocalInput(new Date(from.getTime() + hours * 3_600_000)));
  }

  protected endOfDay(): void {
    const d = this.starts() ? new Date(this.starts()) : new Date();
    d.setHours(23, 59, 0, 0);
    this.ends.set(toLocalInput(d));
  }

  protected save(): void {
    this.formError.set(null);
    const start = this.starts() ? new Date(this.starts()) : null;
    const end = this.ends() ? new Date(this.ends()) : null;
    if (this.message().trim().length < 3) {
      this.formError.set('Write a message of at least 3 characters.');
      return;
    }
    if (!end || isNaN(end.getTime()) || (start && end <= start)) {
      this.formError.set('The end time must be after the start time.');
      return;
    }
    const body: AnnouncementRequest = {
      kind: this.kind(),
      message: this.message().trim(),
      linkUrl: this.linkUrl().trim() || null,
      linkLabel: this.linkLabel().trim() || null,
      startsAt: start ? start.toISOString() : null,
      endsAt: end.toISOString(),
    };
    const current = this.editing();
    this.saving.set(true);
    const call = current
      ? this.http.put<Announcement>(apiUrl(`announcements/${current.id}`), body)
      : this.http.post<Announcement>(apiUrl('announcements'), body);
    call.subscribe({
      next: (a) => {
        this.saving.set(false);
        this.snackBar.open(
          a.status === 'ACTIVE'
            ? 'Announcement is now showing to all users.'
            : 'Announcement scheduled.',
          'OK',
          { duration: 5000 },
        );
        this.reset();
        this.changed();
      },
      error: () => this.saving.set(false),
    });
  }

  protected edit(a: Announcement): void {
    this.editing.set(a);
    this.kind.set(a.kind);
    this.message.set(a.message);
    this.starts.set(toLocalInput(new Date(a.startsAt)));
    this.ends.set(toLocalInput(new Date(a.endsAt)));
    this.linkUrl.set(a.linkUrl ?? '');
    this.linkLabel.set(a.linkLabel ?? '');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected reset(): void {
    this.editing.set(null);
    this.kind.set('RELEASE');
    this.message.set('');
    this.starts.set(toLocalInput(new Date()));
    this.ends.set(toLocalInput(new Date(Date.now() + 2 * 3_600_000)));
    this.linkUrl.set('');
    this.linkLabel.set('');
    this.formError.set(null);
  }

  protected end(a: Announcement): void {
    this.http
      .post<Announcement>(apiUrl(`announcements/${a.id}/end`), null)
      .subscribe(() => this.changed());
  }

  protected remove(a: Announcement): void {
    if (!window.confirm('Delete this announcement? It is removed from the history too.')) {
      return;
    }
    this.http.delete(apiUrl(`announcements/${a.id}`)).subscribe(() => this.changed());
  }

  private changed(): void {
    this.list.reload();
    this.feed.refresh();
  }
}
