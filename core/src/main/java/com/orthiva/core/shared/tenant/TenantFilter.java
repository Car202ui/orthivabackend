package com.orthiva.core.shared.tenant;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.orthiva.core.shared.web.DomainException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs after Spring Security: turns the JWT into a {@link TenantContext.Actor} for the
 * rest of the request and clears it afterwards. Plain {@link Filter} (no Spring base
 * class) so it can be proxied and is registered only inside the security chain.
 */
public class TenantFilter implements Filter {

    private final ActorResolver resolver;

    public TenantFilter(ActorResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                resolver.resolve(jwt).ifPresent(TenantContext::set);
            }
            chain.doFilter(request, response);
        } catch (DomainException e) {
            // Filters run outside @RestControllerAdvice; answer with the same problem+json shape.
            var res = (HttpServletResponse) response;
            res.setStatus(e.status().value());
            res.setContentType("application/problem+json");
            res.getWriter().write("{\"status\":" + e.status().value() + ",\"code\":\"" + e.code()
                    + "\",\"detail\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        } finally {
            TenantContext.clear();
        }
    }
}
