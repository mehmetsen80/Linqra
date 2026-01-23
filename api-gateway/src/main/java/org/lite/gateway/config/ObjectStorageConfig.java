package org.lite.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class ObjectStorageConfig {

        @Value("${aws.region:us-west-2}")
        private String awsRegion;

        @Value("${linqra.storage.type:s3}") // s3 or minio
        private String storageType;

        @Value("${linqra.storage.endpoint:}") // e.g., http://localhost:9000
        private String storageEndpoint;

        @Value("${linqra.storage.access-key:}")
        private String accessKey;

        @Value("${linqra.storage.secret-key:}")
        private String secretKey;

        @Bean
        @Primary
        public S3AsyncClient s3AsyncClient() {
                if ("minio".equalsIgnoreCase(storageType)) {
                        return S3AsyncClient.builder()
                                        .region(Region.US_EAST_1) // MinIO default region
                                        .endpointOverride(URI.create(storageEndpoint))
                                        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                                        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                                        .serviceConfiguration(S3Configuration.builder()
                                                        .pathStyleAccessEnabled(true)
                                                        .build())
                                        .credentialsProvider(StaticCredentialsProvider.create(
                                                        AwsBasicCredentials.create(accessKey, secretKey)))
                                        .build();
                }

                return S3AsyncClient.builder()
                                .region(Region.of(awsRegion))
                                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                                .build();
        }

        @Bean
        @Primary
        public S3Presigner s3Presigner() {
                if ("minio".equalsIgnoreCase(storageType)) {
                        return S3Presigner.builder()
                                        .region(Region.US_EAST_1)
                                        .endpointOverride(URI.create(storageEndpoint))
                                        .serviceConfiguration(S3Configuration.builder()
                                                        .pathStyleAccessEnabled(true)
                                                        .build())
                                        .credentialsProvider(StaticCredentialsProvider.create(
                                                        AwsBasicCredentials.create(accessKey, secretKey)))
                                        .build();
                }

                return S3Presigner.builder()
                                .region(Region.of(awsRegion))
                                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                                .build();
        }
}
