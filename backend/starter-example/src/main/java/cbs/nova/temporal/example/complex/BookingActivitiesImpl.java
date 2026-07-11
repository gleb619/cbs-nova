package cbs.nova.temporal.example.complex;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Production-style implementation that makes real outbound HTTP calls to the flight, hotel and car
 * reservation services. A shared {@link HttpClient} is used for all services and the target base
 * URL can be configured, which makes the implementation testable with a local WireMock server.
 */
public class BookingActivitiesImpl implements BookingActivities {
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(2)).build();

  private static final String DEFAULT_BASE_URL = "http://localhost:8080";

  private final String baseUrl;

  public BookingActivitiesImpl() {
    this(System.getenv().getOrDefault("BOOKING_SERVICE_URL", DEFAULT_BASE_URL));
  }

  public BookingActivitiesImpl(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  @Override
  public void bookFlight(String userId) {
    post("/flight", userId);
  }

  @Override
  public void bookHotel(String userId) {
    post("/hotel", userId);
  }

  @Override
  public void bookCar(String userId) {
    post("/car", userId);
  }

  @Override
  public void cancelFlight(String userId) {
    post("/flight/cancel", userId);
  }

  @Override
  public void cancelHotel(String userId) {
    post("/hotel/cancel", userId);
  }

  @Override
  public void cancelCar(String userId) {
    post("/car/cancel", userId);
  }

  private void post(String path, String userId) {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("userId=" + encode(userId)))
            .build();

    try {
      HttpResponse<String> response = HTTP_CLIENT.send(request,
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        throw new RuntimeException(
                "Service " + path + " returned " + response.statusCode() + ": " + response.body());
      }
    } catch (IOException e) {
      throw new RuntimeException("Service " + path + " call failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Service " + path + " call was interrupted", e);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
