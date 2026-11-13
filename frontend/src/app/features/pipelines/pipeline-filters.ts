import { Injectable, signal } from '@angular/core';

/** Environment and period shared by all Pipelines tabs (provided on the /pipelines route). */
@Injectable()
export class PipelineFilters {
  readonly env = signal('');
  readonly days = signal(30);
  readonly dayOptions = [7, 14, 30, 60, 90];
}
