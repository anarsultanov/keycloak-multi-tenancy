package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotAuthorizedException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AppAuthManager.BearerTokenAuthenticator;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.admin.AdminAuth;
import org.keycloak.services.resources.admin.AdminEventBuilder;

public abstract class AbstractAdminResource<T extends AdminAuth> {

    protected KeycloakSession session;

    protected final RealmModel realm;

    protected UserModel user;
    protected T auth;
    protected AdminEventBuilder adminEvent;
    protected EntityManager entityManager;
    protected TenantProvider tenantProvider;


    public AbstractAdminResource(KeycloakSession session) {
        this.session = session;
        this.realm = session.getContext().getRealm();
        setup();
    }

    private void setup() {
        setupAuth();
        setupEvents();
        setupProvider();
    }

    private void setupAuth() {
        var tokenString = AppAuthManager.extractAuthorizationHeaderToken(session.getContext().getRequestHeaders());

        if (tokenString == null) {
            throw new NotAuthorizedException("Bearer");
        }

        AccessToken token;

        try {
            JWSInput input = new JWSInput(tokenString);
            token = input.readJsonContent(AccessToken.class);
        } catch (JWSInputException e) {
            throw new NotAuthorizedException("Bearer token format error");
        }

        var realmName = token.getIssuer().substring(token.getIssuer().lastIndexOf('/') + 1);
        var realmManager = new RealmManager(session);
        var realm = realmManager.getRealmByName(realmName);

        if (realm == null) {
            throw new NotAuthorizedException("Unknown realm in token");
        }
        session.getContext().setRealm(realm);

        var authResult = new BearerTokenAuthenticator(session)
                .setRealm(realm)
                .setUriInfo(session.getContext().getUri())
                .setConnection(session.getContext().getConnection())
                .setHeaders(session.getContext().getRequestHeaders())
                .authenticate();
        if (authResult == null) {
            throw new NotAuthorizedException("Bearer");
        }

        user = authResult.getUser();

        try {
            Class<T> clazz = findSupportedType();
            Constructor<T> constructor = clazz.getConstructor(RealmModel.class, AccessToken.class, UserModel.class, ClientModel.class);
            auth = constructor.newInstance(realm, token, user, authResult.getClient());
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<T> findSupportedType() {
        ParameterizedType superclass = (ParameterizedType) getClass().getGenericSuperclass();
        Type[] actualTypeArguments = superclass.getActualTypeArguments();
        return (Class<T>) actualTypeArguments[0];
    }

    private void setupEvents() {
        adminEvent = new AdminEventBuilder(session.getContext().getRealm(), auth, session, session.getContext().getConnection())
                .realm(session.getContext().getRealm());
    }

    protected final void setupProvider() {
        this.entityManager = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        this.tenantProvider = session.getProvider(TenantProvider.class);
    }

}