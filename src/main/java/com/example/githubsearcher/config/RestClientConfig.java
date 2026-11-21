package com.example.githubsearcher.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GitHubApiProperties.class)
public class RestClientConfig {

    static final String ACCEPT = "application/vnd.github+json";
    static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    static final String API_VERSION = "2022-11-28";
    static final String USER_AGENT = "github-repository-searcher";

    @Bean
    public RestClient githubRestClient(GitHubApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return baseBuilder(properties)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Headers and base URL only. Tests bind this builder to {@code MockRestServiceServer}
     * before a request factory is installed.
     */
    public static RestClient.Builder baseBuilder(GitHubApiProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, ACCEPT)
                .defaultHeader(API_VERSION_HEADER, API_VERSION)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT);
        String token = properties.getToken();
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
        }
        return builder;
    }
}
