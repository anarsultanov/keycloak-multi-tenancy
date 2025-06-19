package dev.sultanov.keycloak.multitenancy.dto;

import java.util.List;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BusinessStatusRequest {

    private List<BusinessStatusEntry> businessStatusList;
}
