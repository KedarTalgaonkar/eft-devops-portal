import { passwordChecks } from '../features/auth/password-policy';
import { safeReturnTo } from './auth';

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

describe('passwordChecks', () => {
  const ok = (pw: string, user = 'priya') => passwordChecks(pw, user).every((c) => c.ok);

  it('accepts a long mixed password', () => expect(ok('Monsoon-Tiffin-2026')).toBe(true));
  it('rejects short passwords', () => expect(ok('Ab1!short')).toBe(false));
  it('rejects passwords containing the username', () =>
    expect(ok('Priya-Strong-2026!')).toBe(false));
  it('rejects repeated characters', () => expect(ok('Aaaaa-Strong-2026')).toBe(false));
  it('needs three character classes', () => expect(ok('alllowercaseletters')).toBe(false));
});
