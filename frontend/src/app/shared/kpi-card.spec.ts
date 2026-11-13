import { TestBed } from '@angular/core/testing';
import { KpiCard } from './kpi-card';

describe('KpiCard change badge', () => {
  function render(inputs: Record<string, unknown>): HTMLElement {
    const fixture = TestBed.createComponent(KpiCard);
    fixture.componentRef.setInput('label', 'Test');
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a green rise when higher is better', () => {
    const el = render({ value: 90, current: 90, previous: 80, deltaMode: 'pts' });
    const delta = el.querySelector('.delta')!;
    expect(delta.textContent).toContain('▲ 10 pts');
    expect(delta.classList).toContain('good');
  });

  it('shows a rise as bad when lower is better (e.g. time to recover)', () => {
    const el = render({ value: 6, current: 6, previous: 4, higherIsBetter: false });
    const delta = el.querySelector('.delta')!;
    expect(delta.textContent).toContain('▲ 50%');
    expect(delta.classList).toContain('bad');
  });

  it('hides the badge without a previous period', () => {
    expect(render({ value: 5, current: 5, previous: null }).querySelector('.delta')).toBeNull();
  });
});
