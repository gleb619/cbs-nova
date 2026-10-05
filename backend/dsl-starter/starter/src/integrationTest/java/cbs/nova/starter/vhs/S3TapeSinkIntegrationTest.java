package cbs.nova.starter.vhs;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import cbs.nova.starter.vhs.replay.VhsTapeReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class S3TapeSinkIntegrationTest {

  private static final String BUCKET = "vhs-test";
  private static final String PREFIX = "tapes/";

  @Container
  @SuppressWarnings("resource")
  private final GenericContainer<?> minio = new GenericContainer<>(
          DockerImageName.parse("minio/minio:latest"))
          .withEnv("MINIO_ROOT_USER", "minioadmin")
          .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
          .withCommand("server", "/data", "--console-address", ":9001")
          .withExposedPorts(9000)
          .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

  private S3Client s3Client;
  private ObjectMapper objectMapper;
  private Path stagingDir;

  @BeforeEach
  void setUp() throws Exception {
    objectMapper = JsonMapper.builder().build();
    stagingDir = Files.createTempDirectory("vhs-s3-test");
    s3Client = S3Client.builder()
            .endpointOverride(
                    new URI("http://" + minio.getHost() + ":" + minio.getMappedPort(9000)))
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("minioadmin", "minioadmin")))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .build();
    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
  }

  @Test
  void uploadsClosedTapeToMinio() throws Exception {
    S3TapeSink sink = sink(0L);
    String runId = "run-minio-1";
    sink.start(runId, "Ping", "corr-1");
    sink.append(runId, runStartedEvent());
    sink.append(runId, callStartEvent());
    long count = sink.close(runId);

    assertThat(count).isEqualTo(2L);

    Path downloaded = stagingDir.resolve("downloaded.jsonl");
    GetObjectRequest request = GetObjectRequest.builder()
            .bucket(BUCKET)
            .key(PREFIX + runId + ".vhs.jsonl")
            .build();
    ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
    Files.write(downloaded, response.asByteArray());

    VhsTapeReader.VhsTape tape = new VhsTapeReader(objectMapper).read(downloaded);
    assertThat(tape.header().sourceRunId()).isEqualTo(runId);
    assertThat(tape.events()).hasSize(2);
    assertThat(tape.events().get(0).eventType()).isEqualTo("run_started");
    assertThat(tape.events().get(1).eventType()).isEqualTo("call_start");
  }

  @Test
  void maxBytesTruncatesTapeInMinio() throws Exception {
    S3TapeSink sink = sink(1L);
    String runId = "run-minio-trunc";
    sink.start(runId, "Ping", null);
    sink.append(runId, runStartedEvent());
    sink.append(runId, callStartEvent());
    long count = sink.close(runId);

    assertThat(count).isEqualTo(0L);

    Path downloaded = stagingDir.resolve("truncated.jsonl");
    GetObjectRequest request = GetObjectRequest.builder()
            .bucket(BUCKET)
            .key(PREFIX + runId + ".vhs.jsonl")
            .build();
    ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
    Files.write(downloaded, response.asByteArray());

    VhsTapeReader.VhsTape tape = new VhsTapeReader(objectMapper).read(downloaded);
    assertThat(tape.events()).isEmpty();
  }

  private S3TapeSink sink(long maxBytes) {
    CbsVhsProperties.S3Sink s3 = new CbsVhsProperties.S3Sink(BUCKET, PREFIX, null, false, maxBytes);
    CbsVhsProperties.Sink sink = new CbsVhsProperties.Sink(CbsVhsProperties.SinkType.s3, null, s3);
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), sink, new CbsVhsProperties.Scrub(false, List.of(), null, null));
    return new S3TapeSink(properties, s3Client, objectMapper, Instant::now, stagingDir);
  }

  private TapeEvent runStartedEvent() {
    return new TapeEvent(
            "1", 0, "run_started", "2026-09-19T12:00:00Z", 0L, null, null, null,
            new TapeEvent.Timing("2026-09-19T12:00:00Z", null, null),
            null, Map.of());
  }

  private TapeEvent callStartEvent() {
    return new TapeEvent(
            "1", 1, "call_start", "2026-09-19T12:00:00Z", 0L,
            new TapeEvent.CallMetadata("call_001", "helper", "Ping", "fetch"),
            Map.of(), null,
            new TapeEvent.Timing("2026-09-19T12:00:00Z", null, null),
            null, Map.of());
  }
}
