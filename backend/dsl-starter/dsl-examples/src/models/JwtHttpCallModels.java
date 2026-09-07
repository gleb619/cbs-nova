import java.util.Map;

public class JwtHttpCallModels {

  public record AuthApiIn(String token, String secret, String url, String method, String body) {
  }

  public record AuthApiOut(int status, String body, String subject, boolean verified) {
  }
}
