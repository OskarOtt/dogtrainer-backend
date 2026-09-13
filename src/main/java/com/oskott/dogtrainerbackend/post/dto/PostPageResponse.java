package com.oskott.dogtrainerbackend.post.dto;

import java.util.List;

public record PostPageResponse(
        List<PostResponse> items,
        String nextCursor
) {
}
