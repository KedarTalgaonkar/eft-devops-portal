import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { passwordChecks } from '../features/auth/password-policy';
import { Auth, AuthSession, PortalRole, safeReturnTo } from './auth';

describe('safeReturnTo', () => {
  it('keeps same-origin relative paths', () => {
    expect(safeReturnTo('/pipelines?env=SIT')).toBe('/pipelines?env=SIT');
  });

  it.each([
    'https://evil.example',
    '//evil.example',
    '/\\evil.example',
    '/login',
    '/api/auth/logout',
    'pipelines',
    '',
    null,
    undefined,
  ])('falls back to the root for %s', (path) => expect(safeReturnTo(path)).toBe('/'));
});

describe('Auth module access', () => {
  async function signIn(roles: PortalRole[], modules?: AuthSession['modules']) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const auth = TestBed.inject(Auth);
    const loaded = auth.load();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/session').flush({
      authenticated: true,
      user: { username: 'u', displayName: 'U', email: null, roles, source: 'SSO', mustChangePassword: false },
      providers: { ssoEnabled: true, ssoLabel: 'SSO', localEnabled: false },
      modules,
    });
    await loaded;
    return auth;
  }

  it('follows the access groups sent by the backend', async () => {
    const auth = await signIn(['EDITOR'], { PIPELINES: 'NONE', INVENTORY: 'EDIT', QUALITY: 'VIEW' });
    expect(auth.canView('PIPELINES')).toBe(false);
    expect(auth.canEdit('INVENTORY')).toBe(true);
    expect(auth.canView('QUALITY')).toBe(true);
    expect(auth.canEdit('QUALITY')).toBe(false);
  });

  it('falls back to the role when no group data is sent', async () => {
    const auth = await signIn(['VIEWER']);
    expect(auth.canView('PIPELINES')).toBe(true);
    expect(auth.canEdit('PIPELINES')).toBe(false);
  });
});

describe('passwordChecks', () => {
  const ok = (pw: string, user = 'priya') => passwordChecks(pw, user).every((c) => c.ok);

  it('accepts a long mixed password', () => expect(ok('Monsoon-Tiffin-2026')).toBe(true));
  it('rejects short passwords', () => expect(ok('Ab1!short')).toBe(false));
  it('rejects passwords containing the username', () =>
    expect(ok('Priya-Strong-2026!')).toBe(false));
  it('rejects repeated characters', () => expect(ok('Aaaaa-Strong-2026')).toBe(false));
  it('needs three character classes', () => expect(ok('alllowercaseletters')).toBe(false));
});
