package com.euronet.eft.portal.announcement;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @Query("select a from Announcement a where a.startsAt <= :now and a.endsAt > :now order by a.startsAt desc")
    List<Announcement> activeAt(@Param("now") Instant now);

    /**
     * Scheduled and running ones first, then the most recent past ones.
     */
    @Query("select a from Announcement a where a.endsAt > :since order by a.startsAt desc")
    List<Announcement> endingAfter(@Param("since") Instant since, Limit limit);
}
