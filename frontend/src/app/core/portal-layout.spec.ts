import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Auth, PortalRole } from './auth';
import { NAV_GROUPS } from './navigation';
import { PortalLayout } from './portal-layout';

async function render(roles: PortalRole[]) {
  TestBed.configureTestingModule({
    imports: [PortalLayout],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const http = TestBed.inject(HttpTestingController);
  const auth = TestBed.inject(Auth);
  const loaded = auth.load();
  http.expectOne('/api/auth/session').flush({
    authenticated: true,
    user: { username: 'priya.k', displayName: 'Priya Kulkarni', email: null, roles, source: 'LOCAL', mustChangePassword: false },
    providers: { ssoEnabled: true, ssoLabel: 'Euronet SSO', localEnabled: true },
  });
  await loaded;

  const fixture = TestBed.createComponent(PortalLayout);
  fixture.detectChanges();
  http
    .expectOne('/api/dashboard/integrations')
    .flush([{ key: 'jenkins', name: 'Jenkins', mode: 'MOCK', reachable: true, endpoint: null, message: null }]);
  http.match('brand/brand.json').forEach((r) => r.flush({ logo: '', logoAlt: 'Euronet', region: 'India', product: 'EFT DevOps Portal' }));
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('PortalLayout', () => {
  it('shows module navigation, the data mode and the signed-in user', async () => {
    const el = await render(['VIEWER']);
    const visible = NAV_GROUPS.filter((g) => !g.requires).flatMap((g) => g.items);
    expect(el.querySelectorAll('nav a.item').length).toBe(visible.length);
    expect(el.textContent).toContain('Pipelines');
    expect(el.textContent).not.toContain('Users & audit log');
    expect(el.querySelector('.mode')?.textContent).toContain('Sample data');
    expect(el.querySelector('.user')?.textContent).toContain('Priya Kulkarni');
    expect(el.querySelector('.user')?.textContent).toContain('Viewer');
  });

  it('adds the administration group for administrators', async () => {
    const el = await render(['ADMIN']);
    expect(el.textContent).toContain('Users & audit log');
    expect(el.querySelector('.user')?.textContent).toContain('Administrator');
  });
});
