package am.itspace.orderservice;

import am.itspace.orderservice.book.Book;
import am.itspace.orderservice.book.BookClient;
import am.itspace.orderservice.order.domain.Order;
import am.itspace.orderservice.order.domain.OrderStatus;
import am.itspace.orderservice.web.OrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.10"));

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private BookClient bookClient;

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", OrderServiceApplicationTests::r2dbcUrl);
        registry.add("spring.r2dbc.username", postgresql::getUsername);
        registry.add("spring.r2dbc.password", postgresql::getPassword);
        registry.add("spring.flyway.url", postgresql::getJdbcUrl);
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", postgresql.getHost(),
                postgresql.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), postgresql.getDatabaseName());
    }

    @Test
    void whenGetOwnOrdersThenReturn() throws IOException {
        String bookIsbn = "1234567893";
        Book book = new Book(bookIsbn, "Title", "Author", 9.90);
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

        Order expectedOrder = webTestClient.post().uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();
        assertThat(expectedOrder).isNotNull();

        webTestClient.get().uri("/orders")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(Order.class).value(orders -> {
                    List<Long> orderIds = orders.stream()
                            .map(Order::id)
                            .collect(Collectors.toList());
                    assertThat(orderIds).contains(expectedOrder.id());
                });
    }

    @Test
    void whenGetOrdersForAnotherUserThenNotReturned() {
        String bookIsbn = "1234567899";
        Book book = new Book(bookIsbn, "Title", "Author", 9.90);
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
        OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

        Order orderByBjorn = webTestClient.post().uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();
        assertThat(orderByBjorn).isNotNull();

        Order orderByIsabelle = webTestClient.post().uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).returnResult().getResponseBody();
        assertThat(orderByBjorn).isNotNull();

        webTestClient.get().uri("/orders")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(Order.class)
                .value(orders -> {
                    List<Long> orderIds = orders.stream()
                            .map(Order::id)
                            .collect(Collectors.toList());
                    assertThat(orderIds).contains(orderByBjorn.id());
                    assertThat(orderIds).contains(orderByIsabelle.id());
                });
    }

    @Test
    void whenPostRequestAndBookExistsThenOrderAccepted() {
        String bookIsbn = "1234567890";
        Book book = new Book(bookIsbn, "Title", "Author", 9.90);
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
        OrderRequest orderRequest = new OrderRequest(bookIsbn, 3);

        webTestClient.post().uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class)
                .value(order -> {
                    assertThat(order.bookIsbn()).isEqualTo(orderRequest.isbn());
                    assertThat(order.quantity()).isEqualTo(orderRequest.quantity());
                    assertThat(order.bookName()).isEqualTo(book.title() + "-" + book.author());
                    assertThat(order.bookPrice()).isEqualTo(book.price());
                    assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
                })
                .returnResult().getResponseBody();
    }

    @Test
    void whenPostRequestAndBookNotExistsThenOrderRejected() {
        String bookIsbn = "1234567894";
        given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.empty());
        OrderRequest orderRequest = new OrderRequest(bookIsbn, 3);

        webTestClient.post().uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class)
                .value(order -> {
                    assertThat(order.bookIsbn()).isEqualTo(orderRequest.isbn());
                    assertThat(order.quantity()).isEqualTo(orderRequest.quantity());
                    assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
                });
    }
}
