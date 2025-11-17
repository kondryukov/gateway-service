package org.example.gatewayservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationTest {

    @Autowired
    private WebTestClient webClient;

    private static WireMockServer userServiceMock;
    private static WireMockServer notificationServiceMock;

    @BeforeAll
    static void startMocks() {
        userServiceMock = new WireMockServer(9001);
        userServiceMock.start();

        notificationServiceMock = new WireMockServer(9002);
        notificationServiceMock.start();
    }

    @AfterAll
    static void stopMocks() {
        if (userServiceMock != null) {
            userServiceMock.stop();
        }
        if (notificationServiceMock != null) {
            notificationServiceMock.stop();
        }
    }

    @BeforeEach
    void resetMocks() {
        userServiceMock.resetAll();
        notificationServiceMock.resetAll();
    }

    @Test
    void shouldRouteUsersThroughGateway() {
        String body = """
                {
                    "id": 1,
                    "name": "name",
                    "email": "name@mail.ru",
                    "age": 123
                }
                """;

        userServiceMock.stubFor(get(urlEqualTo("/users/read/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        webClient.get()
                .uri("/users/read/{id}", 1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.email").isEqualTo("name@mail.ru");
    }

    @Test
    void shouldRouteNotificationThroughGateway() {
        String body = """
                {
                    "email": "name@mail.ru",
                    "operation": "CREATE"
                }
                """;

        notificationServiceMock.stubFor(post(urlEqualTo("/notification/create"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        webClient.post()
                .uri("/notification/create")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("name@mail.ru")
                .jsonPath("$.operation").isEqualTo("CREATE");
    }

    @Test
    void shouldReturnFallbackWhenUserServiceLongWait() {
        userServiceMock.stubFor(get(urlEqualTo("/users/read/1"))
                .willReturn(aResponse()
                        .withFixedDelay(3000)
                        .withStatus(200)));


        webClient.get()
                .uri("/users/read/{id}", 1)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .isEqualTo("User service is unavailable. Try again later");
    }

    @Test
    void shouldReturnFallbackWhenNotificationServiceLongWait() {
        notificationServiceMock.stubFor(post(urlEqualTo("/notification/create"))
                .willReturn(aResponse()
                        .withFixedDelay(3000)
                        .withStatus(200)));

        webClient.post()
                .uri("/notification/create")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .isEqualTo("Notification service is unavailable. Try again later");
    }

    @Test
    void shouldReturnFallbackWhenUserServiceFalls() {
        userServiceMock.stop();
        webClient.get()
                .uri("/users/read/{id}", 1)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .isEqualTo("User service is unavailable. Try again later");
    }
}