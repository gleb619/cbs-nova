package cbs.nova.starter.vhs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class S3TapeSinkTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @TempDir
  Path tempDir;

  @Test
  void writesEventsAndUploadsOnClose() throws IOException {
    S3Client s3 = mock(S3Client.class);
    CapturedUpload upload = new CapturedUpload();
    doAnswer(inv -> {
      upload.capture(inv.getArgument(0), inv.getArgument(1));
      return null;
    }).when(s3)
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    S3TapeSink sink = sink(s3, 0L, "tapes/");
    String runId = "run-s3-1";
    sink.start(runId, "Ping", "corr-s3");
    sink.append(runId, runStartedEvent());
    sink.append(runId, callStartEvent());
    long count = sink.close(runId);

    assertThat(count).isEqualTo(2L);
    assertThat(upload.request.bucket()).isEqualTo("vhs-bucket");
    assertThat(upload.request.key()).isEqualTo("tapes/run-s3-1.vhs.jsonl");

    List<String> lines = upload.lines();
    assertThat(lines).hasSize(4);
    JsonNode header = objectMapper.readTree(lines.get(0));
    assertThat(header.get("vhs_tape_format_version").asText()).isEqualTo("1.0.0");
    assertThat(header.get("source_run_id").asText()).isEqualTo(runId);

    assertThat(objectMapper.readTree(lines.get(1)).get("event_type").asText())
            .isEqualTo("run_started");
    assertThat(objectMapper.readTree(lines.get(2)).get("event_type").asText())
            .isEqualTo("call_start");

    JsonNode trailer = objectMapper.readTree(lines.get(3));
    assertThat(trailer.get("event_type").asText()).isEqualTo("tape_closed");
    assertThat(trailer.get("output").get("event_count").asInt()).isEqualTo(2);
  }

  @Test
  void noUploadWhenRunNotStarted() {
    S3Client s3 = mock(S3Client.class);
    S3TapeSink sink = sink(s3, 0L, null);
    assertThat(sink.close("missing")).isEqualTo(0L);
    verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void usesPrefixWithTrailingSlash() throws IOException {
    S3Client s3 = mock(S3Client.class);
    CapturedUpload upload = new CapturedUpload();
    doAnswer(inv -> {
      upload.capture(inv.getArgument(0), inv.getArgument(1));
      return null;
    }).when(s3)
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    S3TapeSink sink = sink(s3, 0L, "archive/");
    sink.start("run-2", "Ping", null);
    sink.append("run-2", runStartedEvent());
    sink.close("run-2");

    assertThat(upload.request.key()).isEqualTo("archive/run-2.vhs.jsonl");
  }

  @Test
  void addsTrailingSlashWhenPrefixMissing() throws IOException {
    S3Client s3 = mock(S3Client.class);
    CapturedUpload upload = new CapturedUpload();
    doAnswer(inv -> {
      upload.capture(inv.getArgument(0), inv.getArgument(1));
      return null;
    }).when(s3)
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    S3TapeSink sink = sink(s3, 0L, "archive");
    sink.start("run-3", "Ping", null);
    sink.append("run-3", runStartedEvent());
    sink.close("run-3");

    assertThat(upload.request.key()).isEqualTo("archive/run-3.vhs.jsonl");
  }

  @Test
  void sanitizesRunIdInKey() throws IOException {
    S3Client s3 = mock(S3Client.class);
    CapturedUpload upload = new CapturedUpload();
    doAnswer(inv -> {
      upload.capture(inv.getArgument(0), inv.getArgument(1));
      return null;
    }).when(s3)
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    S3TapeSink sink = sink(s3, 0L, null);
    sink.start("a/b\\c", "Ping", null);
    sink.append("a/b\\c", runStartedEvent());
    sink.close("a/b\\c");

    assertThat(upload.request.key()).isEqualTo("a_b_c.vhs.jsonl");
  }

  @Test
  void maxBytesTruncatesAndStillUploads() throws IOException {
    S3Client s3 = mock(S3Client.class);
    CapturedUpload upload = new CapturedUpload();
    doAnswer(inv -> {
      upload.capture(inv.getArgument(0), inv.getArgument(1));
      return null;
    }).when(s3)
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    S3TapeSink sink = sink(s3, 1L, null);
    sink.start("run-trunc", "Ping", null);
    sink.append("run-trunc", runStartedEvent());
    sink.append("run-trunc", callStartEvent());
    long count = sink.close("run-trunc");

    assertThat(count).isEqualTo(0L);
    List<String> lines = upload.lines();
    assertThat(lines).hasSize(3);
    assertThat(objectMapper.readTree(lines.get(1)).get("event_type").asText())
            .isEqualTo("tape_truncated");
    assertThat(objectMapper.readTree(lines.get(2)).get("event_type").asText())
            .isEqualTo("tape_closed");
  }

  @Test
  void failsFastWithoutS3Client() {
    CbsVhsProperties.Sink sinkConfig = new CbsVhsProperties.Sink(
            CbsVhsProperties.SinkType.s3,
            null,
            new CbsVhsProperties.S3Sink("vhs-bucket", "tapes/", null, false, 0L));
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), sinkConfig,
            new CbsVhsProperties.Scrub(false, List.of(), null, null));

    assertThatThrownBy(() -> new S3TapeSink(properties, null, objectMapper, Instant::now, tempDir))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("S3Client bean");
  }

  @Test
  void failsFastWithoutBucket() {
    CbsVhsProperties.Sink sinkConfig = new CbsVhsProperties.Sink(
            CbsVhsProperties.SinkType.s3,
            null,
            new CbsVhsProperties.S3Sink(null, null, null, false, 0L));
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), sinkConfig,
            new CbsVhsProperties.Scrub(false, List.of(), null, null));

    S3Client s3 = mock(S3Client.class);
    assertThatThrownBy(() -> new S3TapeSink(properties, s3, objectMapper, Instant::now, tempDir))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bucket");
  }

  @Test
  void failsFastWhenSinkTypeIsLocal() {
    CbsVhsProperties.Sink sinkConfig = new CbsVhsProperties.Sink(
            CbsVhsProperties.SinkType.local,
            new CbsVhsProperties.LocalSink(tempDir, 0, 0L),
            null);
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), sinkConfig,
            new CbsVhsProperties.Scrub(false, List.of(), null, null));

    S3Client s3 = mock(S3Client.class);
    assertThatThrownBy(() -> new S3TapeSink(properties, s3, objectMapper, Instant::now, tempDir))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sink.type=s3");
  }

  @Test
  void deletesStagingFileAfterUpload() throws IOException {
    S3Client s3 = mock(S3Client.class);
    doAnswer(inv -> null).when(s3)
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    S3TapeSink sink = sink(s3, 0L, null);
    sink.start("run-cleanup", "Ping", null);
    sink.append("run-cleanup", runStartedEvent());
    sink.close("run-cleanup");

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3).putObject(request.capture(), body.capture());
    assertThat(request.getValue().key()).isEqualTo("run-cleanup.vhs.jsonl");
  }

  private S3TapeSink sink(S3Client s3Client, long maxBytes, String prefix) {
    CbsVhsProperties.S3Sink s3 = new CbsVhsProperties.S3Sink("vhs-bucket", prefix, null, false,
            maxBytes);
    CbsVhsProperties.Sink sink = new CbsVhsProperties.Sink(CbsVhsProperties.SinkType.s3, null, s3);
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), sink, new CbsVhsProperties.Scrub(false, List.of(), null, null));
    return new S3TapeSink(properties, s3Client, objectMapper, Instant::now, tempDir);
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

  private static final class CapturedUpload {

    private PutObjectRequest request;
    private byte[] bytes;

    void capture(PutObjectRequest request, RequestBody body) throws IOException {
      this.request = request;
      try (InputStream in = body.contentStreamProvider().newStream();
              ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        in.transferTo(out);
        this.bytes = out.toByteArray();
      }
    }

    List<String> lines() {
      String raw = new String(bytes, StandardCharsets.UTF_8);
      return raw.isBlank()
              ? List.of()
              : List.of(raw.split("\n", -1)).stream().filter(s -> !s.isBlank()).toList();
    }
  }
}
