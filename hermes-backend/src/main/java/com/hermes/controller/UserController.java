package com.hermes.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hermes.entity.OperationLog;
import com.hermes.entity.User;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.UserMapper;
import com.hermes.util.AuditHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OperationLogMapper operationLogMapper;

    private void logOp(String action, String target, String detail) {
        AuditHelper.log(operationLogMapper, userMapper, "user", action, target, detail);
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 0);
        m.put("msg", "success");
        m.put("data", data);
        return m;
    }

    private Map<String, Object> fail(int code, String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("msg", msg);
        return m;
    }

    // 密码复杂度校验：必须同时包含大写字母、小写字母、数字、特殊字符
    private String checkPasswordComplexity(String password) {
        if (password == null || password.length() < 6) {
            return "密码长度不能少于6位";
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (c >= 'A' && c <= 'Z') hasUpper = true;
            else if (c >= 'a' && c <= 'z') hasLower = true;
            else if (c >= '0' && c <= '9') hasDigit = true;
            else hasSpecial = true;
        }
        int count = 0;
        if (hasUpper) count++;
        if (hasLower) count++;
        if (hasDigit) count++;
        if (hasSpecial) count++;
        if (count < 4) {
            return "密码必须同时包含大写字母、小写字母、数字和特殊字符";
        }
        return null;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.orderByDesc("create_time");
        if (keyword != null && !keyword.isEmpty()) {
            qw.like("username", keyword)
              .or().like("email", keyword);
        }
        Page<User> p = userMapper.selectPage(new Page<>(page, pageSize), qw);
        // Do not expose password in response
        p.getRecords().forEach(u -> u.setPassword(null));
        Map<String, Object> data = new HashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return ok(data);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody User user) {
        Long count = userMapper.selectCount(
            new QueryWrapper<User>().eq("username", user.getUsername()));
        if (count > 0) {
            return fail(400, "用户名已存在");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            return fail(400, "密码不能为空");
        }
        String pwErr = checkPasswordComplexity(user.getPassword());
        if (pwErr != null) {
            return fail(400, pwErr);
        }
        // Email format validation
        if (user.getEmail() != null && !user.getEmail().isEmpty() && !user.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return fail(400, "邮箱格式不正确");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("viewer");
        }
        user.setEnabled(user.getEnabled() != null ? user.getEnabled() : true);
        userMapper.insert(user);
        user.setPassword(null);
        logOp("create", "用户名=" + user.getUsername(),
            "角色=" + user.getRole() + ", 邮箱=" + (user.getEmail() != null ? user.getEmail() : ""));
        return ok(user);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody User user) {
        User existing = userMapper.selectById(id);
        if (existing == null) {
            return fail(404, "用户不存在");
        }
        StringBuilder changes = new StringBuilder();
        User update = new User();
        update.setId(id);
        if (user.getEmail() != null) {
            update.setEmail(user.getEmail());
            changes.append("邮箱:").append(existing.getEmail()).append("→").append(user.getEmail()).append("; ");
        }
        if (user.getRole() != null) {
            update.setRole(user.getRole());
            changes.append("角色:").append(existing.getRole()).append("→").append(user.getRole()).append("; ");
        }
        if (user.getEnabled() != null) {
            update.setEnabled(user.getEnabled());
            changes.append("状态:").append(existing.getEnabled()).append("→").append(user.getEnabled()).append("; ");
        }
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        User result = userMapper.selectById(id);
        result.setPassword(null);
        String detail = changes.length() > 0 ? changes.toString() : "无变更";
        logOp("update", "用户名=" + existing.getUsername(), detail);
        return ok(result);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        String currentUsername = AuditHelper.currentUsername();
        User target = userMapper.selectById(id);
        if (target == null) {
            return fail(404, "用户不存在");
        }
        if (target.getUsername().equals(currentUsername)) {
            return fail(400, "不能删除当前登录用户");
        }
        userMapper.deleteById(id);
        logOp("delete", "用户名=" + target.getUsername(), "已删除用户");
        return ok(null);
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        String oldPassword = body.get("oldPassword");
        if (newPassword == null || newPassword.isEmpty()) {
            return fail(400, "新密码不能为空");
        }
        if (oldPassword == null || oldPassword.isEmpty()) {
            return fail(400, "旧密码不能为空");
        }
        User existing = userMapper.selectById(id);
        if (existing == null) {
            return fail(404, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, existing.getPassword())) {
            return fail(400, "旧密码不正确");
        }
        String pwErr = checkPasswordComplexity(newPassword);
        if (pwErr != null) {
            return fail(400, pwErr);
        }
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        logOp("reset-password", "用户名=" + existing.getUsername(), "密码已重置");
        return ok(null);
    }
}
