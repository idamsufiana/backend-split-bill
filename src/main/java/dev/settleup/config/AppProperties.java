package dev.settleup.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param githubUsername drives the service-charge percentage; override with the
 *                       {@code GITHUB_USERNAME} environment variable.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String githubUsername) {
}
