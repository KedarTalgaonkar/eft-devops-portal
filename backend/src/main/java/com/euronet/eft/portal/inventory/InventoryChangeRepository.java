package com.euronet.eft.portal.inventory;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryChangeRepository extends JpaRepository<InventoryChange, Long> {

    List<InventoryChange> findAllByOrderByChangedAtDescIdDesc(Limit limit);

    List<InventoryChange> findByApplicationIdOrderByChangedAtDescIdDesc(Long applicationId, Limit limit);
}
