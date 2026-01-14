package com.onmeet.chat.repository;

import com.onmeet.chat.entity.Chat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    // 최초: 최근 메시지 N개 (createdAt desc)
    List<Chat> findByMeetRoomIdOrderByCreatedAtDesc(Long meetRoomId, Pageable pageable);

    // 커서: before 보다 과거 메시지 N개 (createdAt desc)
    List<Chat> findByMeetRoomIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long meetRoomId,
            Instant before,
            Pageable pageable
    );

}
