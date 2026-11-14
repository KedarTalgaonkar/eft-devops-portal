import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth, PortalRole } from './auth';

/** Signed-in users only; a temporary password must be changed first. */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(Auth);
  const router = inject(Router);
  const session = await auth.load().catch(() => null);
  if (!session?.authenticated) {
    return router.createUrlTree(['/login'], { queryParams: { returnTo: state.url } });
  }
  if (session.user?.mustChangePassword) {
    return router.createUrlTree(['/account/password'], { queryParams: { returnTo: state.url } });
  }
  return true;
};

/** The change-password page: signed in, any password state. */
export const signedInGuard: CanActivateFn = async () => {
  const auth = inject(Auth);
  const session = await auth.load().catch(() => null);
  return session?.authenticated ? true : inject(Router).createUrlTree(['/login']);
};

/** Role check for whole sections (e.g. administration). The backend enforces the same rule on every call. */
export function roleGuard(role: PortalRole): CanActivateFn {
  return async () => {
    const auth = inject(Auth);
    await auth.load().catch(() => null);
    return auth.can(role) ? true : inject(Router).createUrlTree(['/dashboard']);
  };
}
