package org.springdoc.webflux.ui;

import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.ui.AbstractSwaggerWelcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

import static org.springdoc.core.utils.Constants.DEFAULT_WEB_JARS_PREFIX_URL;

/**
 * Spring Boot 4 / Spring Framework 7 compatibility patch for Springdoc WebFlux.
 * In Spring 7, {@code UriComponentsBuilder.fromHttpRequest(HttpRequest)} was removed.
 * This class replaces it with {@code UriComponentsBuilder.fromUri(request.getURI())}.
 */
public abstract class SwaggerWelcomeCommon extends AbstractSwaggerWelcome {

    protected SwaggerWelcomeCommon(SwaggerUiConfigProperties swaggerUiConfig, SpringDocConfigProperties springDocConfigProperties) {
        super(swaggerUiConfig, springDocConfigProperties);
    }

    protected Mono<Void> redirectToUi(ServerHttpRequest request, ServerHttpResponse response) {
        SwaggerUiConfigParameters swaggerUiConfigParameters = new SwaggerUiConfigParameters(swaggerUiConfig);
        buildFromCurrentContextPath(swaggerUiConfigParameters, request);
        String webjarsPrefix = springDocConfigProperties.getWebjars().getPrefix();
        String additionalPrefix = DEFAULT_WEB_JARS_PREFIX_URL.equals(webjarsPrefix) ? "" : webjarsPrefix;
        String sbUrl = swaggerUiConfigParameters.getContextPath()
                + swaggerUiConfigParameters.getUiRootPath()
                + additionalPrefix
                + getSwaggerUiUrl();
        UriComponentsBuilder uriBuilder = getUriComponentsBuilder(swaggerUiConfigParameters, sbUrl);
        request.getQueryParams().forEach(uriBuilder::queryParam);
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(uriBuilder.build().encode().toString()));
        return response.setComplete();
    }

    @Override
    protected void calculateOauth2RedirectUrl(SwaggerUiConfigParameters swaggerUiConfigParameters, UriComponentsBuilder uriComponentsBuilder) {
        if (StringUtils.isBlank(swaggerUiConfig.getOauth2RedirectUrl()) || !swaggerUiConfigParameters.isValidUrl(swaggerUiConfig.getOauth2RedirectUrl())) {
            String webjarsPrefix = springDocConfigProperties.getWebjars().getPrefix();
            String additionalPath = DEFAULT_WEB_JARS_PREFIX_URL.equals(webjarsPrefix) ? "" : webjarsPrefix;
            swaggerUiConfigParameters.setOauth2RedirectUrl(
                    uriComponentsBuilder
                            .path(swaggerUiConfigParameters.getUiRootPath())
                            .path(additionalPath)
                            .path(getOauth2RedirectUrl())
                            .build()
                            .toString()
            );
        }
    }

    protected Map<String, Object> getSwaggerUiConfig(ServerHttpRequest request) {
        SwaggerUiConfigParameters swaggerUiConfigParameters = new SwaggerUiConfigParameters(swaggerUiConfig);
        this.buildFromCurrentContextPath(swaggerUiConfigParameters, request);
        return swaggerUiConfigParameters.getConfigParameters();
    }

    void buildFromCurrentContextPath(SwaggerUiConfigParameters swaggerUiConfigParameters, ServerHttpRequest request) {
        super.init(swaggerUiConfigParameters);
        swaggerUiConfigParameters.setContextPath(request.getPath().contextPath().value());
        String url = UriComponentsBuilder.fromUri(request.getURI()).toUriString();
        String target = UriComponentsBuilder.fromPath(request.getPath().contextPath().value()).toUriString();
        int endIndex = url.indexOf(target) + target.length();
        if (endIndex > 0) {
            url = url.substring(0, endIndex);
        } else {
            url = url.replace(request.getPath().toString(), "");
        }
        buildConfigUrl(swaggerUiConfigParameters, UriComponentsBuilder.fromUriString(url));
    }
}
