package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.NotificationRuleHandler;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.notification.NotificationRuleEngine;
import cbs.nova.starter.notification.NotificationRuleService;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.webhook.WebhookDispatcher;
import java.util.Optional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * T565 notification rules engine routes. Conditional on {@link NotificationRuleService} (which
 * itself is conditional on a DataSource via {@code NotificationConfiguration}): no service bean, no
 * routes — the same infra-gating idiom as {@code DslScheduleRouterConfiguration}.
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnBean(NotificationRuleService.class)
public class NotificationRouterConfiguration {

  @Bean
  NotificationRuleHandler notificationRuleHandler(NotificationRuleService ruleService,
          NotificationRuleEngine ruleEngine, NotificationRuleFiringRepository firingRepository,
          Optional<WebhookDispatcher> webhookDispatcher) {
    return new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            webhookDispatcher);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/notifications/rules", beanClass = NotificationRuleHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listNotificationRules", summary = "List notification rules, match order (priority desc)", tags = {
          "Notifications"}, parameters = {
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum number of rules to return"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of matching rules to skip before returning results")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))))),
      @RouterOperation(path = "/api/dsl/notifications/rules", beanClass = NotificationRuleHandler.class, beanMethod = "create", method = RequestMethod.POST, operation = @Operation(operationId = "createNotificationRule", summary = "Create a notification rule", tags = {
          "Notifications"}, responses = @ApiResponse(responseCode = "201", description = "Rule created"))),
      @RouterOperation(path = "/api/dsl/notifications/rules/{id}", beanClass = NotificationRuleHandler.class, beanMethod = "get", method = RequestMethod.GET, operation = @Operation(operationId = "getNotificationRule", summary = "Get a notification rule by id", tags = {
          "Notifications"}, responses = {
              @ApiResponse(responseCode = "200", description = "The rule"),
              @ApiResponse(responseCode = "404", description = "No rule with the given id")})),
      @RouterOperation(path = "/api/dsl/notifications/rules/{id}", beanClass = NotificationRuleHandler.class, beanMethod = "update", method = RequestMethod.PUT, operation = @Operation(operationId = "updateNotificationRule", summary = "Replace a notification rule", tags = {
          "Notifications"}, responses = {
              @ApiResponse(responseCode = "200", description = "The updated rule"),
              @ApiResponse(responseCode = "404", description = "No rule with the given id")})),
      @RouterOperation(path = "/api/dsl/notifications/rules/{id}", beanClass = NotificationRuleHandler.class, beanMethod = "delete", method = RequestMethod.DELETE, operation = @Operation(operationId = "deleteNotificationRule", summary = "Delete a notification rule", tags = {
          "Notifications"}, responses = {
              @ApiResponse(responseCode = "204", description = "Rule deleted"),
              @ApiResponse(responseCode = "404", description = "No rule with the given id")})),
      @RouterOperation(path = "/api/dsl/notifications/rules/{id}/enabled", beanClass = NotificationRuleHandler.class, beanMethod = "setEnabled", method = RequestMethod.POST, operation = @Operation(operationId = "toggleNotificationRule", summary = "Enable or disable a notification rule", tags = {
          "Notifications"}, responses = {
              @ApiResponse(responseCode = "200", description = "The updated rule"),
              @ApiResponse(responseCode = "404", description = "No rule with the given id")})),
      @RouterOperation(path = "/api/dsl/notifications/channels", beanClass = NotificationRuleHandler.class, beanMethod = "channels", method = RequestMethod.GET, operation = @Operation(operationId = "listNotificationChannels", summary = "List available notification channel types", tags = {
          "Notifications"}, responses = @ApiResponse(responseCode = "200", description = "Static channel catalogue; email is a placeholder"))),
      @RouterOperation(path = "/api/dsl/notifications/fire-log", beanClass = NotificationRuleHandler.class, beanMethod = "fireLog", method = RequestMethod.GET, operation = @Operation(operationId = "listNotificationFireLog", summary = "List notification rule firing audit rows, newest first", tags = {
          "Notifications"}, parameters = {
              @Parameter(name = "ruleId", in = ParameterIn.QUERY, description = "Optional exact rule id filter"),
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum number of rows to return"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of matching rows to skip before returning results")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))))),
      @RouterOperation(path = "/api/dsl/notifications/test", beanClass = NotificationRuleHandler.class, beanMethod = "test", method = RequestMethod.POST, operation = @Operation(operationId = "testNotificationRules", summary = "Test-match a synthetic event against the enabled rules and report results", tags = {
          "Notifications"}, responses = @ApiResponse(responseCode = "200", description = "Matched rule ids and per-action results; nothing is persisted")))
  })
  public RouterFunction<ServerResponse> notificationRouter(NotificationRuleHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/notifications/rules", handler::list)
            .POST("/api/dsl/notifications/rules", handler::create)
            .GET("/api/dsl/notifications/channels", handler::channels)
            .GET("/api/dsl/notifications/fire-log", handler::fireLog)
            .POST("/api/dsl/notifications/test", handler::test)
            .GET("/api/dsl/notifications/rules/{id}", handler::get)
            .PUT("/api/dsl/notifications/rules/{id}", handler::update)
            .DELETE("/api/dsl/notifications/rules/{id}", handler::delete)
            .POST("/api/dsl/notifications/rules/{id}/enabled", handler::setEnabled)
            .build();
  }
}
