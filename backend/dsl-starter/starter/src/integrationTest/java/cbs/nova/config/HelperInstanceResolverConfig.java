package cbs.nova.config;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.helper.ArithmeticHelper;
import cbs.nova.starter.helper.Base64Helper;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.CompressionHelper;
import cbs.nova.starter.helper.ConditionalFailingHelper;
import cbs.nova.starter.helper.CurrentTimestampHelper;
import cbs.nova.starter.helper.DateMathHelper;
import cbs.nova.starter.helper.FileLatchHelper;
import cbs.nova.starter.helper.FilterRecordsHelper;
import cbs.nova.starter.helper.FormatCsvHelper;
import cbs.nova.starter.helper.FormatMessageHelper;
import cbs.nova.starter.helper.HmacSha256SignHelper;
import cbs.nova.starter.helper.HmacSha256VerifyHelper;
import cbs.nova.starter.helper.HttpAuthHelper;
import cbs.nova.starter.helper.HttpCallHelper;
import cbs.nova.starter.helper.JsonExtractHelper;
import cbs.nova.starter.helper.MaskHelper;
import cbs.nova.starter.helper.ParseDateHelper;
import cbs.nova.starter.helper.ParseYamlHelper;
import cbs.nova.starter.helper.RandomHelper;
import cbs.nova.starter.helper.SemverHelper;
import cbs.nova.starter.helper.Sha256Helper;
import cbs.nova.starter.helper.SortRecordsHelper;
import cbs.nova.starter.helper.UnreliableApiHelper;
import cbs.nova.starter.helper.UrlDecodeHelper;
import cbs.nova.starter.helper.UuidV7Helper;
import cbs.nova.starter.helper.model.FileLatchIn;
import cbs.nova.starter.helper.model.FileLatchOut;
import io.temporal.workflow.Workflow;
import java.lang.reflect.InvocationTargetException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;

public class HelperInstanceResolverConfig {

  public static final CountDownLatch LATCH_ENTERED = new CountDownLatch(1);

  public HelperInstanceResolver helperInstanceResolver() {
    return helperClass -> {
      if (helperClass == ConditionalFailingHelper.class) {
        return new ConditionalFailingHelper();
      }
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper();
      }
      if (helperClass == CurrentTimestampHelper.class) {
        return new CurrentTimestampHelper();
      }
      if (helperClass == FileLatchHelper.class) {
        return new FileLatchHelper() {
          @Override
          public @NonNull Result<FileLatchOut> execute(@NonNull Context<FileLatchIn> ctx) {
            LATCH_ENTERED.countDown();
            Workflow.sleep(Duration.ofSeconds(10));
            return Result.success(new FileLatchOut(ctx.body().payload()));
          }
        };
      }
      if (helperClass == FilterRecordsHelper.class) {
        return new FilterRecordsHelper();
      }
      if (helperClass == FormatMessageHelper.class) {
        return new FormatMessageHelper();
      }
      if (helperClass == HttpCallHelper.class) {
        return new HttpCallHelper(HttpClient.newHttpClient(),
                new CbsNovaLoggingProperties(Level.INFO, Level.INFO,
                        true));
      }
      if (helperClass == JsonExtractHelper.class) {
        return new JsonExtractHelper(new ObjectMapper());
      }
      if (helperClass == SortRecordsHelper.class) {
        return new SortRecordsHelper();
      }
      if (helperClass == ArithmeticHelper.class) {
        return new ArithmeticHelper();
      }
      if (helperClass == UnreliableApiHelper.class) {
        return new UnreliableApiHelper();
      }
      if (helperClass == Base64Helper.class) {
        return new Base64Helper();
      }
      if (helperClass == ParseYamlHelper.class) {
        return new ParseYamlHelper();
      }
      if (helperClass == FormatCsvHelper.class) {
        return new FormatCsvHelper();
      }
      if (helperClass == CompressionHelper.class) {
        return new CompressionHelper();
      }
      if (helperClass == SemverHelper.class) {
        return new SemverHelper();
      }
      if (helperClass == HttpAuthHelper.class) {
        return new HttpAuthHelper();
      }
      if (helperClass == DateMathHelper.class) {
        return new DateMathHelper();
      }
      if (helperClass == HmacSha256VerifyHelper.class) {
        return new HmacSha256VerifyHelper();
      }
      if (helperClass == UrlDecodeHelper.class) {
        return new UrlDecodeHelper();
      }
      if (helperClass == UuidV7Helper.class) {
        return new UuidV7Helper();
      }
      if (helperClass == Sha256Helper.class) {
        return new Sha256Helper();
      }
      if (helperClass == RandomHelper.class) {
        return new RandomHelper();
      }
      if (helperClass == ParseDateHelper.class) {
        return new ParseDateHelper();
      }
      if (helperClass == HmacSha256SignHelper.class) {
        return new HmacSha256SignHelper();
      }
      if (helperClass == MaskHelper.class) {
        return new MaskHelper();
      }

      try {
        return (Executable<?, ?>) helperClass.getConstructor().newInstance();
      } catch (Exception e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName());
      }
    };
  }

}
