package com.euronet.eft.portal.environment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnvironmentRepository extends JpaRepository<DeliveryEnvironment, String> {

    List<DeliveryEnvironment> findAllByOrderBySortOrderAscCodeAsc();
}
