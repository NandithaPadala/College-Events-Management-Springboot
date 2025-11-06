package com.example.sb.demo.repository;

import com.example.sb.demo.entity.Event;
import com.example.sb.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByCreatedBy(User user);
    List<Event> findByEventDateAfterOrderByEventDateAsc(LocalDateTime date);
    List<Event> findByEventDateBeforeOrderByEventDateDesc(LocalDateTime date);
    List<Event> findAllByOrderByEventDateDesc();
}