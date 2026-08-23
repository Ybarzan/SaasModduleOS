package com.fleethub.security;

import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable: " + username));
        Long companyId = appUser.getCompany() != null ? appUser.getCompany().getId() : null;
        // Le flag enabled est porté par le principal : DaoAuthenticationProvider
        // lève DisabledException (403) lors de la connexion d'un compte désactivé.
        return new AppUserPrincipal(appUser.getId(), appUser.getUsername(), appUser.getPassword(),
                appUser.getRole(), companyId, appUser.isEnabled());
    }
}
