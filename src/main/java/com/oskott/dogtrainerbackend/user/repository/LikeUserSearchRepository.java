package com.oskott.dogtrainerbackend.user.repository;

import com.oskott.dogtrainerbackend.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Simple case-insensitive substring search over name/username, used wherever the runtime
 * datastore isn't Postgres (local/test, backed by H2) and {@code pg_trgm} isn't available. No
 * fuzzy/typo-tolerant ranking - just a plain {@code LIKE} match, in the same spirit as the
 * existing simple lookup-by-email flow. See {@link PgTrigramUserSearchRepository} for the
 * Postgres (prod) trigram-based search.
 */
@Repository
@Profile("!prod")
public class LikeUserSearchRepository implements UserSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<User> search(String query, int limit, UUID excludedUserId) {
        String pattern = "%" + escapeLikeWildcards(query.toLowerCase()) + "%";
        TypedQuery<User> typedQuery = entityManager.createQuery("""
                        SELECT u FROM User u
                        WHERE u.deletedAt IS NULL
                          AND (LOWER(u.name) LIKE :pattern ESCAPE '\\'
                               OR LOWER(u.username) LIKE :pattern ESCAPE '\\')
                          AND (:excludedUserId IS NULL OR u.id <> :excludedUserId)
                        ORDER BY u.name
                        """, User.class)
                .setParameter("pattern", pattern)
                .setParameter("excludedUserId", excludedUserId)
                .setMaxResults(limit);
        return typedQuery.getResultList();
    }

    private String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
