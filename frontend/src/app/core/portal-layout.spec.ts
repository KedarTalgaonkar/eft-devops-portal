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
    user: {
      username: 'priya.k',
      displayName: 'Priya Kulkarni',
      email: null,
      roles,
      source: 'LOCAL',
      mustChangePassword: false,
    },
    providers: { ssoEnabled: true, ssoLabel: 'Euronet SSO', localEnabled: true },
  });
  await loaded;

  const fixture = TestBed.createComponent(PortalLayout);
  fixture.detectChanges();
  const soon = new Date(Date.now() + 3_600_000).toISOString();
  http.expectOne('/api/announcements/active').flush([
    {
      id: 1,
      kind: 'RELEASE',
      message: 'IMPS 2.4 release to UAT tonight',
      linkUrl: '/pipelines/deployments',
      linkLabel: 'Details',
      startsAt: new Date().toISOString(),
      endsAt: soon,
      status: 'ACTIVE',
      createdBy: 'release-mgr',
      createdAt: new Date().toISOString(),
      updatedBy: null,
      updatedAt: null,
    },
    {
      id: 2,
      kind: 'INCIDENT',
      message: 'SIT switch is down',
      linkUrl: null,
      linkLabel: null,
      startsAt: new Date().toISOString(),
      endsAt: soon,
      status: 'ACTIVE',
      createdBy: 'oncall',
      createdAt: new Date().toISOString(),
      updatedBy: null,
      updatedAt: null,
    },
  ]);
  http
    .match('brand/brand.json')
    .forEach((r) =>
      r.flush({ logo: '', logoAlt: 'Euronet', region: 'India', product: 'EFT DevOps Portal' }),
    );
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('PortalLayout', () => {
  it('shows module navigation, live announcements and the signed-in user', async () => {
    const el = await render(['VIEWER']);
    const visible = NAV_GROUPS.filter((g) => !g.requires).flatMap((g) => g.items);
    expect(el.querySelectorAll('nav a.item').length).toBe(visible.length);
    expect(el.textContent).toContain('Pipelines');
    expect(el.textContent).not.toContain('Users & audit log');
    const items = el.querySelectorAll('app-announcement-bar .item');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('IMPS 2.4 release to UAT tonight');
    expect(items[0].querySelector('button.close')).not.toBeNull();
    // incidents cannot be hidden
    expect(items[1].querySelector('button.close')).toBeNull();
    expect(el.querySelector('.user')?.textContent).toContain('Priya Kulkarni');
    expect(el.querySelector('.user')?.textContent).toContain('Viewer');
  });

  it('adds the administration group for administrators', async () => {
    const el = await render(['ADMIN']);
    expect(el.textContent).toContain('Users & audit log');
    expect(el.querySelector('.user')?.textContent).toContain('Administrator');
  });
});
