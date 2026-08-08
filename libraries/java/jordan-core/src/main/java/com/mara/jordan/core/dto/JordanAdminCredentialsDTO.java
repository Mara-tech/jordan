package com.mara.jordan.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /jordan/admin/login} : operator credentials exchanged for a session token.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanAdminCredentialsDTO {
    private String login;
    private String password;
}
