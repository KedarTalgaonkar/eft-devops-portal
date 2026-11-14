import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Root: either the sign-in pages or the signed-in portal layout (see app.routes.ts). */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class App {}
