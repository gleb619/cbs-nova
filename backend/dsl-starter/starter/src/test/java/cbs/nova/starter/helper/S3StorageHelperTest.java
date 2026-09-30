package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.S3StorageIn;
import cbs.nova.starter.helper.model.S3StorageOut;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3StorageHelperTest {

  private final S3Client s3Client = mock(S3Client.class);
  private final S3StorageHelper helper = new S3StorageHelper(s3Client);

  @Test
  void putStoresBodyUnderBucketAndKey() {
    Result<S3StorageOut> result = execute(
            new S3StorageIn("put", "orders", "2026/01/order-42.json", "{\"id\":42}", null));

    assertThat(result.isSuccess()).isTrue();
    S3StorageOut out = result.value();
    assertThat(out.op()).isEqualTo("put");
    assertThat(out.bucket()).isEqualTo("orders");
    assertThat(out.key()).isEqualTo("2026/01/order-42.json");
    assertThat(out.bodyBase64()).isNull();
    assertThat(out.timestamp()).isBetween(Instant.now().minusSeconds(5),
            Instant.now().plusSeconds(5));

    ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor
            .forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
    PutObjectRequest request = requestCaptor.getValue();
    assertThat(request.bucket()).isEqualTo("orders");
    assertThat(request.key()).isEqualTo("2026/01/order-42.json");
    assertThat(request.contentType()).isEqualTo("application/octet-stream");
    assertThat(bodyCaptor.getValue().contentStreamProvider().newStream()).hasContent("{\"id\":42}");
  }

  @Test
  void putUsesExplicitContentType() {
    Result<S3StorageOut> result = execute(
            new S3StorageIn("put", "orders", "a.txt", "hello", "text/plain"));

    assertThat(result.isSuccess()).isTrue();

    ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor
            .forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertThat(requestCaptor.getValue().contentType()).isEqualTo("text/plain");
  }

  @Test
  void getReturnsBase64BodyAndContentType() {
    byte[] content = "{\"id\":42}".getBytes(StandardCharsets.UTF_8);
    GetObjectResponse response = GetObjectResponse.builder().contentType("application/json")
            .build();
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenReturn(ResponseBytes.fromByteArray(response, content));

    Result<S3StorageOut> result = execute(
            new S3StorageIn("get", "orders", "order.json", null, null));

    assertThat(result.isSuccess()).isTrue();
    S3StorageOut out = result.value();
    assertThat(out.op()).isEqualTo("get");
    assertThat(out.key()).isEqualTo("order.json");
    assertThat(out.contentType()).isEqualTo("application/json");
    assertThat(new String(Base64.getDecoder().decode(out.bodyBase64()), StandardCharsets.UTF_8))
            .isEqualTo("{\"id\":42}");

    ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor
            .forClass(GetObjectRequest.class);
    verify(s3Client).getObjectAsBytes(requestCaptor.capture());
    assertThat(requestCaptor.getValue().bucket()).isEqualTo("orders");
    assertThat(requestCaptor.getValue().key()).isEqualTo("order.json");
  }

  @Test
  void deleteRemovesObject() {
    Result<S3StorageOut> result = execute(
            new S3StorageIn("delete", "orders", "order.json", null, null));

    assertThat(result.isSuccess()).isTrue();
    S3StorageOut out = result.value();
    assertThat(out.op()).isEqualTo("delete");
    assertThat(out.key()).isEqualTo("order.json");
    assertThat(out.bodyBase64()).isNull();

    ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor
            .forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(requestCaptor.capture());
    assertThat(requestCaptor.getValue().bucket()).isEqualTo("orders");
    assertThat(requestCaptor.getValue().key()).isEqualTo("order.json");
  }

  @Test
  void unknownOpFails() {
    Result<S3StorageOut> result = execute(new S3StorageIn("copy", "orders", "a.txt", "x", null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageStartingWith("s3Storage.op must be one of");
  }

  @Test
  void missingOpFails() {
    Result<S3StorageOut> result = execute(new S3StorageIn(" ", "orders", "a.txt", "x", null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("s3Storage.op is required");
  }

  @Test
  void missingBucketFails() {
    Result<S3StorageOut> result = execute(new S3StorageIn("get", "", "order.json", null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("s3Storage.bucket is required");
  }

  @Test
  void missingKeyFails() {
    Result<S3StorageOut> result = execute(new S3StorageIn("get", "orders", null, null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("s3Storage.key is required");
  }

  @Test
  void missingBodyOnPutFails() {
    Result<S3StorageOut> result = execute(new S3StorageIn("put", "orders", "a.txt", "", null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("s3Storage.body is required for op=put");
  }

  @Test
  void missingS3ClientBeanFailsWithoutTouchingStorage() {
    S3StorageHelper withoutClient = new S3StorageHelper((S3Client) null);

    Result<S3StorageOut> result = withoutClient.execute(
            SimpleContext.builder(new S3StorageIn("get", "orders", "a.txt", null, null))
                    .mode(ExecutionMode.PREVIEW).build());

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalStateException.class);
    assertThat(result.cause()).hasMessageStartingWith("s3Storage: S3Client bean is not available");
  }

  private Result<S3StorageOut> execute(S3StorageIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
