package com.hermes.security;

import com.hermes.entity.User;
import com.hermes.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Query user from MySQL database
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User> qw =
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.eq("username", username);
        User user = userMapper.selectOne(qw);

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        if (user.getEnabled() != null && !user.getEnabled()) {
            throw new UsernameNotFoundException("User disabled: " + username);
        }

        String role = user.getRole() != null ? user.getRole().toUpperCase() : "VIEWER";
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            Collections.singletonList(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)
            )
        );
    }
}
