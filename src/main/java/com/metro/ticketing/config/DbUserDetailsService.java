package com.metro.ticketing.config;

import com.metro.ticketing.entity.User;
import com.metro.ticketing.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DbUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String contact) throws UsernameNotFoundException {
        User user = userRepository.findByContact(contact)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + contact));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getContact())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .disabled(!Boolean.TRUE.equals(user.getIsActive()))
                .build();
    }
}
