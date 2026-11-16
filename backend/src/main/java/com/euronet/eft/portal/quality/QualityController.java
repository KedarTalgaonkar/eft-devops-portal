package com.euronet.eft.portal.quality;

import com.euronet.eft.portal.integration.sonar.SonarClient;
import com.euronet.eft.portal.integration.sonar.SonarModels.QualityProject;
import com.euronet.eft.portal.integration.sonar.SonarModels.QualityProjectDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quality")
@Tag(name = "Code quality (SonarQube)", description = "Quality gates and code metrics")
public class QualityController {

    private final SonarClient sonar;

    public QualityController(SonarClient sonar) {
        this.sonar = sonar;
    }

    @GetMapping("/projects")
    @Operation(summary = "All projects with quality gate status and key metrics")
    public List<QualityProject> projects() {
        return sonar.projects();
    }

    @GetMapping("/projects/{key}")
    @Operation(summary = "One project with its quality gate conditions")
    public QualityProjectDetail project(@PathVariable String key) {
        return sonar.project(key);
    }
}
