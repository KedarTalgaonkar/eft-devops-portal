import { Component, computed, input } from '@angular/core';

type Tone = 'ok' | 'bad' | 'warn' | 'info' | 'neutral';

const TONES: Record<string, Tone> = {
  SUCCESS: 'ok', UP: 'ok', OK: 'ok', COMPLETED: 'ok', REACHABLE: 'ok', LIVE: 'ok', GREEN: 'ok', PASSED: 'ok', LOW: 'neutral',
  FAILURE: 'bad', DOWN: 'bad', ERROR: 'bad', CRITICAL: 'bad', FAILED: 'bad', FIRING: 'bad', RED: 'bad',
  ABORTED: 'warn', UNSTABLE: 'warn', WARN: 'warn', HIGH: 'warn', PENDING: 'warn', WARNING: 'warn', AMBER: 'warn',
  IN_PROGRESS: 'info', MEDIUM: 'info', MOCK: 'info', OPEN: 'info',
};

const ICONS: Record<Tone, string> = { ok: '●', bad: '▲', warn: '◆', info: '◌', neutral: '○' };

/** Status label with colour AND a shape, so it reads without colour vision. */
@Component({
  selector: 'app-status-chip',
  template: `<span class="chip" [class]="'chip ' + tone()"><span aria-hidden="true">{{ shape() }}</span>{{ label() }}</span>`,
  styles: `
    .chip { display: inline-flex; align-items: center; gap: 5px; padding: 2px 9px; border-radius: 999px; font-size: 12px;
      font-weight: 600; line-height: 18px; white-space: nowrap; }
    .chip span { font-size: 9px; }
    .ok { background: var(--ok-soft); color: var(--ok); }
    .bad { background: var(--bad-soft); color: var(--bad); }
    .warn { background: var(--warn-soft); color: var(--warn); }
    .info { background: var(--info-soft); color: var(--info); }
    .neutral { background: var(--neutral-soft); color: var(--app-muted); }
  `,
})
export class StatusChip {
  readonly status = input<string | null | undefined>();
  readonly text = input<string>();

  protected readonly tone = computed<Tone>(() => TONES[(this.status() ?? '').toUpperCase()] ?? 'neutral');
  protected readonly shape = computed(() => ICONS[this.tone()]);
  protected readonly label = computed(() => {
    if (this.text()) {
      return this.text();
    }
    const s = this.status();
    if (!s) {
      return '—';
    }
    const words = s.replace(/_/g, ' ').toLowerCase();
    return words.charAt(0).toUpperCase() + words.slice(1);
  });
}
