package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.helper.model.S3StorageIn;
import cbs.nova.starter.helper.model.S3StorageOut;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores and retrieves objects in S3-compatible object storage (AWS S3, MinIO) through the host
 * application's existing {@link S3Client} Spring bean; no client is created here and MinIO works by
 * configuring a custom {@code endpointOverride} on that bean.
 *
 * <p>
 * The helper is opt-in: AWS SDK v2 is a {@code compileOnly} dependency of the starter, so apps that
 * never declare {@code software.amazon.awssdk:s3} are unaffected. When the SDK is missing the
 * helper class cannot be loaded at all; when the SDK is present but no {@code S3Client} bean
 * exists, calls fail with an explicit error (logged once) instead of silently dropping the object.
 */
@SpringHelper(name = "s3Storage")
public class S3StorageHelper implements Executable<S3StorageIn, S3StorageOut> {

  private static final Logger LOG = LoggerFactory.getLogger(S3StorageHelper.class);
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
  private static final String OP_PUT = "put";
  private static final String OP_GET = "get";
  private static final String OP_DELETE = "delete";

  private final @Nullable S3Client s3Client;

  @Autowired
  public S3StorageHelper(ObjectProvider<S3Client> s3ClientProvider) {
    this.s3Client = s3ClientProvider.getIfAvailable();
  }

  S3StorageHelper(@Nullable S3Client s3Client) {
    this.s3Client = s3Client;
  }

  @Override
  public @NonNull Result<S3StorageOut> execute(@NonNull Context<S3StorageIn> ctx) {
    try {
      S3StorageIn input = ctx.body();
      String op = validate(input);

      if (s3Client == null) {
        LOG.error("s3Storage: no S3Client bean available; object storage is not configured");
        return Result.failure(new IllegalStateException(
                "s3Storage: S3Client bean is not available; object storage is not configured"));
      }

      return switch (op) {
        case OP_PUT -> put(input);
        case OP_GET -> get(input);
        default -> delete(input);
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private @NonNull Result<S3StorageOut> put(@NonNull S3StorageIn input) {
    byte[] content = input.body().getBytes(StandardCharsets.UTF_8);
    PutObjectRequest request = PutObjectRequest.builder()
            .bucket(input.bucket())
            .key(input.key())
            .contentType(contentType(input))
            .contentLength((long) content.length)
            .build();
    s3Client.putObject(request, RequestBody.fromBytes(content));
    return Result.success(new S3StorageOut(OP_PUT, input.bucket(), input.key(), null, null,
            Instant.now()));
  }

  private @NonNull Result<S3StorageOut> get(@NonNull S3StorageIn input) {
    GetObjectRequest request = GetObjectRequest.builder()
            .bucket(input.bucket())
            .key(input.key())
            .build();
    ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
    byte[] content = response.asByteArray();
    String responseContentType = response.response().contentType();
    return Result.success(new S3StorageOut(OP_GET, input.bucket(), input.key(),
            Base64.getEncoder().encodeToString(content),
            responseContentType == null ? DEFAULT_CONTENT_TYPE : responseContentType,
            Instant.now()));
  }

  private @NonNull Result<S3StorageOut> delete(@NonNull S3StorageIn input) {
    DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(input.bucket())
            .key(input.key())
            .build();
    s3Client.deleteObject(request);
    return Result.success(new S3StorageOut(OP_DELETE, input.bucket(), input.key(), null, null,
            Instant.now()));
  }

  private static @NonNull String contentType(@NonNull S3StorageIn input) {
    return input.contentType() == null || input.contentType().isBlank()
            ? DEFAULT_CONTENT_TYPE
            : input.contentType();
  }

  private static @NonNull String validate(@NonNull S3StorageIn input) {
    if (input.op() == null || input.op().isBlank()) {
      throw new IllegalArgumentException("s3Storage.op is required");
    }
    String op = input.op().toLowerCase(Locale.ROOT);
    if (!OP_PUT.equals(op) && !OP_GET.equals(op) && !OP_DELETE.equals(op)) {
      throw new IllegalArgumentException(
              "s3Storage.op must be one of put, get, delete: " + input.op());
    }
    if (input.bucket() == null || input.bucket().isBlank()) {
      throw new IllegalArgumentException("s3Storage.bucket is required");
    }
    if (input.key() == null || input.key().isBlank()) {
      throw new IllegalArgumentException("s3Storage.key is required");
    }
    if (OP_PUT.equals(op) && (input.body() == null || input.body().isBlank())) {
      throw new IllegalArgumentException("s3Storage.body is required for op=put");
    }
    return op;
  }
}
