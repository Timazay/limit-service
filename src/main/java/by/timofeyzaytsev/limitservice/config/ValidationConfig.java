package by.timofeyzaytsev.limitservice.config;

import java.util.Locale;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Pins the locale used to render Bean Validation messages to English.
 *
 * <p>Hibernate Validator ships translations for 28 locales and picks one from the locale of the
 * machine the JVM runs on: a validator built under a Russian locale reports a {@code @Min(0)}
 * violation as {@code "должно быть не меньше 0"}. The API contract is English, so messages must
 * not depend on where the service is deployed, which makes the auto-configured validator
 * unusable as is.</p>
 *
 * <p>This class is only half of the fix. Spring 7 wraps the validator's interpolator in
 * {@code LocaleContextMessageInterpolator}, which takes the locale from
 * {@link org.springframework.context.i18n.LocaleContextHolder} rather than from the validator
 * itself, and Hibernate Validator only initializes bundles for the locales it knows at startup.
 * So {@code spring.web.locale=EN} together with {@code spring.web.locale-resolver=fixed} in
 * {@code application.yml} keeps {@code LocaleContextHolder} English regardless of the
 * {@code Accept-Language} header, and this class makes sure English is one of the locales
 * Hibernate Validator is able to serve.</p>
 */
@Configuration
public class ValidationConfig {

    /**
     * Replaces the auto-configured validator with one that always resolves messages in English.
     *
     * <p>The {@code localeResolver} returns a constant rather than relying on
     * {@code defaultLocale}: that is the only part of the interpolator consulted when resolving a
     * message, so pinning it here makes the outcome independent of the {@link Locale} argument
     * and of the machine's settings alike.</p>
     */
    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setMessageInterpolator(new ResourceBundleMessageInterpolator(
                Set.of(Locale.ENGLISH),
                Locale.ENGLISH,
                context -> Locale.ENGLISH,
                true));
        return factory;
    }
}