package dev.sultanov.keycloak.multitenancy.support.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import jakarta.mail.internet.MimeUtility;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

public class MailhogClient {

    private final ResteasyWebTarget target;

    private MailhogClient(ResteasyWebTarget target) {
        this.target = target;
    }

    public static MailhogClient create() {
        var context = IntegrationTestContextHolder.getContext();
        return new MailhogClient((ResteasyWebTarget) context.httpClient().target(context.mailhogUrl()));
    }

    @SneakyThrows
    public List<EmailContent> findAllForRecipient(String recipient) {
        var response = target.path("/api/v2/search")
                .queryParam("kind", "to")
                .queryParam("query", recipient)
                .request(MediaType.APPLICATION_JSON)
                .get();

        var entity = response.readEntity(String.class);
        var payload = new ObjectMapper().readValue(entity, JsonNode.class);

        var mails = new ArrayList<EmailContent>();
        for (JsonNode item : payload.get("items")) {
            var content = item.get("Content");
            var headers = content.get("Headers");
            var subject = MimeUtility.decodeText(headers.get("Subject").get(0).asText());
            var body = MimeUtility.decodeText(content.get("Body").asText());
            var from = headers.get("From").get(0).asText();
            var to = headers.get("To").get(0).asText();
            mails.add(new EmailContent(from, to, subject, body));
        }
        return mails;
    }

    public void deleteAll() {
        var response = target.path("/api/v1/messages").request().delete();
        response.close();
    }
}
