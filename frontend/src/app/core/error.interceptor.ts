import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { errorMessage } from './api';
import { Auth } from './auth';

/**
 * - 401 on any API call (except the session probe and the login form) = session ended: back to the sign-in page.
 * - 403 PASSWORD_CHANGE_REQUIRED: go to the change-password page.
 * - other failed changes (POST/PUT/DELETE) show a snackbar; failed reads are shown next to the data on the page.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  const auth = inject(Auth);
  const router = inject(Router);
  const authCall = req.url.startsWith('/api/auth/session') || req.url.startsWith('/api/auth/login');

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        if (err.status === 401 && !authCall) {
          auth.sessionExpired();
          return throwError(() => err);
        }
        if (err.status === 403 && (err.error as { code?: string } | null)?.code === 'PASSWORD_CHANGE_REQUIRED') {
          void router.navigate(['/account/password']);
          return throwError(() => err);
        }
      }
      if (req.method !== 'GET' && !authCall) {
        snackBar.open(errorMessage(err), 'Dismiss', { duration: 8000 });
      }
      return throwError(() => err);
    }),
  );
};
