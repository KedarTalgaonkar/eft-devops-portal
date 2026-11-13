package com.euronet.eft.portal.environment;

import com.euronet.eft.portal.environment.EnvironmentCatalog.EnvironmentView;
import com.euronet.eft.portal.environment.EnvironmentCatalog.UpdateEnvironmentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/environments")
@Tag(name = "Environments", description = "Delivery environments in display order; new ones are added by the Excel import")
public class EnvironmentController {

    private final EnvironmentCatalog catalog;

    public EnvironmentController(EnvironmentCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "All environments in display order")
    public List<EnvironmentView> list() {
        return catalog.list();
    }

    @PutMapping("/{code}")
    @Operation(summary = "Rename, reorder, re-categorise (PROD / NON_PROD) or hide an environment")
    public EnvironmentView update(@PathVariable String code, @RequestBody UpdateEnvironmentRequest request) {
        return catalog.update(code, request);
    }
}
