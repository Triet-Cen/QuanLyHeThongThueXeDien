package com.example.QuanLyChoThueXeDien.category.dtos;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleCategoryForm {
    @NotBlank(message = "Vui lòng nhập tên loại xe.")
    @Size(max = 60, message = "Tên loại xe tối đa 60 ký tự.")
    private String categoryName;

    @Size(max = 200, message = "Mô tả tối đa 200 ký tự.")
    private String description;

    public String getCategoryName() {
        return categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
