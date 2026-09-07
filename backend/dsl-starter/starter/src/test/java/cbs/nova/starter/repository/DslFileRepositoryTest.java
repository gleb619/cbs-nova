package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DslFileRepositoryTest {

  @TempDir
  Path root;

  private final DslFileRepository repository = new DslFileRepository();

  @Test
  void writePublishesContentToTargetPath() throws IOException {
    repository.write(root, "dsl/LoanDsl.java", "class LoanDsl {}");

    assertThat(Files.readString(root.resolve("dsl/LoanDsl.java")))
            .isEqualTo("class LoanDsl {}");
  }

  @Test
  void writeReplacesExistingContent() throws IOException {
    repository.write(root, "dsl/LoanDsl.java", "v1");
    repository.write(root, "dsl/LoanDsl.java", "v2");

    assertThat(Files.readString(root.resolve("dsl/LoanDsl.java"))).isEqualTo("v2");
  }

  @Test
  void writeLeavesNoTempFilesBehind() throws IOException {
    repository.write(root, "dsl/LoanDsl.java", "content");

    try (var stream = Files.list(root.resolve("dsl"))) {
      assertThat(stream.map(p -> p.getFileName().toString()))
              .containsExactly("LoanDsl.java");
    }
  }

  @Test
  void concurrentWritersToSamePathNeverYieldPartialContent() throws Exception {
    int writerCount = 4;
    int iterations = 50;
    String marker = "x".repeat(64 * 1024);
    List<String> contents = IntStream.range(0, writerCount)
            .mapToObj(i -> "version-" + i + "-" + marker)
            .toList();

    List<String> observed = new CopyOnWriteArrayList<>();
    ExecutorService pool = Executors.newFixedThreadPool(writerCount + 1);
    CountDownLatch ready = new CountDownLatch(writerCount + 1);
    CountDownLatch go = new CountDownLatch(1);
    CountDownLatch writersDone = new CountDownLatch(writerCount);
    try {
      for (int w = 0; w < writerCount; w++) {
        int index = w;
        pool.submit(() -> {
          ready.countDown();
          go.await();
          for (int i = 0; i < iterations; i++) {
            repository.write(root, "dsl/Same.java", contents.get(index));
          }
          writersDone.countDown();
          return null;
        });
      }
      pool.submit(() -> {
        ready.countDown();
        go.await();
        Path target = root.resolve("dsl/Same.java");
        while (!writersDone.await(1, TimeUnit.MILLISECONDS)) {
          if (Files.exists(target)) {
            observed.add(Files.readString(target));
          }
        }
        if (Files.exists(target)) {
          observed.add(Files.readString(target));
        }
        return null;
      });

      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
    } finally {
      pool.shutdown();
      assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(observed).isNotEmpty();
    assertThat(observed).allSatisfy(content ->
            assertThat(contents).as("read must be one complete writer version").contains(content));
  }
}
