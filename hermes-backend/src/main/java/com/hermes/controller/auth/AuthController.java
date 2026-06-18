package com.hermes.controller.auth;

import com.hermes.entity.User;
import com.hermes.mapper.UserMapper;
import com.hermes.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            Map<String, Object> m = new HashMap<>();
            m.put("code", 401); m.put("msg", "用户名和密码不能为空"); m.put("data", null);
            return m;
        }

        try {
            // Query user from MySQL
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            qw.eq("username", username);
            User user = userMapper.selectOne(qw);

            if (user == null) {
                Map<String, Object> m = new HashMap<>();
                m.put("code", 401); m.put("msg", "用户不存在"); m.put("data", null);
                return m;
            }

            if (user.getEnabled() != null && !user.getEnabled()) {
                Map<String, Object> m = new HashMap<>();
                m.put("code", 401); m.put("msg", "账户已被禁用"); m.put("data", null);
                return m;
            }

            // Verify password
            if (!passwordEncoder.matches(password, user.getPassword())) {
                Map<String, Object> m = new HashMap<>();
                m.put("code", 401); m.put("msg", "密码错误"); m.put("data", null);
                return m;
            }

            // Generate token
            String token = jwtUtil.generateToken(username);

            // Update last login time
            try {
                user.setLastLoginTime(LocalDateTime.now());
                userMapper.updateById(user);
            } catch (Exception ignored) {}

            Map<String, Object> data = new HashMap<>();
            data.put("code", 0); data.put("msg", "success");
            Map<String, Object> dataInner = new HashMap<>();
            dataInner.put("token", token); dataInner.put("username", username);
            data.put("data", dataInner);
            return data;
        } catch (Exception e) {
            Map<String, Object> m = new HashMap<>();
            m.put("code", 500); m.put("msg", "登录失败: " + e.getMessage()); m.put("data", null);
            return m;
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, Object> m = new HashMap<>();
            m.put("code", 401); m.put("msg", "Not authenticated"); m.put("data", null);
            return m;
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .map(a -> a.substring(5).toLowerCase())
                .orElse("viewer");

        String email = "";
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            qw.eq("username", userDetails.getUsername());
            User u = userMapper.selectOne(qw);
            if (u != null) {
                email = u.getEmail() != null ? u.getEmail() : "";
            }
        } catch (Exception ignored) {}

        Map<String, Object> data = new HashMap<>();
        data.put("username", userDetails.getUsername());
        data.put("role", role);
        data.put("email", email);
        return Map.of(
            "code", 0,
            "msg", "success",
            "data", data
        );
    }
}