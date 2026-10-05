package com.oskott.dogtrainerbackend.user.repository;

import com.oskott.dogtrainerbackend.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Fuzzy, typo-tolerant user search backed by Postgres pg_trgm ({@code similarity()}/{@code %}),
 * using the GIN trigram indexes created in {@code 030-add-users-trgm-search.xml}. Only active in
 * {@code prod}, where the runtime datastore is Postgres - see {@link LikeUserSearchRepository}
 * for the local/test (H2) fallback.
 */
@Repository
@Profile("prod")
public class PgTrigramUserSearchRepository implements UserSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<User> search(String query, int limit, UUID excludedUserId) {
        return entityManager.createNativeQuery("""
                        SELECT * FROM users
                        WHERE deleted_at IS NULL
                          AND (name % :query OR username % :query)
                          AND (:excludedUserId IS NULL OR id <> :excludedUserId)
                        ORDER BY GREATEST(similarity(name, :query), similarity(username, :query)) DESC
                        LIMIT :limit
                        """, User.class)
                .setParameter("query", query)
                .setParameter("limit", limit)
                .setParameter("excludedUserId", excludedUserId)
                .getResultList();
    }
}
