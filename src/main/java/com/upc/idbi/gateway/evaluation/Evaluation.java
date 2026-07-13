package com.upc.idbi.gateway.evaluation;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String restaurantName;
    private String location;
    private String address;
    private String contactName;
    private String contactEmail;
    private String phone;

    @Enumerated(EnumType.STRING)
    private EvaluationStatus status;

    private Integer progress;
    private Integer score;

    private LocalDateTime createdAt;
}