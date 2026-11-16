package com.euronet.eft.portal.integration.sonar;

import com.euronet.eft.portal.integration.IntegrationHealth;
import com.euronet.eft.portal.integration.sonar.SonarModels.QualityProject;
import com.euronet.eft.portal.integration.sonar.SonarModels.QualityProjectDetail;

import java.util.List;

/**
 * Code quality and quality gates. Live client: phase 2 (/api/measures/component, /api/qualitygates/project_status).
 */
public interface SonarClient extends IntegrationHealth {

    List<QualityProject> projects();

    QualityProjectDetail project(String key);
}
