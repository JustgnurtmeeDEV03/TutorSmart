package com.dossier.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Java Record - Tạo ra một object bất biến (immutable)
// Dữ liệu truyền vào không thể bị sửa đổi ngoài ý muốn trong suốt vòng đời request
public record RegisterRequest(

    // Không để dữ liệu rác lọt vào Service/DB rồi mới báo lỗi

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    String password,

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 100, message = "Display name must be 2-100 characters")
    String displayName
) {}
