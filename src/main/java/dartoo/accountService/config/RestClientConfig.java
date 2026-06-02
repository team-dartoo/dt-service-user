package dartoo.accountService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient notificationServiceRestClient(
            @Value("${notification-service.base-url}") String baseUrl,
            @Value("${notification-service.worker-api-key}") String workerApiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Worker-API-Key", workerApiKey)
                .build();
    }
}
