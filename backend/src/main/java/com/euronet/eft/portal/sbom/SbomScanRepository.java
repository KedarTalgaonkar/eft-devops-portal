package com.euronet.eft.portal.sbom;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SbomScanRepository extends JpaRepository<SbomScan, Long> {

    List<SbomScan> findTop100ByOrderByScannedAtDesc();
}
