package com.learnkafka.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.domain.Book;
import com.learnkafka.domain.LibraryEvent;
import com.learnkafka.domain.LibraryEventType;
import com.learnkafka.producer.LibraryEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibraryEventsController.class)
@AutoConfigureMockMvc
public class LibraryEventControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    LibraryEventProducer libraryEventProducer;

    @Test
    void postLibraryEvent() throws Exception {
        // given
        Book book = Book.builder()
                .bookId(123)
                .bookAuthor("Ciprian")
                .bookName("Kafka using Spring Boot")
                .build();

        LibraryEvent libraryEvent = LibraryEvent.builder()
                .libraryEventId(null)
                .book(book)
                .libraryEventType(LibraryEventType.NEW)
                .build();

        when(libraryEventProducer.sendLibraryEventApproach2(isA(LibraryEvent.class))).thenReturn(null);

        // expect
        mockMvc.perform(
                        post("/v1/libraryevent")
                                .content(objectMapper.writeValueAsString(libraryEvent))
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void postLibraryEvent_4xx() throws Exception {
        // given
        Book book = Book.builder()
                .bookId(null)
                .bookAuthor(null)
                .bookName("Kafka using Spring Boot")
                .build();

        LibraryEvent libraryEvent = LibraryEvent.builder()
                .libraryEventId(null)
                .book(book)
                .libraryEventType(LibraryEventType.NEW)
                .build();

        when(libraryEventProducer.sendLibraryEventApproach2(isA(LibraryEvent.class))).thenReturn(null);

        String expectedErrorMessage = "book.bookAuthor - must not be blank, book.bookId - must not be null";

        // expect
        mockMvc.perform(
                        post("/v1/libraryevent")
                                .content(objectMapper.writeValueAsString(libraryEvent))
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(expectedErrorMessage));
    }

    @Test
    void updateLibraryEvent() throws Exception {
        // given
        Book book = Book.builder()
                .bookId(123)
                .bookAuthor("Ciprian")
                .bookName("Kafka using Spring Boot")
                .build();

        LibraryEvent libraryEvent = LibraryEvent.builder()
                .libraryEventId(456)
                .book(book)
                .libraryEventType(LibraryEventType.UPDATE)
                .build();

        when(libraryEventProducer.sendLibraryEventApproach2(isA(LibraryEvent.class))).thenReturn(null);

        // expect
        mockMvc.perform(
                        put("/v1/libraryevent")
                                .content(objectMapper.writeValueAsString(libraryEvent))
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void updateLibraryEvent_withNullLibraryEventId() throws Exception {
        // given
        Book book = Book.builder()
                .bookId(123)
                .bookAuthor("Ciprian")
                .bookName("Kafka using Spring Boot")
                .build();

        LibraryEvent libraryEvent = LibraryEvent.builder()
                .libraryEventId(null)
                .book(book)
                .build();

        when(libraryEventProducer.sendLibraryEventApproach2(isA(LibraryEvent.class))).thenReturn(null);

        String expectedErrorMessage = "Please pass the library event id";

        // expect
        mockMvc.perform(
                        put("/v1/libraryevent")
                                .content(objectMapper.writeValueAsString(libraryEvent))
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(expectedErrorMessage));
    }
}
