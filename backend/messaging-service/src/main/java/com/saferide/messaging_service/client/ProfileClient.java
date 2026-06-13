package com.saferide.messaging_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves userIds → display names via profile-service's internal batch
 * endpoints. A member is either a passenger or a driver, so we query both
 * and merge. Best-effort: failures yield no name (the UI falls back).
 */
@Component
public class ProfileClient {

    private static final Logger log = LoggerFactory.getLogger(ProfileClient.class);

    private final RestClient client;

    public ProfileClient(@Value("${profile.service.url:http://localhost:8002}") String baseUrl) {
        this.client = RestClient.create(baseUrl);
    }

    public Map<UUID, String> resolveNames(Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return names;
        }
        List<UUID> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        merge(names, fetch("/api/v1/profile/internal/passengers/by-ids", ids));
        merge(names, fetch("/api/v1/profile/internal/drivers/by-ids", ids));
        return names;
    }

    private List<UserSummary> fetch(String path, List<UUID> ids) {
        try {
            return client.post()
                    .uri(path)
                    .body(ids)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UserSummary>>() {});
        } catch (Exception e) {
            log.warn("name resolution {} failed: {}", path, e.getMessage());
            return List.of();
        }
    }

    private void merge(Map<UUID, String> names, List<UserSummary> summaries) {
        if (summaries == null) {
            return;
        }
        for (UserSummary s : summaries) {
            if (s.userId() != null && s.fullName() != null && !s.fullName().isBlank()) {
                names.putIfAbsent(s.userId(), s.fullName());
            }
        }
    }
}
