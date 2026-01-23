package org.lite.gateway.service.impl; // Refactored for ObjectStorage

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.StorageProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3LifecycleConfigurationServiceImpl {

        private final S3AsyncClient s3AsyncClient;
        private final StorageProperties storageProperties;

        /**
         * Initialize S3 Lifecycle Rules on startup
         */
        @EventListener(ApplicationReadyEvent.class)
        public void initializeLifecycleRules() {
                log.info("Initializing Object Storage Lifecycle Rules for bucket: {}",
                                storageProperties.getBucketName());

                try {
                        // Define rules
                        List<LifecycleRule> rules = new ArrayList<>();

                        // Rule 1: Audit Logs Retention (7 Years)
                        rules.add(LifecycleRule.builder()
                                        .id("AuditLogsRetentionRule")
                                        .filter(LifecycleRuleFilter.builder()
                                                        .prefix("audit-logs/")
                                                        .build())
                                        .status(ExpirationStatus.ENABLED)
                                        .expiration(LifecycleExpiration.builder()
                                                        .days(2555) // 7 years * 365
                                                        .build())
                                        .build());

                        // Rule 2: Exports Cleanup (7 Days)
                        rules.add(LifecycleRule.builder()
                                        .id("ExportsCleanupRule")
                                        .filter(LifecycleRuleFilter.builder()
                                                        .prefix("exports/")
                                                        .build())
                                        .status(ExpirationStatus.ENABLED)
                                        .expiration(LifecycleExpiration.builder()
                                                        .days(7)
                                                        .build())
                                        .build());

                        // Rule 3: Temporary Uploads Cleanup (1 Day) - Good practice for aborted
                        // multipart uploads
                        rules.add(LifecycleRule.builder()
                                        .id("AbortedMultipartUploadsRule")
                                        .filter(LifecycleRuleFilter.builder()
                                                        .prefix("") // Apply to all
                                                        .build())
                                        .status(ExpirationStatus.ENABLED)
                                        .abortIncompleteMultipartUpload(AbortIncompleteMultipartUpload.builder()
                                                        .daysAfterInitiation(1)
                                                        .build())
                                        .build());

                        BucketLifecycleConfiguration lifecycleConfig = BucketLifecycleConfiguration.builder()
                                        .rules(rules)
                                        .build();

                        PutBucketLifecycleConfigurationRequest request = PutBucketLifecycleConfigurationRequest
                                        .builder()
                                        .bucket(storageProperties.getBucketName())
                                        .lifecycleConfiguration(lifecycleConfig)
                                        .build();

                        // Execute asynchronously but log result
                        s3AsyncClient.putBucketLifecycleConfiguration(request)
                                        .whenComplete((response, error) -> {
                                                if (error != null) {
                                                        log.error("Failed to set Lifecycle Rules: {}",
                                                                        error.getMessage());
                                                } else {
                                                        log.info("Successfully configured Lifecycle Rules (Audits: 7 years, Exports: 7 days)");
                                                }
                                        });

                } catch (Exception e) {
                        log.error("Error configuring storage lifecycle: {}", e.getMessage(), e);
                }
        }
}
