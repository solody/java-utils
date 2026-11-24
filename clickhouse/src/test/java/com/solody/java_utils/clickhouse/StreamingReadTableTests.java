package com.solody.java_utils.clickhouse;

import com.solody.java_utils.test.PreCommandExtension;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@ExtendWith(VertxExtension.class)
public class StreamingReadTableTests {

    private static final Path COMPOSE_FILE =
            PreCommandExtension.classpathResourceAsTempFile("docker/docker-compose.yaml");

    @RegisterExtension
    static final PreCommandExtension ext = new PreCommandExtension(
            List.of("docker compose -f \"" + COMPOSE_FILE + "\" -p java-utils-clickhouse up clickhouse -d"),
            List.of("docker compose -f \"" + COMPOSE_FILE + "\" -p java-utils-clickhouse down"),
            List.of(List.of("localhost", "8123")),
            2 * 1000L
    );

    @AfterAll
    static void cleanupComposeFile() throws IOException {
        Files.deleteIfExists(COMPOSE_FILE);
    }

    @Test
    public void testStreamingReadTable(Vertx vertx, VertxTestContext testContext) {
        new StreamingReadTable(vertx) {
            public Future<Void> run() {
                return streamRead(
                        "localhost:8123", "default", "123456",
                        "SELECT * FROM system.tables FORMAT JSON",
                        () -> true,
                        log::info,
                        0
                ).compose(v -> Future.future(promise -> {
                    promise.complete(null);
                }));

            }
        }.run().onComplete(promise -> {
            log.info("streaming read table complete.");
            testContext.completeNow();
        });
    }
}
