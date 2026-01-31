package com.onmeet.chat.repository;

import com.onmeet.chat.entity.Chat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    // 첫 로딩 최신:n개
    @Query("""
           SELECT c
           FROM Chat c
           WHERE c.meetRoomId = :roomId
           ORDER BY c.id DESC
           """)
    List<Chat> findLatestByRoomId(
            @Param("roomId") Long roomId,
            Pageable pageable
    );

    //  더보기: beforeId 이전(과거) 메시지 N개 (id < beforeId, id DESC)
    @Query("""
           SELECT c
           FROM Chat c
           WHERE c.meetRoomId = :roomId
             AND c.id < :beforeId
           ORDER BY c.id DESC
           """)
    List<Chat> findBeforeIdByRoomId(
            @Param("roomId") Long roomId,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    // 폴링/재연결용: afterId 이후(최신 방향) 메시지 조회
    @Query("""
           SELECT c
           FROM Chat c
           WHERE c.meetRoomId = :roomId
             AND c.id > :afterId
           ORDER BY c.id ASC
           """)
    List<Chat> findAfterIdByRoomId(
            @Param("roomId") Long roomId,
            @Param("afterId") Long afterId
    );
}
