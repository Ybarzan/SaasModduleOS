package com.finance.service;

import com.finance.dto.AuthResponse;
import com.finance.dto.LoginRequest;
import com.finance.dto.RegisterRequest;
import com.finance.model.Company;
import com.finance.model.Role;
import com.finance.model.User;
import com.finance.repository.CompanyRepository;
import com.finance.repository.UserRepository;
import com.finance.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email");
        }

        Company company = Company.builder().name(request.companyName().trim()).build();
        companyRepository.save(company);

        User owner = User.builder()
                .company(company)
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .displayName(request.displayName().trim())
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(owner);

        return toAuthResponse(owner);
    }

    private AuthResponse toAuthResponse(User user) {
        var company = user.getCompany();
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name(),
                company != null ? company.getId() : null);
        return new AuthResponse(
                token,
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                company != null ? company.getId() : null,
                company != null ? company.getName() : null);
    }
}
