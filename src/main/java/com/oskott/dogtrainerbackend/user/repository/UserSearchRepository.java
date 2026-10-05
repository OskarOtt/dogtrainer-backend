package com.oskott.dogtrainerbackend.user.repository;

import com.oskott.dogtrainerbackend.user.entity.User;

import java.util.List;
import java.util.UUID;

/**
 * Abstraction over the user search implementation so it can differ by datastore: a real
 * pg_trgm-backed fuzzy search in Postgres (prod), vs. a simple LIKE-based fallback on H2
 * (local/test), where pg_trgm doesn't exist. See {@code PgTrigramUserSearchRepository} and
 * {@code LikeUserSearchRepository}.
 */
public interface UserSearchRepository {

    List<User> search(String query, int limit, UUID excludedUserId);
}
