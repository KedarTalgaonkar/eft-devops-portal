import { ApplicationConfig, LOCALE_ID, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEnIn from '@angular/common/locales/en-IN';
import { provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { MatIconRegistry } from '@angular/material/icon';
import { MAT_TOOLTIP_DEFAULT_OPTIONS, MatTooltipDefaultOptions } from '@angular/material/tooltip';
import { routes } from './app.routes';
import { errorInterceptor } from './core/error.interceptor';

// Indian English: dates (14 Nov 2026), lakh / crore digit grouping (1,00,000), Indian week conventions
registerLocaleData(localeEnIn);

const tooltipDefaults: MatTooltipDefaultOptions = {
  showDelay: 250,
  hideDelay: 0,
  touchendHideDelay: 1500,
  disableTooltipInteractivity: true,
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withFetch(),
      // CSRF: echo the XSRF-TOKEN cookie set by the backend in the X-XSRF-TOKEN header of every change request
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([errorInterceptor]),
    ),
    { provide: LOCALE_ID, useValue: 'en-IN' },
    { provide: MAT_TOOLTIP_DEFAULT_OPTIONS, useValue: tooltipDefaults },
    // Icons come from the self-hosted "material-symbols" package (no Google Fonts call)
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
    }),
  ],
};
