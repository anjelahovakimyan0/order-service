package am.itspace.orderservice.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("bookshop")
public record ClientProperties(
    @NotNull
    URI catalogServiceUri
) {}
