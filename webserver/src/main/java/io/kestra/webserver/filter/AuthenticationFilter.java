package io.kestra.webserver.filter;

import io.kestra.core.utils.AuthUtils;
import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.services.OAuth2Service;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Base64;
import java.util.Collection;
import java.util.Optional;

//We want to authenticate only Kestra endpoints
@Filter("/api/v1/**")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true") // don't add this filter in EE
public class AuthenticationFilter implements HttpServerFilter {
    private static final String PREFIX_BASIC = "Basic";
    private static final String PREFIX_BEARER = "Bearer";
    private static final Integer ORDER = ServerFilterPhase.SECURITY.order();
    public static final String BASIC_AUTH_COOKIE_NAME = "BASIC_AUTH";

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
        return Mono.fromCallable(() -> basicAuthService.configuration())
            .subscribeOn(Schedulers.boundedElastic())
            .flux()
            .flatMap(basicAuthConfiguration -> {
                boolean isConfigEndpoint = request.getPath().endsWith("/configs")
                    || (
                    (request.getPath().endsWith("/basicAuth") || request.getPath().endsWith("/basicAuthValidationErrors"))
                        && !basicAuthService.isBasicAuthInitialized()
                );

                boolean isOpenUrl = Optional.ofNullable(basicAuthConfiguration.openUrls())
                    .map(Collection::stream)
                    .map(stream -> stream.anyMatch(s -> request.getPath().startsWith(s)))
                    .orElse(false);
                
                // Check OAuth2 openUrls as well
                boolean isOAuth2OpenUrl = oauth2Service
                    .filter(OAuth2Service::isEnabled)
                    .map(service -> service.getConfiguration().getOpenUrls())
                    .map(Collection::stream)
                    .map(stream -> stream.anyMatch(s -> request.getPath().startsWith(s)))
                    .orElse(false);

                if (isConfigEndpoint || isOpenUrl || isOAuth2OpenUrl || isManagementEndpoint(request)) {
                    return chain.proceed(request);
                }
                
                // Try OAuth2 Bearer token authentication first
                if (oauth2Service.isPresent() && oauth2Service.get().isEnabled()) {
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
                var basicAuth = fromCookie(request)
                    .or(() -> fromAuthorizationHeader(request))
                    .map(BasicAuth::from);

                if (basicAuth.isEmpty() || basicAuthConfiguration.credentials() == null ||
                    !basicAuth.get().username().equals(basicAuthConfiguration.credentials().getUsername()) ||
                    !AuthUtils.encodePassword(basicAuthConfiguration.credentials().getSalt(),
                        basicAuth.get().password()).equals(basicAuthConfiguration.credentials().getPassword())
                ) {
                    Boolean isFromLoginPage = Optional.ofNullable(request.getHeaders().get("Referer")).map(referer -> referer.split("\\?")[0].endsWith("/login")).orElse(false);

                    // Avoid sending a WWW-Authenticate: Basic header for API calls that expect JSON
                    // (the browser will show a native credentials prompt when that header is present).
                    boolean acceptsHtml = Optional.ofNullable(request.getHeaders().get("Accept")).map(a -> a.contains("text/html")).orElse(false);

                    return Mono.just(HttpResponse.unauthorized())
                        .map(response -> (isFromLoginPage || !acceptsHtml) ? response : response.header("WWW-Authenticate", "Basic"));
                }

                return chain.proceed(request);
            });
    }

    private Optional<String> fromCookie(HttpRequest<?> request) {
        try {
            return Optional.ofNullable(
                request.getCookies()
                    .get(BASIC_AUTH_COOKIE_NAME)
            ).map(Cookie::getValue);
        } catch (Exception e) {
            // Can happen in tests because getCookies() is not implemented in NettyClientHttpRequest but is in NettyHttpRequest
            return Optional.empty();
        }
    }

    private Optional<String> fromAuthorizationHeader(HttpRequest<?> request) {
        return request.getHeaders()
            .getAuthorization()
            .filter(auth -> auth.toLowerCase().startsWith(PREFIX_BASIC.toLowerCase()))
            .map(cred -> cred.substring(PREFIX_BASIC.length() + 1));
    }
    
    private Optional<String> fromBearerToken(HttpRequest<?> request) {
        return request.getHeaders()
            .getAuthorization()
            .filter(auth -> auth.toLowerCase().startsWith(PREFIX_BEARER.toLowerCase()))
            .map(token -> token.substring(PREFIX_BEARER.length() + 1).trim());
    }

    @SuppressWarnings("rawtypes")
    private boolean isManagementEndpoint(HttpRequest<?> request) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch<?, ?> method) {
            return method.getAnnotation(Endpoint.class) != null;
        }
        return false;
    }

    record BasicAuth(String username, String password) {
        static BasicAuth from(String authentication) {
            var decoded = new String(Base64.getDecoder().decode(authentication));
            var username = decoded.substring(0, decoded.indexOf(':'));
            var password = decoded.substring(decoded.indexOf(':') + 1);
            return new BasicAuth(username, password);
        }
    }
}
