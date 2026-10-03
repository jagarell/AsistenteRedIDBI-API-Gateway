package com.upc.idbi.gateway.minuta;

import com.upc.idbi.gateway.notification.MinutaNotifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Recordatorio diario a los técnicos: un borrador vence {@code draft-ttl-days}
 * días después de crearse y se avisa una sola vez cuando faltan
 * {@code warn-days-before} días o menos (también si ya venció y nunca se avisó).
 */
@Component
@RequiredArgsConstructor
public class MinutaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(MinutaReminderJob.class);

    private final MinutaRepository repository;
    private final MinutaNotifier notifier;

    @Value("${app.reminders.draft-ttl-days:7}")
    private long draftTtlDays;

    @Value("${app.reminders.warn-days-before:2}")
    private long warnDaysBefore;

    @Scheduled(cron = "${app.reminders.cron:0 0 9 * * *}", zone = "America/Lima")
    public void run() {
        remind(LocalDateTime.now());
    }

    /** Separado de {@link #run()} para poder probarlo con una fecha fija. */
    @Transactional
    public int remind(LocalDateTime now) {
        int sent = 0;
        for (Minuta minuta : repository.findByStatusAndExpiryReminderSentAtIsNull(MinutaStatus.BORRADOR)) {
            LocalDateTime due = minuta.getCreatedAt().plusDays(draftTtlDays);
            long daysLeft = Duration.between(now, due).toDays();
            if (daysLeft > warnDaysBefore) {
                continue;
            }
            if (notifier.draftExpiring(minuta, Math.max(daysLeft, 0))) {
                minuta.setExpiryReminderSentAt(now);
                repository.save(minuta);
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Avisos de borradores por vencer enviados: {}", sent);
        }
        return sent;
    }
}
