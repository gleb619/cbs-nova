import java.util.Map;

public class PaginatedUrlModels {

  public record PaginatedUrlIn(String baseUrl, String pathSegment,
      Map<String, String> queryParams) {
  }

  public record PaginatedUrlOut(String url, String encodedPath, String queryString) {
  }
}
