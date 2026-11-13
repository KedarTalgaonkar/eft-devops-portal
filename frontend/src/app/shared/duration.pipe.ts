import { Pipe, PipeTransform } from '@angular/core';

/** 754 -> "12m 34s", 4000 -> "1h 6m". */
@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(seconds: number | null | undefined): string {
    if (seconds === null || seconds === undefined) {
      return '—';
    }
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    if (h > 0) {
      return `${h}h ${m}m`;
    }
    return m > 0 ? `${m}m ${s}s` : `${s}s`;
  }
}
