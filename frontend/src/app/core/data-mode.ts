import { Injectable, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { apiUrl } from './api';
import { IntegrationStatus } from './models';

/**
 * Whether the numbers on screen come from the real tools or from built-in sample data. Each integration
 * (Jenkins, GitHub, SonarQube, ...) runs in mock mode until its URL and token are configured, so leadership must
 * be able to tell sample figures from live ones.
 */
@Injectable({ providedIn: 'root' })
export class DataMode {
  readonly integrations = httpResource<IntegrationStatus[]>(() => apiUrl('dashboard/integrations'), { defaultValue: [] });

  readonly live = computed(() => this.integrations.value().filter((i) => i.mode === 'LIVE'));
  readonly sample = computed(() => this.integrations.value().filter((i) => i.mode !== 'LIVE'));

  /** null until loaded; "Live data", "Sample data" or "3 of 7 sources live". */
  readonly label = computed(() => {
    const all = this.integrations.value();
    if (!all.length) {
      return null;
    }
    const live = this.live().length;
    return live === 0 ? 'Sample data' : live === all.length ? 'Live data' : `${live} of ${all.length} sources live`;
  });

  readonly explanation = computed(() => {
    const sample = this.sample().map((i) => i.name);
    if (!sample.length) {
      return 'All figures come from the connected tools.';
    }
    return `${sample.join(', ')} ${sample.length === 1 ? 'is' : 'are'} not connected yet, so ${sample.length === 1 ? 'its' : 'their'} figures are generated sample data. `
      + 'An administrator connects a tool by setting its URL and token. Click for details.';
  });
}
