package com.translationapp.im.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Data
@Embeddable
public class UserDepartmentId implements Serializable {
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "department_id")
    private Long departmentId;
}
