package com.incokalk.security;

import com.incokalk.model.Company;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Miroir de {@link RolesAllowed} pour le palier commercial plutot que le role.
 * Plan minimum requis -- Company.Plan est un ordre total (FREE < STARTER < PRO
 * < ENTERPRISE), donc une seule valeur suffit contrairement au tableau de
 * RolesAllowed. Contrairement au role (qui varie souvent methode par methode
 * dans un meme controleur -- GET ouvert a USER, DELETE reserve a ADMIN), le
 * palier commercial est presque toujours uniforme sur tout un controleur
 * (tout le Hub Entrepot est Enterprise). Utilisable sur une classe entiere
 * pour eviter d'annoter chaque endpoint individuellement. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPlan {
    Company.Plan value();
}
