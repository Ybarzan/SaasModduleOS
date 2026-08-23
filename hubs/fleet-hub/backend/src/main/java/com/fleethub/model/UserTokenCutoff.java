package com.fleethub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_token_cutoff")
@Getter
@Setter
public class UserTokenCutoff {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime revokedBefore;
}
