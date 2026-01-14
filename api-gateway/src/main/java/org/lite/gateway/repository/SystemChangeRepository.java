package org.lite.gateway.repository;

import org.lite.gateway.entity.SystemChangeLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemChangeRepository extends ReactiveMongoRepository<SystemChangeLog, String> {
}
