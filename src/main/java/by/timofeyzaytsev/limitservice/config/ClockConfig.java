package by.timofeyzaytsev.limitservice.config;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ClockConfig {

    private final AppProperties appProperties;

    @Bean
    public Clock clock() {
        return Clock.system(appProperties.timeZone());
    }
}
