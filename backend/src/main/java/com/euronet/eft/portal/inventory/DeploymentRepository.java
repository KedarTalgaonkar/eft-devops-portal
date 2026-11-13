package com.euronet.eft.portal.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    @Query("select d from Deployment d join fetch d.application a order by a.name, a.client, d.environment, d.contextName")
    List<Deployment> findAllWithApplication();
}
