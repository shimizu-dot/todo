package com.example.todo.mapper;

import java.util.List;

import com.example.todo.model.AppUser;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {
    AppUser findByUsername(@Param("username") String username);

    long countUsers();

    int insert(AppUser user);

    List<AppUser> findAllUsers();

    AppUser findById(@Param("id") Long id);

    int updateRoleById(
            @Param("id") Long id,
            @Param("role") String role
    );

    int updateForAdmin(
            @Param("id") Long id,
            @Param("role") String role,
            @Param("enabled") boolean enabled,
            @Param("password") String password
    );

    int deleteById(@Param("id") Long id);
}
