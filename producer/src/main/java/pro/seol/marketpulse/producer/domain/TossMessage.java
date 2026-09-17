package pro.seol.marketpulse.producer.domain;

import java.util.List;

import tools.jackson.databind.JsonNode;

import pro.seol.marketpulse.common.serde.EventJson;

public sealed interface TossMessage {

    record Data(TossTopic topic, JsonNode payload) implements TossMessage {}

    record SubscriptionAck(String id, List<String> subscribed, List<String> rejected) implements TossMessage {}

    record Failure(String code, String message) implements TossMessage {}

    record Unknown(String raw) implements TossMessage {}

    static TossMessage from(final String raw) {
        final JsonNode node;
        try {
            node = EventJson.mapper().readTree(raw);
        } catch (RuntimeException e) {
            return new Unknown(raw);
        }
        return switch (node.path("type").asString("")) {
            case "message" -> new Data(TossTopic.parse(node.path("topic").asString(null)), node.path("data"));
            case "subscriptions" ->
                new SubscriptionAck(
                        node.path("id").asString(""), texts(node.path("subscribed")), texts(node.path("rejected")));
            case "error" ->
                new Failure(
                        node.path("error").path("code").asString(""),
                        node.path("error").path("message").asString(""));
            default -> new Unknown(raw);
        };
    }

    private static List<String> texts(final JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
