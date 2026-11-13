import { Routes } from '@angular/router';
import { PipelineFilters } from './features/pipelines/pipeline-filters';

const T = ' · EFT DevOps · Euronet India';

/**
 * Every module = a shell (hero + tabs) with child routes. The first tab is the executive overview,
 * the others are detail views for support teams. Tabs have their own URLs so they can be shared.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard-shell').then((m) => m.DashboardShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', title: 'Command centre' + T, loadComponent: () => import('./features/dashboard/dashboard-overview').then((m) => m.DashboardOverviewPage) },
      { path: 'issues', title: 'Issues' + T, loadComponent: () => import('./features/dashboard/dashboard-issues').then((m) => m.DashboardIssuesPage) },
      { path: 'environments', title: 'Environment status' + T, loadComponent: () => import('./features/dashboard/dashboard-environments').then((m) => m.DashboardEnvironmentsPage) },
      { path: 'services', title: 'Service health' + T, loadComponent: () => import('./features/dashboard/dashboard-services').then((m) => m.DashboardServicesPage) },
      { path: 'integrations', title: 'Integrations' + T, loadComponent: () => import('./features/dashboard/dashboard-integrations').then((m) => m.DashboardIntegrationsPage) },
    ],
  },
  {
    path: 'pipelines',
    providers: [PipelineFilters],
    loadComponent: () => import('./features/pipelines/pipelines-shell').then((m) => m.PipelinesShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', title: 'Delivery overview' + T, loadComponent: () => import('./features/pipelines/pipelines-overview').then((m) => m.PipelinesOverviewPage) },
      { path: 'all', title: 'Pipeline metrics' + T, loadComponent: () => import('./features/pipelines/pipelines-all').then((m) => m.PipelinesAllPage) },
      { path: 'deployments', title: 'Deployment history' + T, loadComponent: () => import('./features/pipelines/pipelines-deployments').then((m) => m.PipelinesDeploymentsPage) },
    ],
  },
  {
    path: 'inventory',
    loadComponent: () => import('./features/inventory/inventory-shell').then((m) => m.InventoryShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', title: 'Inventory overview' + T, loadComponent: () => import('./features/inventory/inventory-overview').then((m) => m.InventoryOverviewPage) },
      { path: 'deployments', title: 'Deployment paths' + T, loadComponent: () => import('./features/inventory/inventory-deployments').then((m) => m.InventoryDeploymentsPage) },
      { path: 'changes', title: 'Change log' + T, loadComponent: () => import('./features/inventory/inventory-changes').then((m) => m.InventoryChangesPage) },
      { path: 'environments', title: 'Environments' + T, loadComponent: () => import('./features/inventory/inventory-environments').then((m) => m.InventoryEnvironmentsPage) },
    ],
  },
  {
    path: 'github',
    loadComponent: () => import('./features/github/github-shell').then((m) => m.GithubShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', title: 'Source code overview' + T, loadComponent: () => import('./features/github/github-overview').then((m) => m.GithubOverviewPage) },
      { path: 'repositories', title: 'Repositories' + T, loadComponent: () => import('./features/github/github-repositories').then((m) => m.GithubRepositoriesPage) },
      { path: 'pulls', title: 'Pull requests' + T, loadComponent: () => import('./features/github/github-pulls').then((m) => m.GithubPullsPage) },
    ],
  },
  {
    path: 'quality',
    loadComponent: () => import('./features/quality/quality-shell').then((m) => m.QualityShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', title: 'Code quality overview' + T, loadComponent: () => import('./features/quality/quality-overview').then((m) => m.QualityOverviewPage) },
      { path: 'projects', title: 'Quality by project' + T, loadComponent: () => import('./features/quality/quality-projects').then((m) => m.QualityProjectsPage) },
    ],
  },
  {
    path: 'sbom',
    loadComponent: () => import('./features/sbom/sbom-shell').then((m) => m.SbomShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', title: 'Security overview' + T, loadComponent: () => import('./features/sbom/sbom-overview').then((m) => m.SbomOverviewPage) },
      { path: 'scans', title: 'SBOM scans' + T, loadComponent: () => import('./features/sbom/sbom-scans').then((m) => m.SbomScansPage) },
    ],
  },
  {
    path: 'artifacts',
    loadComponent: () => import('./features/artifacts/artifacts-shell').then((m) => m.ArtifactsShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', title: 'Artifacts overview' + T, loadComponent: () => import('./features/artifacts/artifacts-overview').then((m) => m.ArtifactsOverviewPage) },
      { path: 'browse', title: 'Browse artifacts' + T, loadComponent: () => import('./features/artifacts/artifacts-browse').then((m) => m.ArtifactsBrowsePage) },
    ],
  },
  {
    path: 'monitoring',
    loadComponent: () => import('./features/monitoring/monitoring-shell').then((m) => m.MonitoringShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', title: 'Operations overview' + T, loadComponent: () => import('./features/monitoring/monitoring-overview').then((m) => m.MonitoringOverviewPage) },
      { path: 'alerts', title: 'Alerts' + T, loadComponent: () => import('./features/monitoring/monitoring-alerts').then((m) => m.MonitoringAlertsPage) },
      { path: 'infrastructure', title: 'Infrastructure' + T, loadComponent: () => import('./features/monitoring/monitoring-infrastructure').then((m) => m.MonitoringInfrastructurePage) },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
