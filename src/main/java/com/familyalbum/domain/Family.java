package com.familyalbum.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "family")
public class Family {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 16)
    private String inviteCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Family() {}

    public Family(String name, String inviteCode) {
        this.name = name;
        this.inviteCode = inviteCode;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getInviteCode() { return inviteCode; }
    public Instant getCreatedAt() { return createdAt; }
}
