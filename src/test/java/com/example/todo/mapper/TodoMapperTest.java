package com.example.todo.mapper;

import com.example.todo.model.AppUser;
import com.example.todo.model.Priority;
import com.example.todo.model.Todo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tododb;MODE=LEGACY;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
@Transactional
class TodoMapperTest {

    @Autowired
    private TodoMapper todoMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertAndFindById_Works() {
        AppUser user = new AppUser();
        user.setUsername("mapper_user_1");
        user.setPassword("raw");
        user.setRole("USER");
        user.setEnabled(true);
        userMapper.insert(user);

        Todo todo = new Todo();
        todo.setTitle("mapper test title");
        todo.setAuthor("mapper test author");
        todo.setUserId(user.getId());
        todo.setPriority(Priority.HIGH);
        todo.setCategoryId(1L);
        todo.setDeadline(LocalDate.of(2026, 5, 1));
        todo.setCompleted(false);

        int inserted = todoMapper.insert(todo);

        assertEquals(1, inserted);
        assertNotNull(todo.getId());

        Todo found = todoMapper.findById(todo.getId());
        assertNotNull(found);
        assertEquals("mapper test title", found.getTitle());
        assertEquals(user.getId(), found.getUserId());
    }

    @Test
    void findByIdForUser_OtherUserCannotAccess() {
        AppUser owner = new AppUser();
        owner.setUsername("owner_user");
        owner.setPassword("raw");
        owner.setRole("USER");
        owner.setEnabled(true);
        userMapper.insert(owner);

        AppUser other = new AppUser();
        other.setUsername("other_user");
        other.setPassword("raw");
        other.setRole("USER");
        other.setEnabled(true);
        userMapper.insert(other);

        Todo todo = new Todo();
        todo.setTitle("owner todo");
        todo.setAuthor("owner");
        todo.setUserId(owner.getId());
        todo.setPriority(Priority.MEDIUM);
        todo.setCategoryId(1L);
        todo.setCompleted(false);
        todoMapper.insert(todo);

        Todo forOwner = todoMapper.findByIdForUser(todo.getId(), owner.getId());
        Todo forOther = todoMapper.findByIdForUser(todo.getId(), other.getId());

        assertNotNull(forOwner);
        assertNull(forOther);
    }
}
