package dev.sultanov.keycloak.multitenancy.email;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.keycloak.models.ClientModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;

public class EmailRecipient implements UserModel {

    private final String email;

    public EmailRecipient(String email) {
        this.email = email;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        return new HashMap<>();
    }

    @Override
    public String getFirstAttribute(String name) {
        return null;
    }

    // All other methods are not required and therefore throw an exception

    @Override
    public String getId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getUsername() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setUsername(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long getCreatedTimestamp() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreatedTimestamp(Long timestamp) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEnabled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEnabled(boolean enabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAttribute(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<String> getRequiredActionsStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRequiredAction(String action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeRequiredAction(String action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getFirstName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFirstName(String firstName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLastName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLastName(String lastName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEmail(String email) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmailVerified() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<GroupModel> getGroupsStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void joinGroup(GroupModel group) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void leaveGroup(GroupModel group) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMemberOf(GroupModel group) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getFederationLink() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFederationLink(String link) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServiceAccountClientLink() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setServiceAccountClientLink(String clientInternalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<RoleModel> getRealmRoleMappingsStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<RoleModel> getClientRoleMappingsStream(ClientModel app) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasRole(RoleModel role) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void grantRole(RoleModel role) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        throw new UnsupportedOperationException();
    }
}

