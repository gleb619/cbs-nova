package cbs.nova.starter;

import java.util.List;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.GenericContainer;

public class KeycloakRealmInitializer {

  private final String authServerUrl;
  private final String realm = "cbs-nova";
  private final String clientId = "cbs-nova-api";
  private final String username = "test-user";
  private final String password = "test-pass";

  public KeycloakRealmInitializer(GenericContainer<?> keycloak) {
    this.authServerUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
  }

  public void initialize() {
    try (Keycloak keycloak = Keycloak.getInstance(authServerUrl, "master", "admin", "admin",
            "admin-cli")) {
      RealmRepresentation realmRep = new RealmRepresentation();
      realmRep.setRealm(realm);
      realmRep.setEnabled(true);
      keycloak.realms().create(realmRep);

      var realmResource = keycloak.realm(realm);

      ClientRepresentation client = new ClientRepresentation();
      client.setClientId(clientId);
      client.setEnabled(true);
      client.setDirectAccessGrantsEnabled(true);
      client.setPublicClient(false);
      client.setSecret("test-secret");
      realmResource.clients().create(client);

      UserRepresentation user = new UserRepresentation();
      user.setUsername(username);
      user.setEnabled(true);

      CredentialRepresentation credential = new CredentialRepresentation();
      credential.setType(CredentialRepresentation.PASSWORD);
      credential.setValue(password);
      credential.setTemporary(false);
      user.setCredentials(List.of(credential));

      realmResource.users().create(user);
    }
  }

  public String tokenEndpoint() {
    return authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
  }

  public String getClientId() {
    return clientId;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getClientSecret() {
    return "test-secret";
  }
}
