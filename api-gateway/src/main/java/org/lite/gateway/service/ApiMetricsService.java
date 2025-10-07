package org.lite.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiMetric;
import org.lite.gateway.repository.ApiMetricRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiMetricsService {

    // Define output type to avoid raw type warnings
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> OUTPUT_TYPE = (Class<Map<String, Object>>) (Class<?>) Map.class;

    private final ApiMetricRepository apiMetricRepository;
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    public Flux<ApiMetric> getMetrics(LocalDateTime startDate, LocalDateTime endDate, 
                                    String fromService, String toService) {
        return apiMetricRepository.findByTimestampBetweenAndServices(
            startDate != null ? startDate : LocalDateTime.now().minusYears(10),
            endDate != null ? endDate : LocalDateTime.now(),
            fromService,
            toService
        );
    }

    public Mono<Map<String, Object>> getMetricsSummary(LocalDateTime startDate, LocalDateTime endDate) {
        Criteria timeCriteria = new Criteria();
        if (startDate != null && endDate != null) {
            timeCriteria = Criteria.where("timestamp").gte(startDate).lte(endDate);
        }

        AggregationOperation match = Aggregation.match(timeCriteria);
        AggregationOperation group = Aggregation.group()
                .count().as("totalRequests")
                .sum("duration").as("totalDuration")
                .avg("duration").as("avgDuration")
                .max("duration").as("maxDuration")
                .min("duration").as("minDuration")
                .sum(ConditionalOperators.when(Criteria.where("success").is(true))
                    .then(1)
                    .otherwise(0)).as("successfulRequests")
                .sum(ConditionalOperators.when(Criteria.where("success").is(false))
                    .then(1)
                    .otherwise(0)).as("failedRequests");

        // Project to exclude _id from the result
        AggregationOperation project = Aggregation.project(
                "totalRequests", "totalDuration", "avgDuration", 
                "maxDuration", "minDuration", "successfulRequests", "failedRequests"
        ).andExclude("_id");

        TypedAggregation<ApiMetric> aggregation = Aggregation.newAggregation(ApiMetric.class, match, group, project);
        return reactiveMongoTemplate.aggregate(aggregation, OUTPUT_TYPE)
                .next()
                .defaultIfEmpty(new HashMap<>());
    }


    public Mono<Map<String, Object>> getServiceInteractionsByService(String serviceName, LocalDateTime startDate, LocalDateTime endDate) {
        Criteria timeCriteria = new Criteria();
        if (startDate != null && endDate != null) {
            timeCriteria = Criteria.where("timestamp").gte(startDate).lte(endDate);
        }

        // Get incoming traffic (where service is toService)
        Criteria incomingCriteria = new Criteria().andOperator(
                timeCriteria,
                Criteria.where("toService").is(serviceName)
        );

        AggregationOperation incomingMatch = Aggregation.match(incomingCriteria);
        AggregationOperation incomingGroup = Aggregation.group("fromService")
                .count().as("count")
                .avg("duration").as("avgDuration")
                .sum("duration").as("totalDuration")
                .sum(ConditionalOperators.when(Criteria.where("success").is(true))
                    .then(1)
                    .otherwise(0)).as("successCount")
                .sum(ConditionalOperators.when(Criteria.where("success").is(false))
                    .then(1)
                    .otherwise(0)).as("failureCount");

        AggregationOperation incomingProject = Aggregation.project("count", "avgDuration", "totalDuration", "successCount", "failureCount")
                .and("_id").as("fromService")
                .andExclude("_id");

        TypedAggregation<ApiMetric> incomingAggregation = Aggregation.newAggregation(
                ApiMetric.class, incomingMatch, incomingGroup, incomingProject
        );

        // Get outgoing traffic (where service is fromService)
        Criteria outgoingCriteria = new Criteria().andOperator(
                timeCriteria,
                Criteria.where("fromService").is(serviceName)
        );

        AggregationOperation outgoingMatch = Aggregation.match(outgoingCriteria);
        AggregationOperation outgoingGroup = Aggregation.group("toService")
                .count().as("count")
                .avg("duration").as("avgDuration")
                .sum("duration").as("totalDuration")
                .sum(ConditionalOperators.when(Criteria.where("success").is(true))
                    .then(1)
                    .otherwise(0)).as("successCount")
                .sum(ConditionalOperators.when(Criteria.where("success").is(false))
                    .then(1)
                    .otherwise(0)).as("failureCount");

        AggregationOperation outgoingProject = Aggregation.project("count", "avgDuration", "totalDuration", "successCount", "failureCount")
                .and("_id").as("toService")
                .andExclude("_id");

        TypedAggregation<ApiMetric> outgoingAggregation = Aggregation.newAggregation(
                ApiMetric.class, outgoingMatch, outgoingGroup, outgoingProject
        );

        // Execute both aggregations and combine results
        return Mono.zip(
                reactiveMongoTemplate.aggregate(incomingAggregation, OUTPUT_TYPE).collectList(),
                reactiveMongoTemplate.aggregate(outgoingAggregation, OUTPUT_TYPE).collectList()
        ).map(tuple -> {
            Map<String, Object> result = new HashMap<>();
            result.put("serviceName", serviceName);
            result.put("incoming", tuple.getT1());  // Traffic coming to this service
            result.put("outgoing", tuple.getT2());  // Traffic going from this service
            return result;
        });
    }

    public Mono<Map<String, Object>> getServiceInteractionsSummary(String serviceName, LocalDateTime startDate, LocalDateTime endDate) {
        Criteria timeCriteria = new Criteria();
        if (startDate != null && endDate != null) {
            timeCriteria = Criteria.where("timestamp").gte(startDate).lte(endDate);
        }

        // Get incoming traffic totals (where service is toService)
        Criteria incomingCriteria = new Criteria().andOperator(
                timeCriteria,
                Criteria.where("toService").is(serviceName)
        );

        AggregationOperation incomingMatch = Aggregation.match(incomingCriteria);
        AggregationOperation incomingGroup = Aggregation.group()
                .count().as("totalCount")
                .avg("duration").as("avgDuration")
                .sum("duration").as("totalDuration")
                .min("duration").as("minDuration")
                .max("duration").as("maxDuration")
                .sum(ConditionalOperators.when(Criteria.where("success").is(true))
                    .then(1)
                    .otherwise(0)).as("successCount")
                .sum(ConditionalOperators.when(Criteria.where("success").is(false))
                    .then(1)
                    .otherwise(0)).as("failureCount");

        AggregationOperation incomingProject = Aggregation.project(
                "totalCount", "avgDuration", "totalDuration", "minDuration", "maxDuration", "successCount", "failureCount"
        ).andExclude("_id");

        TypedAggregation<ApiMetric> incomingAggregation = Aggregation.newAggregation(
                ApiMetric.class, incomingMatch, incomingGroup, incomingProject
        );

        // Get outgoing traffic totals (where service is fromService)
        Criteria outgoingCriteria = new Criteria().andOperator(
                timeCriteria,
                Criteria.where("fromService").is(serviceName)
        );

        AggregationOperation outgoingMatch = Aggregation.match(outgoingCriteria);
        AggregationOperation outgoingGroup = Aggregation.group()
                .count().as("totalCount")
                .avg("duration").as("avgDuration")
                .sum("duration").as("totalDuration")
                .min("duration").as("minDuration")
                .max("duration").as("maxDuration")
                .sum(ConditionalOperators.when(Criteria.where("success").is(true))
                    .then(1)
                    .otherwise(0)).as("successCount")
                .sum(ConditionalOperators.when(Criteria.where("success").is(false))
                    .then(1)
                    .otherwise(0)).as("failureCount");

        AggregationOperation outgoingProject = Aggregation.project(
                "totalCount", "avgDuration", "totalDuration", "minDuration", "maxDuration", "successCount", "failureCount"
        ).andExclude("_id");

        TypedAggregation<ApiMetric> outgoingAggregation = Aggregation.newAggregation(
                ApiMetric.class, outgoingMatch, outgoingGroup, outgoingProject
        );

        // Execute both aggregations and combine results
        return Mono.zip(
                reactiveMongoTemplate.aggregate(incomingAggregation, OUTPUT_TYPE)
                        .next()
                        .defaultIfEmpty(new HashMap<>()),
                reactiveMongoTemplate.aggregate(outgoingAggregation, OUTPUT_TYPE)
                        .next()
                        .defaultIfEmpty(new HashMap<>())
        ).map(tuple -> {
            Map<String, Object> incomingStats = tuple.getT1();
            Map<String, Object> outgoingStats = tuple.getT2();
            
            // Calculate success rates
            if (incomingStats.containsKey("totalCount")) {
                int totalCount = ((Number) incomingStats.get("totalCount")).intValue();
                int successCount = ((Number) incomingStats.get("successCount")).intValue();
                double successRate = totalCount > 0 ? (successCount * 100.0 / totalCount) : 0.0;
                incomingStats.put("successRate", successRate);
            }
            
            if (outgoingStats.containsKey("totalCount")) {
                int totalCount = ((Number) outgoingStats.get("totalCount")).intValue();
                int successCount = ((Number) outgoingStats.get("successCount")).intValue();
                double successRate = totalCount > 0 ? (successCount * 100.0 / totalCount) : 0.0;
                outgoingStats.put("successRate", successRate);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("serviceName", serviceName);
            result.put("incoming", incomingStats);
            result.put("outgoing", outgoingStats);
            return result;
        });
    }

    public Flux<Map<String, Object>> getServiceInteractions(LocalDateTime startDate, LocalDateTime endDate) {
        Criteria timeCriteria = new Criteria();
        if (startDate != null && endDate != null) {
            timeCriteria = Criteria.where("timestamp").gte(startDate).lte(endDate);
        }

        AggregationOperation match = Aggregation.match(timeCriteria);
        AggregationOperation group = Aggregation.group("fromService", "toService")
                .count().as("count")
                .avg("duration").as("avgDuration")
                .sum("duration").as("totalDuration")
                .sum(ConditionalOperators.when(Criteria.where("success").is(true))
                    .then(1)
                    .otherwise(0)).as("successCount")
                .sum(ConditionalOperators.when(Criteria.where("success").is(false))
                    .then(1)
                    .otherwise(0)).as("failureCount");

        // Project to rename _id fields to more meaningful names and exclude _id from output
        AggregationOperation project = Aggregation.project("count", "avgDuration", "totalDuration", "successCount", "failureCount")
                .and("_id.fromService").as("fromService")
                .and("_id.toService").as("toService")
                .andExclude("_id");

        AggregationOperation sort = Aggregation.sort(Sort.Direction.DESC, "count");

        TypedAggregation<ApiMetric> aggregation = Aggregation.newAggregation(ApiMetric.class, match, group, project, sort);
        return reactiveMongoTemplate.aggregate(aggregation, OUTPUT_TYPE);
    }

    public Flux<Map<String, Object>> getTopEndpointsByService(String serviceName, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        Criteria timeCriteria = new Criteria();
        if (startDate != null && endDate != null) {
            timeCriteria = Criteria.where("timestamp").gte(startDate).lte(endDate);
        }

        // Add service filter to match either fromService or toService
        Criteria serviceCriteria = new Criteria().orOperator(
                Criteria.where("fromService").is(serviceName),
                Criteria.where("toService").is(serviceName)
        );

        Criteria finalCriteria = new Criteria().andOperator(timeCriteria, serviceCriteria);

        AggregationOperation match = Aggregation.match(finalCriteria);
        AggregationOperation group = Aggregation.group("pathEndPoint")
                .count().as("count")
                .avg("duration").as("avgDuration")
                .addToSet("toService").as("services");

        // Project to rename _id to endpoint and exclude _id from output
        AggregationOperation project = Aggregation.project("count", "avgDuration", "services")
                .and("_id").as("endpoint")
                .andExclude("_id"); // Explicitly exclude _id

        AggregationOperation sort = Aggregation.sort(Sort.Direction.DESC, "count");
        AggregationOperation limitOp = Aggregation.limit(limit);

        TypedAggregation<ApiMetric> aggregation = Aggregation.newAggregation(ApiMetric.class, match, group, project, sort, limitOp);
        return reactiveMongoTemplate.aggregate(aggregation, OUTPUT_TYPE);
    }

    public Flux<Map<String, Object>> getTopEndpoints(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        Criteria timeCriteria = new Criteria();
        if (startDate != null && endDate != null) {
            timeCriteria = Criteria.where("timestamp").gte(startDate).lte(endDate);
        }

        AggregationOperation match = Aggregation.match(timeCriteria);
        AggregationOperation group = Aggregation.group("pathEndPoint")
                .count().as("count")
                .avg("duration").as("avgDuration")
                .addToSet("toService").as("services");

        // Project to rename _id to endpoint and exclude _id from output
        AggregationOperation project = Aggregation.project("count", "avgDuration", "services")
                .and("_id").as("endpoint")
                .andExclude("_id");

        AggregationOperation sort = Aggregation.sort(Sort.Direction.DESC, "count");
        AggregationOperation limitOp = Aggregation.limit(limit);

        TypedAggregation<ApiMetric> aggregation = Aggregation.newAggregation(ApiMetric.class, match, group, project, sort, limitOp);
        return reactiveMongoTemplate.aggregate(aggregation, OUTPUT_TYPE);
    }

    public Mono<ApiMetric> getMetricById(String id) {
        return apiMetricRepository.findById(id);
    }

    // TODO: Implement secure delete operations
    /*
    public Mono<Void> deleteMetricById(String id) {
        return apiMetricRepository.deleteById(id);
    }

    public Mono<Void> deleteAllMetrics() {
        return apiMetricRepository.deleteAll();
    }
    */

    public Mono<Long> getMetricsCount() {
        return apiMetricRepository.count();
    }

    public Flux<ApiMetric> getMetricsByService(String serviceName) {
        return apiMetricRepository.findByFromServiceOrToService(serviceName, serviceName);
    }
} 