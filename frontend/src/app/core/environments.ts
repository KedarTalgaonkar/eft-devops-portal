import { computed, Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { apiUrl } from './api';

export interface EnvironmentView {
  code: string;
  name: string;
  sortOrder: number;
  category: 'PROD' | 'NON_PROD';
  active: boolean;
}

/**
 * The environment catalogue from the backend. Every filter, column and grid renders from this list,
 * so a new environment (added by an Excel import) appears everywhere without a frontend change.
 */
@Injectable({ providedIn: 'root' })
export class Environments {
  readonly all = httpResource<EnvironmentView[]>(() => apiUrl('environments'), {
    defaultValue: [],
  });
  readonly active = computed(() => this.all.value().filter((e) => e.active));
  readonly codes = computed(() => this.active().map((e) => e.code));

  name(code: string): string {
    return this.all.value().find((e) => e.code === code)?.name ?? code;
  }

  isProd(code: string): boolean {
    return this.all.value().find((e) => e.code === code)?.category === 'PROD';
  }

  reload(): void {
    this.all.reload();
  }
}
