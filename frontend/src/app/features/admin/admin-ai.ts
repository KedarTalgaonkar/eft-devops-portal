import { Component, computed } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { AiStatus } from '../../core/ai';
import { KpiCard } from '../../shared/kpi-card';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';

interface Totals {
  key: string;
  calls: number;
  failures: number;
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
}
interface UsageRow {
  id: number;
  occurredAt: string;
  feature: string;
  subject: string | null;
  username: string;
  mode: string;
  model: string | null;
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
  durationMs: number;
  outcome: string;
  detail: string | null;
}
interface AiAdminView {
  status: AiStatus;
  usage: {
    spentThisMonthUsd: number;
    monthlyBudgetUsd: number;
    callsThisMonth: number;
    callsToday: number;
    byFeature: Totals[];
    byUser: Totals[];
    recent: UsageRow[];
  };
  feedback: { feature: string; helpful: number; not_helpful: number }[];
  maskDomains: string[];
  maxLogKb: number;
  digestSchedule: string;
}

const FEATURE_LABEL: Record<string, string> = {
  'build-diagnosis': 'Build failure explainer',
  'executive-digest': 'Executive digest',
};

/** AI settings in effect (read-only: set through environment variables), budget, usage, feedback and recent calls. */
@Component({
  selector: 'app-admin-ai',
  imports: [
    DatePipe,
    DecimalPipe,
    MatIconModule,
    MatTooltipModule,
    KpiCard,
    LoadState,
    StatusChip,
    CardGroup,
  ],
  template: `
    <app-load-state
      [loading]="view.isLoading()"
      [error]="view.error()"
      what="AI usage"
      (retry)="view.reload()"
    />
    @if (view.hasValue()) {
      @let v = view.value();
      <section class="card settings">
        <div class="section-head">
          <div class="card-title">AI service</div>
          <app-status-chip
            [status]="
              v.status.mode === 'LIVE'
                ? v.status.configured
                  ? 'LIVE'
                  : 'ERROR'
                : v.status.mode === 'MOCK'
                  ? 'MOCK'
                  : 'LOW'
            "
            [text]="
              v.status.mode === 'OFF'
                ? 'Off'
                : v.status.mode === 'MOCK'
                  ? 'Mock (sample answers)'
                  : v.status.configured
                    ? 'Live'
                    : 'Live: not configured'
            "
          />
          <span class="spacer"></span>
          <button class="btn" type="button" (click)="view.reload()">
            <mat-icon>refresh</mat-icon>Refresh
          </button>
        </div>
        <dl class="kv">
          <dt>Model</dt>
          <dd>{{ v.status.label }}</dd>
          <dt>Masked before sending</dt>
          <dd>
            secrets, IP addresses, e-mail addresses, inventory hosts and hosts ending in
            {{ v.maskDomains.join(', ') }}
          </dd>
          <dt>Log analysed</dt>
          <dd>last {{ v.maxLogKb }} KB plus earlier error lines</dd>
          <dt>Daily limit per user</dt>
          <dd>{{ v.status.userDailyLimit }} requests</dd>
          <dt>Executive digest</dt>
          <dd class="mono">{{ v.digestSchedule }}</dd>
        </dl>
        <p class="faint">
          Settings come from environment variables (AI_MODE, AI_PROVIDER, AI_MODEL,
          AI_MONTHLY_BUDGET_USD, …); see docs/ai.md.
        </p>
      </section>

      <app-card-group
        heading="Usage this month"
        icon="data_usage"
        description="Estimated spend against the monthly budget, request volume, failures and how users rate the answers."
      >
        <section class="grid" aria-label="Usage this month">
          <app-kpi-card
            label="Estimated spend this month"
            icon="payments"
            unit="USD"
            [value]="(v.usage.spentThisMonthUsd | number: '1.2-2') ?? '0'"
            [hint]="'Budget ' + v.usage.monthlyBudgetUsd + ' USD · ' + budgetUsed() + '% used'"
            [tone]="budgetUsed() >= 90 ? 'bad' : budgetUsed() >= 70 ? 'warn' : 'ok'"
          />
          <app-kpi-card
            label="AI requests this month"
            icon="auto_awesome"
            [value]="v.usage.callsThisMonth"
            [hint]="v.usage.callsToday + ' today'"
          />
          <app-kpi-card
            label="Failed requests"
            icon="error"
            [value]="failures()"
            [tone]="failures() ? 'warn' : 'ok'"
            hint="this month"
          />
          <app-kpi-card
            label="Answers rated helpful"
            icon="thumb_up"
            [unit]="helpfulRate() === null ? '' : '%'"
            [value]="helpfulRate() ?? 'No ratings yet'"
            [hint]="votes() + (votes() === 1 ? ' rating' : ' ratings')"
            tone="info"
          />
        </section>
      </app-card-group>

      <section class="grid-2">
        <div class="card">
          <div class="card-title">By feature</div>
          <table class="data" aria-label="Usage by feature">
            <thead>
              <tr>
                <th>Feature</th>
                <th class="num">Requests</th>
                <th class="num">Failed</th>
                <th class="num">Tokens in / out</th>
                <th class="num">USD</th>
              </tr>
            </thead>
            <tbody>
              @for (t of v.usage.byFeature; track t.key) {
                <tr>
                  <td>{{ label(t.key) }}</td>
                  <td class="num">{{ t.calls }}</td>
                  <td class="num">{{ t.failures }}</td>
                  <td class="num">{{ t.inputTokens | number }} / {{ t.outputTokens | number }}</td>
                  <td class="num">{{ t.costUsd | number: '1.2-4' }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="faint">No AI requests this month.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="card">
          <div class="card-title">Top users</div>
          <table class="data" aria-label="Usage by user">
            <thead>
              <tr>
                <th>User</th>
                <th class="num">Requests</th>
                <th class="num">USD</th>
              </tr>
            </thead>
            <tbody>
              @for (t of v.usage.byUser; track t.key) {
                <tr>
                  <td>{{ t.key }}</td>
                  <td class="num">{{ t.calls }}</td>
                  <td class="num">{{ t.costUsd | number: '1.2-4' }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="3" class="faint">No AI requests this month.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <section class="card">
        <div class="card-title">Recent requests</div>
        <div class="table-wrap">
          <table class="data" aria-label="Recent AI requests">
            <thead>
              <tr>
                <th>When</th>
                <th>User</th>
                <th>Feature</th>
                <th>Subject</th>
                <th>Outcome</th>
                <th class="num">Tokens in / out</th>
                <th class="num">Seconds</th>
                <th class="num">USD</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              @for (r of v.usage.recent; track r.id) {
                <tr>
                  <td>{{ r.occurredAt | date: 'dd MMM, HH:mm:ss' }}</td>
                  <td>{{ r.username }}</td>
                  <td>
                    {{ label(r.feature) }}
                    <span class="muted">{{ r.mode === 'LIVE' ? '' : '(sample)' }}</span>
                  </td>
                  <td class="mono wrap">{{ r.subject ?? '—' }}</td>
                  <td>
                    <app-status-chip
                      [status]="r.outcome === 'SUCCESS' ? 'OK' : 'ERROR'"
                      [text]="r.outcome === 'SUCCESS' ? 'Answered' : 'Failed'"
                    />
                  </td>
                  <td class="num">{{ r.inputTokens | number }} / {{ r.outputTokens | number }}</td>
                  <td class="num">{{ r.durationMs / 1000 | number: '1.1-1' }}</td>
                  <td class="num">{{ r.costUsd | number: '1.2-4' }}</td>
                  <td class="wrap muted">{{ r.detail ?? '' }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="9" class="faint">No AI requests yet.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  styles: `
    .settings {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .section-head {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .kv {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 6px 18px;
      margin: 0;
      font-size: 13.5px;
    }
    .kv dt {
      color: var(--app-muted);
    }
    .kv dd {
      margin: 0;
    }
    .grid-2 {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 20px;
    }
    @media (max-width: 900px) {
      .grid-2 {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class AdminAiPage {
  protected readonly view = httpResource<AiAdminView>(() => apiUrl('admin/ai'));

  protected readonly budgetUsed = computed(() => {
    const u = this.view.value()?.usage;
    return u && u.monthlyBudgetUsd > 0
      ? Math.round((u.spentThisMonthUsd / u.monthlyBudgetUsd) * 100)
      : 0;
  });
  protected readonly failures = computed(() =>
    (this.view.value()?.usage.byFeature ?? []).reduce((s, t) => s + t.failures, 0),
  );
  protected readonly votes = computed(() =>
    (this.view.value()?.feedback ?? []).reduce(
      (s, f) => s + Number(f.helpful) + Number(f.not_helpful),
      0,
    ),
  );
  protected readonly helpfulRate = computed(() => {
    const total = this.votes();
    const up = (this.view.value()?.feedback ?? []).reduce((s, f) => s + Number(f.helpful), 0);
    return total ? Math.round((up / total) * 100) : null;
  });

  protected label(feature: string): string {
    return FEATURE_LABEL[feature] ?? feature;
  }
}
