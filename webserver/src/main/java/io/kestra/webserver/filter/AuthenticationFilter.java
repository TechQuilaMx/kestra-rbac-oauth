package io.kestra.webserver.filter;

import java.util.Collection;
import java.util.Optional;

import org.reactivestreams.Publisher;

import io.kestra.webserver.annotation.AnonymousAccess;
import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.services.OAuth2Service;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Filter(Filter.MATCH_ALL_PATTERN)
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true")
public class AuthenticationFilter implements HttpServerFilter {
    private static final String PREFIX_BEARER = "Bearer";
    private static final Integer ORDER = ServerFilterPhase.SECURITY.order();
    /** @deprecated Use {@link BasicAuthService#BASIC_AUTH_COOKIE_NAME} */
    public static final String BASIC_AUTH_COOKIE_NAME = BasicAuthService.BASIC_AUTH_COOKIE_NAME;
    private static final String API_PREFIX = "/api/v1";

    @Inject
    private BasicAuthService basicAuthService;

    @Inject
    private Optional<OAuth2Service> oauth2Service = Optional.empty();

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        // Registered on "/**" instead of the Ant pattern "/api/v1/**", which Micronaut matches against
        // the raw request target and which a percent-encoded separator therefore evades (GHSA-rjhm-qm6w-m7x9).
        String rawPath = request.getPath();
        boolean rawLooksLikeApi = rawPath.startsWith(API_PREFIX);
        // Decoding only matters when there is something to decode or collapse; skip it otherwise so
        // static assets and health checks don't pay for a URI parse on every request.
        if (!rawLooksLikeApi && rawPath.indexOf('%') < 0 && !rawPath.contains("//")) {
            return chain.proceed(request);
        }
        String normalizedPath = normalizePath(request.getUri().getPath());
        if (!rawLooksLikeApi && !normalizedPath.startsWith(API_PREFIX)) {
            return chain.proceed(request);
        }

        return Mono.fromCallable(() -> basicAuthService.configuration())
            .subscribeOn(Schedulers.boundedElastic())
            .flux()
            .flatMap(basicAuthConfiguration ->
            {
                boolean isConfigEndpoint = "/api/v1/configs/login".equals(normalizedPath)
                    || "/api/v1/login".equals(normalizedPath)
                    || ((normalizedPath.matches("/api/v1(/[^/]+)?/basicAuth") || "/api/v1/basicAuthValidationErrors".equals(normalizedPath))
                        && !basicAuthService.isBasicAuthInitialized());

                // A path prefix is not an identity: the router resolves it, so the route it picked must
                // itself opt into anonymous access (GHSA-j5cv-8rw9-vv2p).
                boolean isOpenUrl = isAnonymousRoute(request)
                    && Optional.ofNullable(basicAuthConfiguration.openUrls())
                        .map(Collection::stream)
                        .map(stream -> stream.anyMatch(normalizedPath::startsWith))
                        .orElse(false);

                // Check OAuth2 openUrls as well
                boolean isOAuth2OpenUrl = oauth2Service
                    .filter(OAuth2Service::isEnabled)
                    .map(service -> service.getConfiguration().getOpenUrls())
                    .map(Collection::stream)
                    .map(stream -> stream.anyMatch(normalizedPath::startsWith))
                    .orElse(false);

                boolean mcpAuthHandled = request.getAttribute(McpServerAuthenticationFilter.MCP_AUTH_HANDLED, Boolean.class)
                    .orElse(false);

                if (isConfigEndpoint || isOpenUrl || isOAuth2OpenUrl || isManagementEndpoint(request) || mcpAuthHandled) {
                    return chain.proceed(request);
                }

                boolean oauth2Enabled = oauth2Service.isPresent() && oauth2Service.get().isEnabled();

                // Try OAuth2 Bearer token authentication first
                if (oauth2Enabled) {
                    var bearerToken = fromBearerToken(request);
                    if (bearerToken.isPresent()) {
                        // Validate OAuth2 token
                        var userInfo = oauth2Service.get().validateToken(bearerToken.get());
                        if (userInfo.isPresent()) {
                            // Token is valid, store user info in request for authorization
                            request.setAttribute("userInfo", userInfo.get());
                            return chain.proceed(request);
                        } else {
                            // Invalid OAuth2 token
                            return Mono.just(HttpResponse.unauthorized());
                        }
                    }
                }

                // Fall back to Basic Auth
                if (!basicAuthService.isAuthenticated(request)) {
                    Boolean isFromLoginPage = Optional.ofNullable(request.getHeaders().get("Referer"))
                        .map(referer -> referer.split("\\?")[0].endsWith("/login"))
                        .orElse(false);

                    boolean acceptsHtml = Optional.ofNullable(request.getHeaders().get("Accept"))
                        .map(a -> a.contains("text/html"))
                        .orElse(false);

                    boolean shouldChallengeWithBasic = !oauth2Enabled && !isFromLoginPage && acceptsHtml;

                    return Mono.just(HttpResponse.unauthorized())
                        .map(response -> shouldChallengeWithBasic ? response.header("WWW-Authenticate", "Basic") : response);
                }

                return chain.proceed(request);
            });
    }

    private static String normalizePath(String path) {
        return path.replaceAll("/+", "/");
    }

    private Optional<String> fromBearerToken(HttpRequest<?> request) {
        Optional<String> fromHeader = request.getHeaders()
            .getAuthorization()
            .filter(auth -> auth.toLowerCase().startsWith(PREFIX_BEARER.toLowerCase()))
            .map(token -> token.substring(PREFIX_BEARER.length() + 1).trim());
        if (fromHeader.isPresent()) {
            return fromHeader;
        }

        // EventSource/worker requests cannot always set Authorization headers.
        return Optional.ofNullable(request.getParameters().get("token"))
            .filter(token -> !token.isBlank());
    }

    @SuppressWarnings("rawtypes")
    private boolean isManagementEndpoint(HttpRequest<?> request) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch<?, ?> method) {
            return method.getAnnotation(Endpoint.class) != null;
        }
        return false;
    }

    @SuppressWarnings("rawtypes")
    private boolean isAnonymousRoute(HttpRequest<?> request) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch<?, ?> method) {
            return method.getAnnotation(AnonymousAccess.class) != null;
        }
        return false;
    }
}
