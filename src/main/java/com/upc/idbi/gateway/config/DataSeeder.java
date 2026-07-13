package com.upc.idbi.gateway.config;

import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.evaluation.EvaluationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final EvaluationRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;

        repository.save(Evaluation.builder()
                .restaurantName("Restaurante El Rincón")
                .location("CDMX · Insurgentes")
                .address("Av. Insurgentes Sur 1234, CDMX")
                .contactName("Roberto García")
                .contactEmail("roberto.garcia@elrincon.com")
                .phone("+52 55 1234 5678")
                .status(EvaluationStatus.COMPLETADO)
                .progress(72)
                .score(72)
                .createdAt(LocalDateTime.now())
                .build());

        repository.save(Evaluation.builder()
                .restaurantName("Café Central Gourmet")
                .location("CDMX · Polanco")
                .status(EvaluationStatus.BORRADOR)
                .progress(58)
                .score(58)
                .createdAt(LocalDateTime.now())
                .build());

        repository.save(Evaluation.builder()
                .restaurantName("Pizza Palace Express")
                .location("CDMX · Coyoacán")
                .status(EvaluationStatus.ENVIADO)
                .progress(85)
                .score(85)
                .createdAt(LocalDateTime.now())
                .build());

        repository.save(Evaluation.builder()
                .restaurantName("Taco Tradicional MX")
                .location("CDMX · Xochimilco")
                .status(EvaluationStatus.EN_ANALISIS)
                .progress(44)
                .score(44)
                .createdAt(LocalDateTime.now())
                .build());
    }
}