package com.yfkzdk.chatbot.modules.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yfkzdk.chatbot.common.exception.Asserts;
import com.yfkzdk.chatbot.domain.ChatUserDetails;
import com.yfkzdk.chatbot.modules.user.dto.UserLoginParam;
import com.yfkzdk.chatbot.modules.user.dto.UserRegisterParam;
import com.yfkzdk.chatbot.modules.user.mapper.UserMapper;
import com.yfkzdk.chatbot.modules.user.model.User;
import com.yfkzdk.chatbot.modules.user.service.UserService;
import com.yfkzdk.chatbot.security.util.JwtTokenUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户业务实现 — 参考 mall-tiny UmsAdminServiceImpl
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(JwtTokenUtil jwtTokenUtil, PasswordEncoder passwordEncoder) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User register(UserRegisterParam param) {
        // 查重
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(User::getUsername, param.getUsername());
        if (!list(wrapper).isEmpty()) {
            return null;
        }

        User user = new User();
        BeanUtils.copyProperties(param, user);
        user.setPassword(passwordEncoder.encode(param.getPassword()));
        user.setStatus(1);
        user.setQuotaTotal(100);
        user.setQuotaUsed(0);
        user.setCreatedAt(LocalDateTime.now());

        baseMapper.insert(user);
        return user;
    }

    @Override
    public String login(UserLoginParam param) {
        UserDetails userDetails;
        try {
            userDetails = loadUserByUsername(param.getUsername());
        } catch (UsernameNotFoundException e) {
            return null;
        }

        if (!passwordEncoder.matches(param.getPassword(), userDetails.getPassword())) {
            return null;
        }
        if (!userDetails.isEnabled()) {
            Asserts.fail("账号已被禁用");
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return jwtTokenUtil.generateToken(userDetails);
    }

    @Override
    public String refreshToken(String oldToken) {
        return jwtTokenUtil.refreshToken(oldToken);
    }

    @Override
    public User getByUsername(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(User::getUsername, username);
        List<User> list = list(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = getByUsername(username);
        if (user != null) {
            return new ChatUserDetails(user);
        }
        throw new UsernameNotFoundException("用户名或密码错误: " + username);
    }
}
