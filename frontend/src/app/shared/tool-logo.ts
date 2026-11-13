import { Component, computed, input } from '@angular/core';
import {
  siApachetomcat,
  siDocker,
  siGithub,
  siGrafana,
  siJenkins,
  siJfrog,
  siOpenjdk,
  siPostgresql,
  siPrometheus,
  siRedhatopenshift,
  siSonarqubeserver,
  siSpringboot,
} from 'simple-icons';

/** Brand marks of the tools the portal integrates with (simple-icons, CC0), bundled locally: no external requests. */
const LOGOS = {
  jenkins: siJenkins,
  github: siGithub,
  sonarqube: siSonarqubeserver,
  jfrog: siJfrog,
  artifactory: siJfrog,
  grafana: siGrafana,
  prometheus: siPrometheus,
  tomcat: siApachetomcat,
  java: siOpenjdk,
  postgres: siPostgresql,
  spring: siSpringboot,
  docker: siDocker,
  openshift: siRedhatopenshift,
} as const;

export type ToolName = keyof typeof LOGOS;

@Component({
  selector: 'app-tool-logo',
  template: `
    <span class="logo" [class.tile]="tile()" [style.width.px]="size()" [style.height.px]="size()">
      <svg viewBox="0 0 24 24" role="img" [attr.aria-label]="icon().title" [style.width.px]="inner()" [style.height.px]="inner()">
        <title>{{ icon().title }}</title>
        <path [attr.d]="icon().path" [attr.fill]="color()" />
      </svg>
    </span>
  `,
  styles: `
    :host { display: inline-flex; }
    .logo { display: inline-flex; align-items: center; justify-content: center; flex: none; }
    .tile { background: #fff; border: 1px solid var(--app-line); border-radius: 10px; box-shadow: var(--shadow); }
  `,
})
export class ToolLogo {
  readonly name = input.required<ToolName>();
  readonly size = input(28);
  /** White rounded tile behind the mark (for use on navy backgrounds). */
  readonly tile = input(false);
  /** Override the brand colour (e.g. 'currentColor'). */
  readonly mono = input<string | null>(null);

  protected readonly icon = computed(() => LOGOS[this.name()]);
  protected readonly color = computed(() => {
    if (this.mono()) {
      return this.mono();
    }
    const hex = this.icon().hex;
    // very dark brand colours (GitHub, OpenJDK) stay readable on both light tiles and the navy hero
    if (hex === '000000' || hex === '181717') {
      return '#1b1f24';
    }
    // Tomcat's pale yellow has too little contrast on white: use a deeper shade of the same hue
    return hex === 'F8DC75' ? '#b8860b' : `#${hex}`;
  });
  protected readonly inner = computed(() => Math.round(this.size() * (this.tile() ? 0.6 : 1)));
}
