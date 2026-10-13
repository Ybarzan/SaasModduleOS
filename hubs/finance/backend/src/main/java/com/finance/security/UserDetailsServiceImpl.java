package com.finance.security;

import com.finance.model.User;
import com.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable: " + email));
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPassword(), user.getRole(),
                user.getCompany() != null ? user.getCompany().getId() : null, user.isEnabled());
    }
}
