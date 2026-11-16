package com.euronet.eft.portal.sbom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SbomScanRepository extends JpaRepository<SbomScan, Long> {

    List<SbomScan> findTop100ByOrderByScannedAtDesc();
}
