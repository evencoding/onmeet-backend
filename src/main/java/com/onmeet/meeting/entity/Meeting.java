package com.onmeet.meeting.entity;

import com.onmeet.team.entity.Team;
import com.onmeet.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "meetings")
@EntityListeners(AuditingEntityListener.class)
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "host_user_id")
    private User hostUser;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Instant scheduledAt;

    private Instant startedAt;

    private Instant endedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Meeting(Team team, User hostUser, String title, Instant scheduledAt) {
        this.team = team;
        this.hostUser = hostUser;
        this.title = title;
        this.scheduledAt = scheduledAt;
    }
}
