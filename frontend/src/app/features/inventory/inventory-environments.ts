import { Component, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { EnvironmentView, Environments } from '../../core/environments';
import { LoadState } from '../../shared/load-state';

/**
 * Environment catalogue. New environments are registered automatically by the Excel import; here they can
 * be renamed, reordered, marked as production (raises issue severity) or hidden from filters.
 */
@Component({
  selector: 'app-inventory-environments',
  imports: [LoadState],
  template: `
    <div class="card intro">
      <strong>Environments are data, not code.</strong>
      <span class="muted">Add a column group such as "PERF", "Pre-Prod" or "PROD" to the inventory workbook and import it: the environment
        is registered here and appears in every filter, grid and chart. Production environments raise issue severity.</span>
    </div>
    <app-load-state [loading]="envs.all.isLoading()" [error]="envs.all.error()" what="environments" (retry)="envs.reload()" />
    <div class="table-wrap">
      <table class="data" aria-label="Environments">
        <thead><tr><th>Code</th><th>Display name</th><th class="num">Order</th><th>Category</th><th>Shown in filters</th></tr></thead>
        <tbody>
          @for (e of envs.all.value(); track e.code) {
            <tr>
              <td class="mono"><strong>{{ e.code }}</strong></td>
              <td><input class="cell-input" [value]="e.name" [attr.aria-label]="'Name of ' + e.code" (change)="save(e, { name: $any($event.target).value })" /></td>
              <td class="num"><input class="cell-input num-input" type="number" [value]="e.sortOrder" [attr.aria-label]="'Order of ' + e.code"
                                     (change)="save(e, { sortOrder: +$any($event.target).value })" /></td>
              <td>
                <select class="select" [value]="e.category" [attr.aria-label]="'Category of ' + e.code" (change)="save(e, { category: $any($event.target).value })">
                  <option value="NON_PROD">Non-production</option><option value="PROD">Production</option>
                </select>
              </td>
              <td><input type="checkbox" [checked]="e.active" [attr.aria-label]="'Show ' + e.code" (change)="save(e, { active: $any($event.target).checked })" /></td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .intro { display: flex; flex-direction: column; gap: 4px; }
    .cell-input { border: 1px solid var(--app-line-strong); border-radius: 8px; padding: 6px 8px; font: inherit; }
    .num-input { width: 80px; text-align: right; }
  `,
})
export class InventoryEnvironmentsPage {
  private readonly http = inject(HttpClient);
  protected readonly envs = inject(Environments);

  protected save(env: EnvironmentView, change: Partial<EnvironmentView>): void {
    this.http.put(apiUrl(`environments/${encodeURIComponent(env.code)}`), change).subscribe(() => this.envs.reload());
  }
}
