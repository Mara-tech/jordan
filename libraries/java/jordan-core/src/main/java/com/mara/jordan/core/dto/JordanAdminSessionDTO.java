package com.mara.jordan.core.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of {@code POST /jordan/admin/login} : the authenticated operator and the
 * time-limited token to send as {@code Authorization: Bearer <token>} on admin calls.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanAdminSessionDTO {
    private String login;
    private String role;
    /** What the role is allowed to do : {@code read}, {@code send}, {@code delete}. */
    private List<String> permissions;
    private String token;
    /** Seconds since 1970/1/1 after which the server refuses the token. */
    private long expiresAt;
}
