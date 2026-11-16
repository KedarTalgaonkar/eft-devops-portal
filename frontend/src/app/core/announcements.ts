import { Injectable, signal } from '@angular/core';

export type AnnouncementKind = 'INFO' | 'RELEASE' | 'MAINTENANCE' | 'INCIDENT';

export interface Announcement {
  id: number;
  kind: AnnouncementKind;
  message: string;
  linkUrl: string | null;
  linkLabel: string | null;
  startsAt: string;
  endsAt: string;
  status: 'SCHEDULED' | 'ACTIVE' | 'ENDED';
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface AnnouncementRequest {
  kind: AnnouncementKind;
  message: string;
  linkUrl: string | null;
  linkLabel: string | null;
  startsAt: string | null;
  endsAt: string;
}

export const KIND_META: Record<AnnouncementKind, { label: string; icon: string; hint: string }> = {
  INFO: { label: 'Notice', icon: 'campaign', hint: 'General information for everyone' },
  RELEASE: {
    label: 'Release',
    icon: 'rocket_launch',
    hint: 'A release or deployment is planned or in progress',
  },
  MAINTENANCE: { label: 'Maintenance', icon: 'construction', hint: 'Planned downtime or slowness' },
  INCIDENT: {
    label: 'Incident',
    icon: 'report',
    hint: 'Something is broken now; users cannot hide it',
  },
};

/** "until 23:00", "until 15 Nov, 01:00" or "15 Nov 21:00 – 23:00" in the browser's time zone (IST for most users). */
export function windowText(a: Pick<Announcement, 'startsAt' | 'endsAt'>, now = new Date()): string {
  const start = new Date(a.startsAt);
  const end = new Date(a.endsAt);
  const time = (d: Date) =>
    d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: false });
  const day = (d: Date) => d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
  const sameDay = (x: Date, y: Date) => x.toDateString() === y.toDateString();
  const endText = sameDay(end, now) ? time(end) : `${day(end)}, ${time(end)}`;
  if (start > now) {
    return sameDay(start, end)
      ? `${day(start)} ${time(start)} – ${time(end)}`
      : `${day(start)} ${time(start)} – ${day(end)} ${time(end)}`;
  }
  return `until ${endText}`;
}

/** Value for <input type="datetime-local"> in local time. */
export function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Lets the management page refresh the header banner right after a change (it otherwise polls every 2 minutes). */
@Injectable({ providedIn: 'root' })
export class AnnouncementFeed {
  readonly version = signal(0);

  refresh(): void {
    this.version.update((n) => n + 1);
  }
}
