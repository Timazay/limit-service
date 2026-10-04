package by.timofeyzaytsev.limitservice.config;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.config.property.ExchangeRateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Configuration
@RequiredArgsConstructor
public class RetryConfig {

    private final AppProperties props;

    @Bean
    public RetryTemplate retryTemplate() {
        ExchangeRateProperties.TwelveDataProperties td = props.exchangeRate().twelvedata();

        RetryPolicy policy = RetryPolicy.builder()
            .maxRetries(td.maxRetries())
            .delay(td.retryDelay())
            .multiplier(td.retryMultiplier())
            .maxDelay(td.retryMaxDelay())
            .includes(RestClientException.class)
            .excludes(HttpClientErrorException.NotFound.class)
            .build();

        return new RetryTemplate(policy);
    }
}
