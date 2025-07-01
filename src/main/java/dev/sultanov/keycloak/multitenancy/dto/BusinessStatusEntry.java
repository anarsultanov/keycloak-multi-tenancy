package dev.sultanov.keycloak.multitenancy.dto;

import java.util.List;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BusinessStatusEntry {

    private String status;
    private List<String> businessId;
}
