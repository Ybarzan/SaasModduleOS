package com.praxio.dto.sso;

/** Corps de connexion attendu par fleet-hub (champ "username", pas "email"). */
public record UsernameLoginRequest(String username, String password) {}
