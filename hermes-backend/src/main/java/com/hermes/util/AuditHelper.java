package com.hermes.util;

import com.hermes.entity.OperationLog;
import com.hermes.entity.User;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

/**
 * 审计日志通用工具 - 所有模块的审计记录统一通过此工具写入，
 * 确保操作人 ID 和用户名始终正确。
 */
public class AuditHelper {

    /**
     * 写入审计日志。自动从 SecurityContext 获取当前登录用户信息。
     *
     * @param logMapper  OperationLogMapper
     * @param userMapper UserMapper（用于查询当前用户ID，可传null，为null时userId填0）
     * @param module     模块名: hdfs / yarn / mr / user / system
     * @param action     操作类型
     * @param target     操作目标
     * @param detail     详情描述
     */
    public static void log(OperationLogMapper logMapper, UserMapper userMapper,
                           String module, String action, String target, String detail) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = (auth != null && auth.getName() != null) ? auth.getName() : "unknown";

            // 查找用户ID
            Long userId = 0L;
            if (userMapper != null && !"unknown".equals(username)) {
                User u = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("username", username));
                if (u != null) {
                    userId = u.getId();
                }
            }

            OperationLog log = new OperationLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setClusterId(0L);
            log.setModule(module);
            log.setAction(action);
            log.setTarget(target != null ? target : "");
            log.setResult("success");
            log.setDetail(detail);
            logMapper.insert(log);
        } catch (Exception ignored) {
            // 审计日志写入失败不应影响主流程
        }
    }

    /**
     * 通过 userId 查询用户名（可用于 service 层审计日志写入）
     */
    public static String getUsernameById(UserMapper userMapper, Long userId) {
        if (userMapper == null || userId == null || userId <= 0) return "";
        try {
            User u = userMapper.selectById(userId);
            return u != null ? u.getUsername() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 简写：从 SecurityContext 获取当前用户名
     */
    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "unknown";
    }
}
