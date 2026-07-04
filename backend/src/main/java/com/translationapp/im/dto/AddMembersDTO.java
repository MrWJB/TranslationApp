package com.translationapp.im.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddMembersDTO {
    @NotEmpty
    private List<Long> memberIds;
}
