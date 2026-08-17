package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.domain.CompensationStatus;
import com.leecardo.paymentdiagnostics.domain.MessageDeliveryStatus;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderRole;
import com.leecardo.paymentdiagnostics.domain.OrderStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class SimulationScenarioLoaderTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T10:12:00Z");

    @Test
    void loadsValidDocumentThroughDtoMapping() {
        SimulationScenarioDocument document = loader().load(resource(validJson()));

        assertEquals(1, document.schemaVersion());
        assertEquals(OBSERVED_AT, document.observedAt());
        assertEquals(new OrderId("order-123"), document.orders().getFirst().orderId());
        assertEquals(OrderRole.SINGLE, document.orders().getFirst().role());
        assertEquals(OrderStatus.PAID, document.orders().getFirst().status());
        assertEquals(new BigDecimal("30.00"), document.paymentTransactions().getFirst().amount());
        assertEquals(PaymentStatus.CALLBACK_RECEIVED, document.paymentTransactions().getFirst().status());
        assertEquals(MessageDeliveryStatus.CONSUMED, document.messageDeliveries().getFirst().status());
        assertEquals(CompensationStatus.SUCCEEDED, document.compensationTasks().getFirst().status());
        assertTrue(document.traceSummaries().getFirst().complete());
        assertEquals(SimulationFactSource.PAYMENT, document.failures().getFirst().source());
        assertEquals(FactQueryException.Kind.TIMEOUT, document.failures().getFirst().kind());
    }

    @Test
    void rejectsUnsupportedSchemaVersionAndMissingRequiredTopLevelFields() {
        assertLoadFailure("schemaVersion must be 1", validJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));
        assertLoadFailure("observedAt must not be null", validJson().replace("\"observedAt\": \"2026-08-17T10:12:00Z\"", "\"observedAt\": null"));
        assertLoadFailure("orders must not be null", jsonWithTopLevelNull("orders"));
        assertLoadFailure("paymentTransactions must not be null", jsonWithTopLevelNull("paymentTransactions"));
        assertLoadFailure("messageDeliveries must not be null", jsonWithTopLevelNull("messageDeliveries"));
        assertLoadFailure("compensationTasks must not be null", jsonWithTopLevelNull("compensationTasks"));
        assertLoadFailure("traceSummaries must not be null", jsonWithTopLevelNull("traceSummaries"));
        assertLoadFailure("failures must not be null", jsonWithTopLevelNull("failures"));
    }

    @Test
    void rejectsInvalidEnumDomainValuesNegativeValuesAndInconsistentTimes() {
        assertLoadFailure("status", validJson().replace("\"status\": \"PAID\"", "\"status\": \"NOT_A_STATUS\""));
        assertLoadFailure("goodsCount must be greater than zero", validJson().replace("\"goodsCount\": 2", "\"goodsCount\": -1"));
        assertLoadFailure("amount must not be negative", validJson().replace("\"amount\": 30.00", "\"amount\": -30.00"));
        assertLoadFailure("callbackReceivedAt must not be before providerCompletedAt",
                validJson().replace("\"providerCompletedAt\": \"2026-08-17T10:06:00Z\"", "\"providerCompletedAt\": \"2026-08-17T10:08:00Z\""));
    }

    @Test
    void rejectsOmittedPrimitiveDtoFieldsIncludingValidDefaultValues() {
        assertLoadFailure("schemaVersion must not be null", removeLine(validJson(), "\"schemaVersion\": 1"));
        assertLoadFailure("goodsCount must not be null", removeLine(validJson(), "\"goodsCount\": 2"));
        assertLoadFailure("retryCount must not be null", removeLine(validJson(), "\"retryCount\": 1"));
        assertLoadFailure("maxRetries must not be null", removeLine(validJson(), "\"maxRetries\": 3"));
        assertLoadFailure("complete must not be null", removeLine(validJson(), "\"complete\": true"));
        assertLoadFailure("retryCount must not be null", removeLine(defaultPrimitiveJson(), "\"retryCount\": 0"));
        assertLoadFailure("complete must not be null", removeLine(defaultPrimitiveJson(), "\"complete\": false"));
    }

    @Test
    void loadErrorContainsSafeResourceDescriptionAndLogicalObjectIdOnly() {
        String absolutePath = "/tmp/payment-diagnostics/secret/scenario.json";
        ByteArrayResource resource = new ByteArrayResource(validJson().replace("\"amount\": 30.00", "\"amount\": -30.00").getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getDescription() {
                return "file [" + absolutePath + "]";
            }
        };

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> loader().load(resource));

        assertTrue(thrown.getMessage().contains("scenario.json"));
        assertTrue(thrown.getMessage().contains("payment-1"));
        assertFalse(thrown.getMessage().contains("/tmp/payment-diagnostics/secret"));
        assertSafeCauseChain(thrown);
    }

    @Test
    void wrapsIoFailureWithSafeResourceDescriptionAndNoUnsafeCausePath() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> loader().load(new ThrowingResource("/var/private/scenarios/broken.json")));

        assertTrue(thrown.getMessage().contains("broken.json"));
        assertFalse(thrown.getMessage().contains("/var/private/scenarios"));
        assertSafeCauseChain(thrown);
    }

    private static SimulationScenarioLoader loader() {
        return new SimulationScenarioLoader(new ObjectMapper());
    }

    private static void assertLoadFailure(String expectedMessagePart, String json) {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> loader().load(resource(json)));
        assertTrue(thrown.getMessage().contains(expectedMessagePart), thrown.getMessage());
        assertSafeCauseChain(thrown);
    }

    private static void assertSafeCauseChain(Throwable thrown) {
        for (Throwable failure = thrown; failure != null; failure = failure.getCause()) {
            String message = failure.getMessage();
            if (message != null) {
                assertFalse(message.contains("/var/"), message);
                assertFalse(message.contains("C:\\"), message);
                assertFalse(message.contains("/tmp/payment-diagnostics/secret"), message);
            }
        }
    }

    private static ByteArrayResource resource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getDescription() {
                return "simulation scenario test fixture";
            }
        };
    }

    private static String jsonWithTopLevelNull(String fieldName) {
        String json = validJson();
        int fieldStart = json.indexOf("\"" + fieldName + "\": [");
        int arrayStart = json.indexOf('[', fieldStart);
        int depth = 0;
        for (int index = arrayStart; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(0, arrayStart) + "null" + json.substring(index + 1);
                }
            }
        }
        throw new IllegalArgumentException("field not found: " + fieldName);
    }

    private static String removeLine(String json, String lineContents) {
        return json.lines()
                .filter(line -> !line.contains(lineContents))
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
    }

    private static String invalidPaymentOrderIdJson() {
        return validJson().replace("\"orderId\": \"order-123\",\n                      \"provider\": \"ALIPAY\"",
                "\"orderId\": \"not an id!\",\n                      \"provider\": \"ALIPAY\"");
    }

    private static String defaultPrimitiveJson() {
        return """
                {
                  "schemaVersion": 1,
                  "observedAt": "2026-08-17T10:12:00Z",
                  "orders": [
                    {
                      "orderId": "order-123",
                      "masterOrderId": null,
                      "role": "SINGLE",
                      "productId": "product-1",
                      "productName": "Diagnostic Widget",
                      "productType": "COURSE",
                      "goodsCount": 1,
                      "unitPrice": 15.00,
                      "orderAmount": 15.00,
                      "paymentAmount": 15.00,
                      "paymentSource": "ALIPAY",
                      "providerOrderId": "provider-order-123",
                      "orderSource": "WEB",
                      "status": "PAID",
                      "orderedAt": "2026-08-17T10:00:00Z",
                      "stateChangedAt": "2026-08-17T10:07:00Z",
                      "createdAt": "2026-08-17T10:00:00Z",
                      "updatedAt": "2026-08-17T10:07:00Z"
                    }
                  ],
                  "paymentTransactions": [],
                  "messageDeliveries": [],
                  "compensationTasks": [
                    {
                      "taskId": "compensation-1",
                      "orderId": "order-123",
                      "action": "RELEASE_STOCK",
                      "status": "PENDING",
                      "retryCount": 0,
                      "maxRetries": 3,
                      "createdAt": "2026-08-17T10:11:00Z",
                      "lastAttemptAt": null,
                      "lastError": null
                    }
                  ],
                  "traceSummaries": [
                    {
                      "traceId": "trace-1",
                      "orderId": "order-123",
                      "correlationId": "correlation-order-123",
                      "startedAt": "2026-08-17T10:05:00Z",
                      "endedAt": null,
                      "complete": false,
                      "summary": "incomplete trace"
                    }
                  ],
                  "failures": []
                }
                """;
    }

    private static String validJson() {
        return """
                {
                  "schemaVersion": 1,
                  "observedAt": "2026-08-17T10:12:00Z",
                  "orders": [
                    {
                      "orderId": "order-123",
                      "masterOrderId": null,
                      "role": "SINGLE",
                      "productId": "product-1",
                      "productName": "Diagnostic Widget",
                      "productType": "COURSE",
                      "goodsCount": 2,
                      "unitPrice": 15.00,
                      "orderAmount": 30.00,
                      "paymentAmount": 30.00,
                      "paymentSource": "ALIPAY",
                      "providerOrderId": "provider-order-123",
                      "orderSource": "WEB",
                      "status": "PAID",
                      "orderedAt": "2026-08-17T10:00:00Z",
                      "stateChangedAt": "2026-08-17T10:07:00Z",
                      "createdAt": "2026-08-17T10:00:00Z",
                      "updatedAt": "2026-08-17T10:07:00Z"
                    }
                  ],
                  "paymentTransactions": [
                    {
                      "transactionId": "payment-1",
                      "orderId": "order-123",
                      "provider": "ALIPAY",
                      "amount": 30.00,
                      "status": "CALLBACK_RECEIVED",
                      "requestedAt": "2026-08-17T10:05:00Z",
                      "providerCompletedAt": "2026-08-17T10:06:00Z",
                      "callbackReceivedAt": "2026-08-17T10:07:00Z",
                      "providerErrorCode": null,
                      "providerErrorSummary": null
                    }
                  ],
                  "messageDeliveries": [
                    {
                      "deliveryId": "message-1",
                      "orderId": "order-123",
                      "eventType": "OrderPaid",
                      "correlationId": "correlation-order-123",
                      "status": "CONSUMED",
                      "createdAt": "2026-08-17T10:08:00Z",
                      "sentAt": "2026-08-17T10:09:00Z",
                      "consumedAt": "2026-08-17T10:10:00Z",
                      "lastError": null
                    }
                  ],
                  "compensationTasks": [
                    {
                      "taskId": "compensation-1",
                      "orderId": "order-123",
                      "action": "RELEASE_STOCK",
                      "status": "SUCCEEDED",
                      "retryCount": 1,
                      "maxRetries": 3,
                      "createdAt": "2026-08-17T10:11:00Z",
                      "lastAttemptAt": null,
                      "lastError": null
                    }
                  ],
                  "traceSummaries": [
                    {
                      "traceId": "trace-1",
                      "orderId": "order-123",
                      "correlationId": "correlation-order-123",
                      "startedAt": "2026-08-17T10:05:00Z",
                      "endedAt": "2026-08-17T10:10:00Z",
                      "complete": true,
                      "summary": "complete trace"
                    }
                  ],
                  "failures": [
                    {
                      "source": "PAYMENT",
                      "orderId": "order-123",
                      "kind": "TIMEOUT"
                    }
                  ]
                }
                """;
    }

    private static final class ThrowingResource extends ByteArrayResource {

        private final String path;

        private ThrowingResource(String path) {
            super(new byte[0]);
            this.path = path;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException("cannot read " + path);
        }

        @Override
        public String getDescription() {
            return "file [" + path + "]";
        }
    }
}
