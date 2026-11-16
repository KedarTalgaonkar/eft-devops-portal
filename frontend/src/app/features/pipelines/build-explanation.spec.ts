import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BuildExplanation } from '../../core/ai';
import { BuildExplanationPanel } from './build-explanation';

const ANSWER: BuildExplanation = {
  job: 'EFT/prepaid-qr-SIT',
  buildNumber: 412,
  logBytes: 3_500_000,
  logTruncated: true,
  similar: [
    {
      buildNumber: 405,
      summary: 'Deployment to Tomcat failed.',
      explainedAt: '2026-11-10T08:00:00Z',
    },
  ],
  result: {
    id: 7,
    feature: 'build-diagnosis',
    subject: 'EFT/prepaid-qr-SIT#412',
    mode: 'MOCK',
    model: 'sample',
    masking: '1 IP address, 1 secret masked',
    createdAt: '2026-11-14T08:00:00Z',
    createdBy: 'priya',
    helpful: 2,
    notHelpful: 0,
    myVote: null,
    content: {
      category: 'DEPLOYMENT',
      summary: 'Deployment to Tomcat failed: the server did not accept the connection.',
      likelyCause: 'Tomcat on the target host is down.',
      evidence: ['curl: (7) Failed to connect to 192.0.2.41 port 9091'],
      fixSteps: ['Start Tomcat', 'Retry the deploy stage'],
      owner: 'MIDDLEWARE',
      confidence: 'HIGH',
      notACodeProblem: true,
    },
  },
};

describe('BuildExplanationPanel', () => {
  it('asks for the explanation and shows cause, evidence, fix steps and provenance', async () => {
    TestBed.configureTestingModule({
      imports: [BuildExplanationPanel],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(BuildExplanationPanel);
    fixture.componentRef.setInput('applicationId', 3);
    fixture.componentRef.setInput('environment', 'SIT');
    fixture.componentRef.setInput('buildNumber', 412);
    fixture.detectChanges();

    const req = TestBed.inject(HttpTestingController).expectOne('/api/ai/builds/3/SIT/412/explain');
    expect(req.request.method).toBe('POST');
    req.flush(ANSWER);
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Deployment');
    expect(text).toContain('Tomcat on the target host is down.');
    expect(text).toContain('192.0.2.41');
    expect(text).toContain('Retry the deploy stage');
    expect(text).toContain('Probably not a code problem');
    expect(text).toContain('#405');
    expect(text).toContain('AI-generated');
    expect(text).toContain('1 IP address, 1 secret masked');
    // viewers do not see "Analyse again"
    expect(text).not.toContain('Analyse again');
  });

  it('shows the backend message when the request fails', async () => {
    TestBed.configureTestingModule({
      imports: [BuildExplanationPanel],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(BuildExplanationPanel);
    fixture.componentRef.setInput('applicationId', 3);
    fixture.componentRef.setInput('environment', 'SIT');
    fixture.componentRef.setInput('buildNumber', 412);
    fixture.detectChanges();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/ai/builds/3/SIT/412/explain')
      .flush(
        { status: 429, message: "You have reached today's limit of 50 AI requests." },
        { status: 429, statusText: 'Too Many Requests' },
      );
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      "today's limit of 50 AI requests",
    );
  });
});
