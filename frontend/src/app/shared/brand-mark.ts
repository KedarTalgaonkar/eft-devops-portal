import { Component, computed, input, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';

interface BrandConfig {
  logo: string;
  logoAlt: string;
  region: string;
  product: string;
}

const DEFAULTS: BrandConfig = { logo: '', logoAlt: 'Euronet', region: 'India', product: 'EFT DevOps Portal' };

/**
 * Euronet India brand lockup, driven by public/brand/brand.json. Put the official logo file in public/brand/
 * and set "logo" to its path (e.g. "brand/euronet-logo.svg"); until then a text wordmark is shown.
 */
@Component({
  selector: 'app-brand-mark',
  template: `
    <span class="brand">
      @if (logo() && !failed()) {
        <img [src]="logo()" [alt]="config().logoAlt" class="logo" (error)="failed.set(true)" />
      } @else {
        <span class="wordmark" [attr.aria-label]="config().logoAlt">{{ config().logoAlt.toLowerCase() }}</span>
      }
      @if (!compact()) {
        <span class="sub">
          <span class="region">{{ config().region }}</span>
          <span class="product">{{ config().product }}</span>
        </span>
      }
    </span>
  `,
  styles: `
    .brand { display: inline-flex; align-items: center; gap: 12px; color: #fff; }
    .logo { height: 30px; width: auto; display: block; }
    .wordmark { font-weight: 700; font-size: 24px; letter-spacing: -0.03em; line-height: 1; }
    .wordmark::first-letter { color: var(--eu-teal); }
    .sub { display: flex; flex-direction: column; line-height: 1.15; border-left: 1px solid rgba(255,255,255,.25); padding-left: 12px; }
    .region { font-size: 11px; font-weight: 600; letter-spacing: .14em; text-transform: uppercase; color: var(--eu-saffron); }
    .product { font-size: 13px; font-weight: 500; color: rgba(255,255,255,.9); }
  `,
})
export class BrandMark {
  readonly compact = input(false);
  private readonly brand = httpResource<BrandConfig>(() => 'brand/brand.json', { defaultValue: DEFAULTS });
  protected readonly config = computed(() => ({ ...DEFAULTS, ...this.brand.value() }));
  protected readonly logo = computed(() => this.config().logo);
  protected readonly failed = signal(false);
}
