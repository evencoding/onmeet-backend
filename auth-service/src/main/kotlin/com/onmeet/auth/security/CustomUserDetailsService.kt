package com.onmeet.auth.security

import com.onmeet.auth.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        return userRepository.findByEmail(username)
            .orElseThrow { UsernameNotFoundException("User not found with email: $username") }
    }
}
