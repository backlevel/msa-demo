package com.example.user.service;

import com.example.user.domain.User;
import com.example.user.domain.UserRepository;
import com.example.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 유저 생성 - 전체 목록 캐시 무효화
     */
    @Transactional
    @CacheEvict(value = "users-all", allEntries = true)
    public UserDto.Response createUser(UserDto.CreateRequest request) {
        log.info("[Cache] users-all 캐시 삭제 - 신규 유저 등록");
        User user = User.create(request.getName(), request.getEmail());
        return UserDto.Response.from(userRepository.save(user));
    }

    /**
     * 유저 단건 조회
     * - 캐시 키: "users::{id}"
     * - TTL: 30분 (유저 정보는 변경 빈도 낮음)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public UserDto.Response getUser(Long id) {
        log.info("[Cache MISS] users::{} - DB에서 조회", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다. id=" + id));
        return UserDto.Response.from(user);
    }

    /**
     * 전체 유저 조회 - TTL 5분
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "users-all", key = "'all'")
    public List<UserDto.Response> getAllUsers() {
        log.info("[Cache MISS] users-all::all - DB에서 전체 조회");
        return userRepository.findAll().stream()
                .map(UserDto.Response::from)
                .collect(Collectors.toList());
    }
}
