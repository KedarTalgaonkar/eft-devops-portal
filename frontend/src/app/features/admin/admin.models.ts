import { PortalRole } from '../../core/auth';

export interface LocalUserView {
  id: number;
  username: string;
  displayName: string;
  email: string | null;
  roles: PortalRole[];
  enabled: boolean;
  mustChangePassword: boolean;
  locked: boolean;
  lockedUntil: string | null;
  failedAttempts: number;
  lastLoginAt: string | null;
  passwordChangedAt: string | null;
  createdAt: string;
  createdBy: string | null;
}

export interface AuditView {
  id: number;
  occurredAt: string;
  username: string | null;
  authSource: string | null;
  action: string;
  target: string | null;
  outcome: string;
  ipAddress: string | null;
  userAgent: string | null;
  detail: string | null;
}

/** Strong random temporary password that meets the policy (admin hands it over out of band). */
export function generateTemporaryPassword(): string {
  const sets = ['ABCDEFGHJKLMNPQRSTUVWXYZ', 'abcdefghijkmnopqrstuvwxyz', '23456789', '!@#$%*-_+='];
  const all = sets.join('');
  const random = (n: number) => {
    const buf = new Uint32Array(1);
    crypto.getRandomValues(buf);
    return buf[0] % n;
  };
  const chars = sets.map((s) => s[random(s.length)]);
  while (chars.length < 16) {
    chars.push(all[random(all.length)]);
  }
  for (let i = chars.length - 1; i > 0; i--) {
    const j = random(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join('');
}
