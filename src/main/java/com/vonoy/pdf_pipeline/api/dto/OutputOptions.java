package com.vonoy.pdf_pipeline.api.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor
public class OutputOptions {
    @NotBlank private String fileName;
    private boolean inline;
}
