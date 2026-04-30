package dev.soupbase.domain;

import dev.soupbase.db.DatabaseRepository;
import dev.soupbase.domain.model.Database;
import dev.soupbase.domain.model.DatabaseStatus;
import dev.soupbase.infra.HostedClusterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningService.class);

    private final HostedClusterClient hostedClusterClient;
    private final DatabaseRepository databaseRepository;

    public ProvisioningService(HostedClusterClient hostedClusterClient, DatabaseRepository databaseRepository) {
        this.hostedClusterClient = hostedClusterClient;
        this.databaseRepository = databaseRepository;
    }

    @Async
    public void provision(Database database, String plainPassword) {
        try {
            hostedClusterClient.createRole(database.pgUsername(), plainPassword);
            hostedClusterClient.createDatabase(database.pgDatabaseName(), database.pgUsername());
            hostedClusterClient.grantPrivileges(database.pgDatabaseName(), database.pgUsername());
            databaseRepository.updateStatus(database.id(), DatabaseStatus.ACTIVE, null);
            log.info("Provisioned database {} successfully", database.id());
        } catch (Exception e) {
            log.error("Failed to provision database {}: {}", database.id(), e.getMessage(), e);
            databaseRepository.updateStatus(database.id(), DatabaseStatus.FAILED, e.getMessage());
        }
    }

    @Async
    public void deprovision(Database database) {
        try {
            hostedClusterClient.dropDatabase(database.pgDatabaseName());
            hostedClusterClient.dropRole(database.pgUsername());
            databaseRepository.updateStatus(database.id(), DatabaseStatus.DELETED, null);
            log.info("Deprovisioned database {} successfully", database.id());
        } catch (Exception e) {
            log.error("Failed to deprovision database {}: {}", database.id(), e.getMessage(), e);
            databaseRepository.updateStatus(database.id(), DatabaseStatus.FAILED, e.getMessage());
        }
    }
}
