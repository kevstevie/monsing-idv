package org.monsing.config

import java.net.URI
import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryMode
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration

@Configuration
class S3Config {

    private fun s3OverrideConfiguration(): ClientOverrideConfiguration =
        ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofSeconds(30))
            .apiCallAttemptTimeout(Duration.ofSeconds(10))
            .retryStrategy(
                RetryMode.STANDARD
            )
            .build()

    @Bean
    @Profile("!local")
    fun s3Client(): S3Client = S3Client.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(InstanceProfileCredentialsProvider.create())
        .overrideConfiguration(s3OverrideConfiguration())
        .build()

    @Bean
    @Profile("local")
    fun localStackS3Client(): S3Client = S3Client.builder()
        .region(Region.AP_NORTHEAST_2)
        .endpointOverride(URI.create("http://localhost:4566"))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")
            )
        )
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()
        )
        .overrideConfiguration(s3OverrideConfiguration())
        .build()
}
