package by.timofeyzaytsev.limitservice.config;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.config.property.ExchangeRateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final AppProperties props;

    @Bean
    public RestClient twelveDataRestClient(
    ) {
        ExchangeRateProperties.TwelveDataProperties td = props.exchangeRate().twelvedata();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(td.connectTimeout());
        factory.setReadTimeout(td.readTimeout());

        return RestClient.builder()
            .baseUrl(td.baseUrl())
            .requestFactory(factory)
            .build();
    }
}
