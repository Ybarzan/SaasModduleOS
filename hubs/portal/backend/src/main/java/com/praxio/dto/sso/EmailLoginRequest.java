package com.praxio.dto.sso;

/** Corps de connexion attendu par compliance-engine et Logistix (champ "email"). */
public record EmailLoginRequest(String email, String password) {}
