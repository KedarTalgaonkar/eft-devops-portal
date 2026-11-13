package com.euronet.eft.portal.sbom;

import com.euronet.eft.portal.sbom.SbomService.ScanRequest;
import com.euronet.eft.portal.sbom.SbomService.ScanView;
import com.euronet.eft.portal.sbom.SbomService.VulnerabilityView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sbom")
@Tag(name = "SBOM", description = "SBOM generation and vulnerability scan results")
public class SbomController {

    private final SbomService service;

    public SbomController(SbomService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(summary = "Current risk: latest scan per project, severity totals and the most widespread vulnerabilities")
    public SbomService.SbomSummary summary() {
        return service.summary();
    }

    @GetMapping("/scans")
    @Operation(summary = "Latest 100 scans, newest first")
    public List<ScanView> scans() {
        return service.recentScans();
    }

    @GetMapping("/scans/{id}/vulnerabilities")
    @Operation(summary = "Vulnerabilities found by one scan, most severe first")
    public List<VulnerabilityView> vulnerabilities(@PathVariable long id) {
        return service.vulnerabilities(id);
    }

    @PostMapping("/scans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate an SBOM for a project version and scan it")
    public ScanView scan(@Valid @RequestBody ScanRequest request) {
        return service.scan(request);
    }
}
