package com.translationapp.im.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.translationapp.im.domain.FriendRequestStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendRequestDtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializesNestedFromUserWithRealName() throws Exception {
        UserSummaryDTO fromUser = new UserSummaryDTO();
        fromUser.setId(3L);
        fromUser.setUsername("zhangsan");
        fromUser.setRealName("张三");

        FriendRequestDTO dto = new FriendRequestDTO();
        dto.setId(1L);
        dto.setFromUserId(3L);
        dto.setFromUser(fromUser);
        dto.setToUserId(2L);
        dto.setMessage("hello");
        dto.setStatus(FriendRequestStatus.PENDING);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(json.has("fromUser")).isTrue();
        assertThat(json.get("fromUser").get("realName").asText()).isEqualTo("张三");
        assertThat(json.get("fromUser").get("username").asText()).isEqualTo("zhangsan");
        assertThat(json.has("fromRealName")).isFalse();
        assertThat(json.has("fromUsername")).isFalse();
    }
}
