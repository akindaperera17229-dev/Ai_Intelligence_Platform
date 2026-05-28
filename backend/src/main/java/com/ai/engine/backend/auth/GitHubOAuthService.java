package com.ai.engine.backend.auth;

import com.ai.engine.backend.model.Tenant;
import com.ai.engine.backend.model.TenantUser;
import com.ai.engine.backend.repository.TenantRepository;
import com.ai.engine.backend.repository.TenantUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * GitHubOAuthService: handles the GitHub OAuth2 callback flow.
 * 
 * Flow:
 * 1. Exchange temporary code for GitHub access token
 * 2. Fetch GitHub user profile
 * 3. Create or update Tenant + TenantUser in DB
 * 4. Return our own JWT for frontend
 */
@Service
@Slf4j
public class GitHubOAuthService {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private WebClient.Builder webClientBuilder;

    /**
     * Handle the GitHub OAuth callback: exchange code for token, get user, create/update tenant.
     */
    public String handleCallback(String code) {
        log.debug("Processing GitHub OAuth callback with code: {}", code.substring(0, 10) + "...");

        // Step 1: Exchange code for GitHub access token
        String githubToken = exchangeCodeForToken(code);
        log.debug("Successfully obtained GitHub access token");

        // Step 2: Fetch GitHub user profile using the token
        GitHubUserProfile profile = fetchGitHubProfile(githubToken);
        log.info("GitHub user authenticated: {} (id: {})", profile.login(), profile.id());

        // Step 3: Find or create Tenant (one tenant per GitHub user)
        Tenant tenant = tenantRepository.findByGithubId(profile.id())
            .orElseGet(() -> {
                Tenant newTenant = Tenant.builder()
                    .name(profile.name() != null ? profile.name() : profile.login())
                    .slug(sanitizeSlug(profile.login().toLowerCase()))
                    .githubId(profile.id())
                    .avatarUrl(profile.avatarUrl())
                    .plan("FREE")
                    .isActive(true)
                    .build();
                Tenant saved = tenantRepository.save(newTenant);
                log.info("Created new tenant: {} ({})", saved.getName(), saved.getId());
                return saved;
            });

        // Step 4: Find or create TenantUser (admin account within the tenant)
        TenantUser user = tenantUserRepository.findByGithubId(profile.id())
            .orElseGet(() -> {
                TenantUser newUser = TenantUser.builder()
                    .tenant(tenant)
                    .githubId(profile.id())
                    .githubLogin(profile.login())
                    .email(profile.email())
                    .avatarUrl(profile.avatarUrl())
                    .role("ADMIN")
                    .isActive(true)
                    .build();
                TenantUser saved = tenantUserRepository.save(newUser);
                log.info("Created new tenant user: {} in tenant {}", saved.getGithubLogin(), tenant.getId());
                return saved;
            });

        // Update last login time and refresh user data
        user.setGithubLogin(profile.login()); // username can change on GitHub
        user.setLastLoginAt(LocalDateTime.now());
        tenantUserRepository.save(user);

        // Step 5: Issue our own JWT token
        String jwt = jwtTokenProvider.generateToken(user);
        log.debug("Issued JWT token for user: {}", user.getId());
        return jwt;
    }

    /**
     * Exchange a temporary GitHub authorization code for an access token.
     */
    private String exchangeCodeForToken(String code) {
        try {
            Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .bodyValue("client_id=" + clientId + "&client_secret=" + clientSecret + "&code=" + code)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("GitHub did not return access_token in response");
            }

            return (String) response.get("access_token");
        } catch (WebClientResponseException e) {
            log.error("GitHub OAuth token exchange failed: {}", e.getStatusCode());
            throw new RuntimeException("GitHub OAuth token exchange failed", e);
        } catch (Exception e) {
            log.error("Unexpected error during GitHub OAuth token exchange", e);
            throw new RuntimeException("OAuth token exchange failed", e);
        }
    }

    /**
     * Fetch GitHub user profile using the access token.
     */
    private GitHubUserProfile fetchGitHubProfile(String accessToken) {
        try {
            return webClientBuilder.build()
                .get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(GitHubUserProfile.class)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch GitHub user profile: {}", e.getStatusCode());
            throw new RuntimeException("Failed to fetch GitHub user profile", e);
        } catch (Exception e) {
            log.error("Unexpected error fetching GitHub user profile", e);
            throw new RuntimeException("Failed to fetch GitHub user profile", e);
        }
    }

    /**
     * Sanitize GitHub username to be a valid URL slug.
     */
    private String sanitizeSlug(String slug) {
        return slug.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
    }

    /**
     * GitHub user profile from GitHub API /user endpoint.
     */
    public record GitHubUserProfile(
        String id,
        String login,
        String name,
        String email,
        String avatar_url
    ) {
        public String avatarUrl() {
            return avatar_url;
        }
    }
}
