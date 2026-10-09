package com.project.soa.dto.bcrp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BcrpSeriesDto {
    private String name;
    private String dec;
}
