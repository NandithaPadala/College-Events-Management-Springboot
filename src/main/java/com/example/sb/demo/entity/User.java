package com.example.sb.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String role; // "ADMIN" or "STUDENT"

    @Column(nullable = false)
    private String fullName;

    // Additional fields for students
    private String studentId;
    private String department;
    private String year;
    
	public boolean isAdmin() {
		return this.getRole().equals("ADMIN");
	}
	
}