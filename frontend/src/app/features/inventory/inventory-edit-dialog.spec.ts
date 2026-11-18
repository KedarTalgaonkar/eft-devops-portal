import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { InventoryEditData, InventoryEditDialog } from './inventory-edit-dialog';

async function render(data: InventoryEditData) {
  const close = vi.fn();
  TestBed.configureTestingModule({
    imports: [InventoryEditDialog],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: { close } },
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(InventoryEditDialog);
  fixture.detectChanges();
  http.match('/api/environments').forEach((r) =>
    r.flush([
      { code: 'SIT', name: 'SIT', sortOrder: 20, category: 'NON_PROD', active: true },
      { code: 'UAT', name: 'UAT', sortOrder: 30, category: 'NON_PROD', active: true },
    ]),
  );
  await fixture.whenStable();
  fixture.detectChanges();
  return { el: fixture.nativeElement as HTMLElement, http, close, fixture };
}

function type(el: HTMLElement, selector: string, value: string) {
  const input = el.querySelector(selector) as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('InventoryEditDialog', () => {
  it('adds an application and returns its id', async () => {
    const { el, http, close, fixture } = await render({ kind: 'application' });
    const save = el.querySelector('button[type=submit]') as HTMLButtonElement;
    fixture.detectChanges();
    expect(save.disabled).toBe(true); // name is required

    type(el, 'input[placeholder="e.g. IMPS Portal"]', 'IMPS Portal');
    type(el, 'input[placeholder="org/repo, org/other"]', 'eft/imps, eft/common');
    fixture.detectChanges();
    expect(save.disabled).toBe(false);
    save.click();

    const req = http.expectOne('/api/inventory/applications');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'IMPS Portal', client: '', owner: null, repositories: ['eft/imps', 'eft/common'] });
    req.flush({ id: 42 });
    expect(close).toHaveBeenCalledWith({ message: 'IMPS Portal added', applicationId: 42 });
  });

  it('refuses a malformed base URL before sending', async () => {
    const apps = signal([{ id: 7, name: 'Recon', client: 'Euronet', owner: null, deploymentCount: 0, environments: [], repositories: [], updatedAt: '' }]);
    const { el, fixture } = await render({ kind: 'path', applicationId: 7, applications: apps });
    type(el, 'input[placeholder="http://host:port"]', 'javascript:alert(1)');
    fixture.detectChanges();
    expect(el.textContent).toContain('Use http://host:port');
    expect((el.querySelector('button[type=submit]') as HTMLButtonElement).disabled).toBe(true);
  });
});
