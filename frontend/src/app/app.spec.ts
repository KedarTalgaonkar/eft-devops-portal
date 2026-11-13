import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { NAV_ITEMS } from './core/navigation';

describe('App shell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('renders one menu entry per module, grouped, and shows the data mode', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne('/api/dashboard/integrations')
      .flush([{ key: 'jenkins', name: 'Jenkins', mode: 'MOCK', reachable: true, endpoint: null, message: null }]);
    http.expectOne('brand/brand.json').flush({ logo: '', logoAlt: 'Euronet', region: 'India', product: 'EFT DevOps Portal' });
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('nav a.item').length).toBe(NAV_ITEMS.length);
    expect(el.querySelectorAll('.group-label')[1].textContent).toContain('Delivery');
    expect(el.textContent).toContain('Pipelines');
    expect(el.querySelector('.mode')?.textContent).toContain('Sample data');
  });
});
