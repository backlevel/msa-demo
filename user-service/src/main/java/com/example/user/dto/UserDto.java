package com.example.user.dto;

import com.example.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.io.Serializable;

public class UserDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String name;
        private String email;
    }

    @Getter
    @NoArgsConstructor   // Redis 역직렬화에 필요
    @AllArgsConstructor
    public static class Response implements Serializable {
        private Long id;
        private String name;
        private String email;

        public static Response from(User user) {
            return new Response(user.getId(), user.getName(), user.getEmail());
        }
    }
}
