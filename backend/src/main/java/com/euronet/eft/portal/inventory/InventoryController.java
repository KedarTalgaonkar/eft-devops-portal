package com.euronet.eft.portal.inventory;

import com.euronet.eft.portal.inventory.InventoryDtos.ApplicationDetail;
import com.euronet.eft.portal.inventory.InventoryDtos.ApplicationRequest;
import com.euronet.eft.portal.inventory.InventoryDtos.DeploymentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.euronet.eft.portal.inventory.InventoryDtos.ApplicationSummary;
import com.euronet.eft.portal.inventory.InventoryDtos.ChangeView;
import com.euronet.eft.portal.inventory.InventoryDtos.DeploymentView;
import com.euronet.eft.portal.inventory.InventoryDtos.ImportResult;
import com.euronet.eft.portal.inventory.InventoryDtos.UpdateDeploymentRequest;
import com.euronet.eft.portal.inventory.InventoryDtos.UpdateRepositoriesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "EFT applications, their deployment paths per environment, repositories and change log")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    @Operation(summary = "All applications with environments and repositories")
    public List<ApplicationSummary> applications() {
        return service.listApplications();
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "One application with all its deployments")
    public ApplicationDetail application(@PathVariable long id) {
        return service.getApplication(id);
    }

    @PutMapping("/applications/{id}/repositories")
    @Operation(summary = "Replace the GitHub repositories linked to an application")
    public ApplicationDetail updateRepositories(@PathVariable long id, @Valid @RequestBody UpdateRepositoriesRequest request) {
        return service.updateRepositories(id, request.repositories());
    }

    @GetMapping("/deployments")
    @Operation(summary = "Flat list of deployments, optionally for one environment code")
    public List<DeploymentView> deployments(@RequestParam(required = false) String env) {
        return service.listDeployments(env);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an application (name + client must be unique)")
    public ApplicationDetail createApplication(@Valid @RequestBody ApplicationRequest request) {
        return service.createApplication(request);
    }

    @PutMapping("/applications/{id}")
    @Operation(summary = "Edit an application's name, client, owner and repositories")
    public ApplicationDetail updateApplication(@PathVariable long id, @Valid @RequestBody ApplicationRequest request) {
        return service.updateApplication(id, request);
    }

    @PostMapping("/applications/{id}/deployments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a deployment path (environment + context must be unique per application)")
    public DeploymentView createDeployment(@PathVariable long id, @Valid @RequestBody DeploymentRequest request) {
        return service.createDeployment(id, request);
    }

    @PutMapping("/deployments/{id}")
    @Operation(summary = "Edit every field of a deployment path")
    public DeploymentView updateDeployment(@PathVariable long id, @Valid @RequestBody DeploymentRequest request) {
        return service.updateDeployment(id, request);
    }

    @DeleteMapping("/deployments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a deployment path (kept in the change log)")
    public void deleteDeployment(@PathVariable long id) {
        service.deleteDeployment(id);
    }

    @PutMapping("/deployments/{id}/jenkins-job")
    @Operation(summary = "Set the Jenkins job that tracks this deployment (blank = use the naming pattern)")
    public DeploymentView updateJenkinsJob(@PathVariable long id, @Valid @RequestBody UpdateDeploymentRequest request) {
        return service.updateJenkinsJob(id, request.jenkinsJob());
    }

    @GetMapping("/changes")
    @Operation(summary = "Change log: owner moves, new deployment paths, URL/version changes and portal edits")
    public List<ChangeView> changes(@RequestParam(required = false) Long applicationId,
                                    @RequestParam(defaultValue = "200") int limit) {
        return service.changes(applicationId, limit);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import the Tomcat projects Excel workbook (.xlsx): one sheet per owner, any number of environments")
    public ImportResult importWorkbook(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        if (file.isEmpty() || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Upload a non-empty .xlsx file");
        }
        try (InputStream in = file.getInputStream()) {
            return service.importWorkbook(in, name);
        }
    }
}
