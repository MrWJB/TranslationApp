package com.translationapp.im.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "user_departments")
public class UserDepartment {
    @EmbeddedId
    private UserDepartmentId id;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;
}
