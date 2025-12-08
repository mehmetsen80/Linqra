package org.lite.gateway.repository;

import org.lite.gateway.entity.SecurityIncident;
import org.lite.gateway.enums.IncidentStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SecurityIncidentRepository extends ReactiveMongoRepository<SecurityIncident, String> {

    Flux<SecurityIncident> findByStatus(IncidentStatus status);

    Flux<SecurityIncident> findByAffectedTeamId(String teamId);

    Flux<SecurityIncident> findByAffectedTeamIdAndStatus(String teamId, IncidentStatus status);

    Flux<SecurityIncident> findByAffectedUserId(String userId);
}
