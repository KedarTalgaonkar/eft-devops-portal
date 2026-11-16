package com.euronet.eft.portal.announcement;

import com.euronet.eft.portal.announcement.AnnouncementService.AnnouncementRequest;
import com.euronet.eft.portal.announcement.AnnouncementService.AnnouncementView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reading is for every signed-in user; publishing, editing and ending need EDITOR (SecurityConfig). Changes are audited.
 */
@RestController
@RequestMapping("/api/announcements")
@Tag(name = "Announcements", description = "Time-bound header messages for releases, deployments and maintenance")
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping("/active")
    @Operation(summary = "Announcements to show in the header right now")
    public List<AnnouncementView> active() {
        return service.active();
    }

    @GetMapping
    @Operation(summary = "Scheduled, running and recently ended announcements")
    public List<AnnouncementView> recent(@RequestParam(defaultValue = "50") int limit) {
        return service.recent(limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish an announcement (editors and administrators)")
    public AnnouncementView create(@RequestBody AnnouncementRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change an announcement")
    public AnnouncementView update(@PathVariable long id, @RequestBody AnnouncementRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "Take an announcement down now")
    public AnnouncementView end(@PathVariable long id) {
        return service.end(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an announcement")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
