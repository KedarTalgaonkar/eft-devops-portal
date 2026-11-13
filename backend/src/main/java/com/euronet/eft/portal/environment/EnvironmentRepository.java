package com.euronet.eft.portal.environment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<DeliveryEnvironment, String> {

    List<DeliveryEnvironment> findAllByOrderBySortOrderAscCodeAsc();
}
