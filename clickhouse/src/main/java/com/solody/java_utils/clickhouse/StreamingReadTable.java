package com.solody.java_utils.clickhouse;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

abstract public class StreamingReadTable {

     Vertx vertx;

     public StreamingReadTable(Vertx vertx) {
         this.vertx = vertx;
     }

    public Future<Void> streamRead(
            String clickhouseHost,
            String username, String password,
            String sql,
            Supplier<Boolean> canRun,
            Consumer<String> readLine,
            long maxRead
    ) {

        return Future.future(promise -> {
            try {
                int parallelism = Runtime.getRuntime().availableProcessors() * 2;
                long maxExecuteTime = 2;
                TimeUnit maxExecuteTimeUnit = TimeUnit.MINUTES;
                WorkerExecutor executor = this.vertx.createSharedWorkerExecutor(
                        "worker-pool-stream-read", parallelism, maxExecuteTime, maxExecuteTimeUnit);


                String query = URLEncoder.encode(sql, StandardCharsets.UTF_8);
                String url = String.format("http://%s/?user=%s&password=%s&query=%s",clickhouseHost, username, password, query);

                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();

                InputStream in;
                if (code == 200) {
                    in = conn.getInputStream();
                } else {
                    in = conn.getErrorStream();
                }

                AtomicLong atomicLong = new AtomicLong();
                long timer = vertx.setPeriodic(1000, t -> {
                    System.out.printf("Reading %s records %n", atomicLong.get());
                });

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                AtomicBoolean isReading = new AtomicBoolean(false);
                AtomicBoolean doneReading = new AtomicBoolean(false);

                vertx.setPeriodic(10, t -> {
                    if (doneReading.get()) {
                        if (!isReading.get()) {
                            vertx.cancelTimer(timer);
                            vertx.cancelTimer(t);
                            promise.complete(null);
                        }
                    }
                    else {
                        if (!isReading.get()) {
                            isReading.set(true);
                            executor.executeBlocking(() -> {
                                long start = System.currentTimeMillis();
                                while (true) {
                                    if (System.currentTimeMillis() - start > 120 * 1000) {
                                        break;
                                    }
                                    if (!canRun.get()) {
                                        break;
                                    }
                                    try {
                                        String line = reader.readLine();
                                        if (line == null) {
                                            doneReading.set(true);
                                            break;
                                        }

                                        long cr = atomicLong.incrementAndGet();

                                        if (maxRead > 0 && cr >= maxRead) {
                                            System.out.printf("Breaking with cr:%s >= maxRead:%s %n", atomicLong.get(), maxRead);
                                            doneReading.set(true);
                                            break;
                                        }

                                        readLine.accept(line);
                                    } catch (IOException e) {
                                        break;
                                    }
                                }
                                return null;
                            }).onComplete(ar -> {
                                isReading.set(false);
                            });
                        }
                    }
                });


            } catch (Throwable throwable) {
                promise.complete();
            }
        });
    }


}
