package com.solody.java_utils.test;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Order(Integer.MIN_VALUE)
public class PreCommandExtension implements TestInstancePreConstructCallback, TestInstancePreDestroyCallback {

  protected static Logger logger = LoggerFactory.getLogger(PreCommandExtension.class);

  protected List<String> setupCommands;
  protected List<String> teardownCommands;
  protected List<List<String>> healthPorts;
  // Optional extra delay (ms) after starting docker services and before health checks.
  protected long waitToExecuteTestMillis;
  protected long waitToFinishTeardownMillis;

  public PreCommandExtension(List<String> setupCommands, List<String> teardownCommands, List<List<String>> healthPorts, long waitToExecuteTestMillis) {
    this.setupCommands = setupCommands;
    this.healthPorts = healthPorts;
    this.teardownCommands = teardownCommands;
    this.waitToExecuteTestMillis = waitToExecuteTestMillis;
    this.waitToFinishTeardownMillis = waitToExecuteTestMillis;
  }

  /**
   * Copies a classpath resource to a temp file so external tools (e.g. {@code docker compose -f}) can read it.
   * The temp file is marked {@link java.io.File#deleteOnExit()}.
   */
  public static Path classpathResourceAsTempFile(String resourcePath) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = PreCommandExtension.class.getClassLoader();
    }
    try (InputStream in = cl.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalArgumentException("Classpath resource not found: " + resourcePath);
      }
      String fileName = Path.of(resourcePath).getFileName().toString();
      Path temp = Files.createTempFile("classpath-", "-" + fileName);
      temp.toFile().deleteOnExit();
      Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
      return temp;
    } catch (IOException e) {
      throw new RuntimeException("Failed to materialize classpath resource: " + resourcePath, e);
    }
  }

  @Override
  @NullMarked
  public ExtensionContextScope getTestInstantiationExtensionContextScope(ExtensionContext rootContext) {
    return ExtensionContextScope.TEST_METHOD;
  }

  @Override
  @NullMarked
  public void preConstructTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext context) {
    runPreCommands(waitToExecuteTestMillis);
  }

  @Override
  @NullMarked
  public void preDestroyTestInstance(ExtensionContext context) {
    runAfterCommands(waitToFinishTeardownMillis);
  }

  private static int runCommand(String command) throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.command("bash", "-c", command);
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        System.out.println(line);
      }
    }

    return process.waitFor();
  }

  public static boolean isPortReachable(String host, int port, int timeoutMillis) {
    try (Socket socket = new Socket()) {
      // Attempt to connect within a specified timeout
      socket.connect(new java.net.InetSocketAddress(host, port), timeoutMillis);
      return true; // Connection successful, port is open/reachable
    } catch (IOException e) {
      return false; // Connection refused, timed out, or other error
    }
  }

  private static void runCommands(List<String> commands) {
    if (commands == null || commands.isEmpty()) {
      return;
    }
    for (String command : commands) {
      try {
        int exitCode = runCommand(command);
        if (exitCode != 0) {
          throw new IllegalStateException("Command failed with exit code " + exitCode + ": " + command);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void runPreCommands(long waitToComplete){
    runCommands(setupCommands);

    if (waitToComplete > 0) {
      try {
        Thread.sleep(waitToComplete);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    if (healthPorts == null || healthPorts.isEmpty()) {
      return;
    }

    logger.info("Checking health ports: {}", healthPorts);
    AtomicBoolean allPortsHealthy = new AtomicBoolean(false);

    int maxRounds = 120;
    long sleepMs = 1000L;
    int timeoutMillis = 500;

    for (int round = 0; round < maxRounds && !allPortsHealthy.get(); round++) {
      allPortsHealthy.set(true);
      for (List<String> hostPort : healthPorts) {
        if (hostPort == null || hostPort.size() < 2) {
          throw new IllegalArgumentException("healthPorts entries must be [host, port], got: " + hostPort);
        }

        String host = hostPort.get(0);
        int port = Integer.parseInt(hostPort.get(1));

        if (!isPortReachable(host, port, timeoutMillis)) {
          allPortsHealthy.set(false);
          logger.info("Service {}:{} is not reachable yet (round {}/{}).", host, port, round + 1, maxRounds);
          break;
        } else {
          logger.info("Service {}:{} is reachable.", host, port);
        }
      }

      if (!allPortsHealthy.get()) {
        try {
          Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
    }

    if (!allPortsHealthy.get()) {
      throw new IllegalStateException("Health ports not reachable after " + maxRounds + " rounds: " + healthPorts);
    }

  }


  public void runAfterCommands(long waitToComplete) {
    runCommands(teardownCommands);
    if (waitToComplete > 0) {
      try {
        Thread.sleep(waitToComplete);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
  }
}
