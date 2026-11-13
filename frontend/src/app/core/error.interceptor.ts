import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { errorMessage } from './api';

/**
 * Shows a snackbar when a change (POST/PUT/DELETE) fails. Failed reads are shown by the page itself,
 * next to the data that could not load.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (req.method !== 'GET') {
        snackBar.open(errorMessage(err), 'Dismiss', { duration: 8000 });
      }
      return throwError(() => err);
    }),
  );
};
