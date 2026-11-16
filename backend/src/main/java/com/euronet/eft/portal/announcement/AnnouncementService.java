package com.euronet.eft.portal.announcement;

import com.euronet.eft.portal.announcement.Announcement.Kind;
import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.security.CurrentUser;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Time-bound header messages for every signed-in user, e.g. "UAT deployment of IMPS 2.4 tonight 21:00–23:00".
 * Editors and administrators publish them (SecurityConfig: changes under /api need EDITOR).
 */
@Service
public class AnnouncementService {

    static final int MAX_MESSAGE = 500;
    static final Duration MAX_DURATION = Duration.ofDays(30);

    private final AnnouncementRepository repository;

    public AnnouncementService(AnnouncementRepository repository) {
        this.repository = repository;
    }

    private static void apply(Announcement a, AnnouncementRequest r, boolean creating) {
        if (r == null || r.kind() == null) {
            throw new IllegalArgumentException("Choose a type: INFO, RELEASE, MAINTENANCE or INCIDENT.");
        }
        String message = r.message() == null ? "" : r.message().strip().replaceAll("\\s+", " ");
        if (message.length() < 3 || message.length() > MAX_MESSAGE) {
            throw new IllegalArgumentException("The message must be 3 to " + MAX_MESSAGE + " characters.");
        }
        Instant now = Instant.now();
        Instant start = r.startsAt() == null ? now : r.startsAt();
        if (r.endsAt() == null || !r.endsAt().isAfter(start)) {
            throw new IllegalArgumentException("The end time must be after the start time.");
        }
        if (creating && !r.endsAt().isAfter(now)) {
            throw new IllegalArgumentException("The end time is already in the past.");
        }
        if (Duration.between(start, r.endsAt()).compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("An announcement can run for at most 30 days.");
        }
        String link = blankToNull(r.linkUrl());
        if (link != null && !(link.startsWith("https://") || (link.startsWith("/") && !link.startsWith("//") && !link.startsWith("/\\"))) || link != null && (link.length() > 500 || link.chars().anyMatch(Character::isWhitespace))) {
            throw new IllegalArgumentException("The link must be a portal path such as /pipelines or an https:// address.");
        }
        String label = blankToNull(r.linkLabel());
        if (label != null && label.length() > 60) {
            throw new IllegalArgumentException("The link text can be at most 60 characters.");
        }
        a.set(r.kind(), message, link, link == null ? null : (label == null ? "Details" : label), start, r.endsAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static AnnouncementView view(Announcement a, Instant now) {
        Status status = now.isBefore(a.getStartsAt()) ? Status.SCHEDULED : now.isBefore(a.getEndsAt()) ? Status.ACTIVE : Status.ENDED;
        return new AnnouncementView(a.getId(), a.getKind(), a.getMessage(), a.getLinkUrl(), a.getLinkLabel(), a.getStartsAt(), a.getEndsAt(), status, a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedBy(), a.getUpdatedAt());
    }

    /**
     * What the header shows right now: incidents first, then maintenance, releases, information.
     */
    @Transactional(readOnly = true)
    public List<AnnouncementView> active() {
        Instant now = Instant.now();
        return repository.activeAt(now).stream().sorted(Comparator.comparing((Announcement a) -> -a.getKind().ordinal()).thenComparing(Announcement::getStartsAt)).map(a -> view(a, now)).toList();
    }

    /**
     * Scheduled, running and recently ended (last 30 days) announcements for the management page.
     */
    @Transactional(readOnly = true)
    public List<AnnouncementView> recent(int limit) {
        Instant now = Instant.now();
        return repository.endingAfter(now.minus(Duration.ofDays(30)), Limit.of(Math.clamp(limit, 1, 200))).stream().map(a -> view(a, now)).toList();
    }

    @Transactional
    public AnnouncementView create(AnnouncementRequest request) {
        Announcement a = new Announcement(CurrentUser.nameOrSystem());
        apply(a, request, true);
        return view(repository.save(a), Instant.now());
    }

    @Transactional
    public AnnouncementView update(long id, AnnouncementRequest request) {
        Announcement a = find(id);
        apply(a, request, false);
        a.touched(CurrentUser.nameOrSystem());
        return view(a, Instant.now());
    }

    /**
     * Takes the message down now (keeps it in the history).
     */
    @Transactional
    public AnnouncementView end(long id) {
        Announcement a = find(id);
        a.endNow(CurrentUser.nameOrSystem());
        return view(a, Instant.now());
    }

    @Transactional
    public void delete(long id) {
        repository.delete(find(id));
    }

    private Announcement find(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Announcement", id));
    }

    public enum Status {SCHEDULED, ACTIVE, ENDED}

    public record AnnouncementView(Long id, Kind kind, String message, String linkUrl, String linkLabel,
                                   Instant startsAt, Instant endsAt, Status status, String createdBy, Instant createdAt,
                                   String updatedBy, Instant updatedAt) {
    }

    /**
     * @param startsAt optional; now when empty
     * @param endsAt   required; after the start, at most 30 days after it
     * @param linkUrl  optional; a portal path ("/pipelines") or an https:// address
     */
    public record AnnouncementRequest(Kind kind, String message, String linkUrl, String linkLabel, Instant startsAt,
                                      Instant endsAt) {
    }
}
