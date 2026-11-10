package br.com.fluxocaixa.lancamentos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Bean(destroyMethod = "close")
    SnsClient snsClient(
            @Value("${AWS_REGION:sa-east-1}") String region,
            @Value("${AWS_ENDPOINT_URL:}") String endpoint,
            @Value("${AWS_ACCESS_KEY_ID:test}") String accessKey,
            @Value("${AWS_SECRET_ACCESS_KEY:test}") String secretKey
    ) {
        var construtor = SnsClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (endpoint != null && !endpoint.isBlank()) {
            construtor
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return construtor.build();
    }
}
