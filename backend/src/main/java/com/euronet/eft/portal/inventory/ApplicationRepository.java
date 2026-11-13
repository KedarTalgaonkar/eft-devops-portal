package com.euronet.eft.portal.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByNameAndClient(String name, String client);

    @Query("select distinct a from Application a left join fetch a.deployments order by a.name, a.client")
    List<Application> findAllWithDeployments();
}
