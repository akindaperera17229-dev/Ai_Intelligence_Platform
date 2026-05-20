package com.ai.engine.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "engineering_events")

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter

public class EngineeringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventType;
    private String source;
    private String userId;
    private String engineerName;

    private LocalDateTime timestamp;
    @Column(columnDefinition = "TEXT")
    private String metadata;

   

}
