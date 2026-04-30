package dev.soupbase.domain;

import dev.soupbase.db.DatabaseRepository;
import dev.soupbase.domain.model.Database;
import dev.soupbase.domain.model.DatabaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class RecoveryJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);
    private static final int STUCK_THRESHOLD_SECONDS = 60;

    private final DatabaseRepository databaseRepository;

    public RecoveryJob(DatabaseRepository databaseRepository) {
        this.databaseRepository = databaseRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(STUCK_THRESHOLD_SECONDS);
        List<Database> stuck = databaseRepository.findStuckInProvisioning(cutoff);

        if (stuck.isEmpty()) {
            return;
        }

        log.warn("RecoveryJob: found {} database(s) stuck in PROVISIONING — marking FAILED", stuck.size());
        for (Database db : stuck) {
            log.warn("RecoveryJob: marking database {} FAILED (stuck since {})", db.id(), db.createdAt());
            databaseRepository.updateStatus(db.id(), DatabaseStatus.FAILED, "Timed out during provisioning");
        }
    }
}
