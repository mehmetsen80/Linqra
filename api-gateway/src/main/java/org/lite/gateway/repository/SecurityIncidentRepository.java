package org.lite.gateway.repository;

import org.lite.gateway.entity.SecurityIncident;
import org.lite.gateway.enums.IncidentStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SecurityIncidentRepository extends ReactiveMongoRepository<SecurityIncident, String> {

    Flux<SecurityIncident> findByStatusOrderByDetectedAtDesc(IncidentStatus status);

    Flux<SecurityIncident> findByAffectedTeamIdOrderByDetectedAtDesc(String teamId);

    Flux<SecurityIncident> findByAffectedTeamIdAndStatusOrderByDetectedAtDesc(String teamId, IncidentStatus status);

    Flux<SecurityIncident> findByAffectedUserIdOrderByDetectedAtDesc(String userId);
}
