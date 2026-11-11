targetScope = 'resourceGroup'

@minLength(3)
@maxLength(16)
param prefix string = 'ecoinvest'
param location string = resourceGroup().location

@description('Tag already pushed to the registry created by foundation.bicep. Use a unique release tag.')
param imageTag string

var suffix = uniqueString(subscription().subscriptionId, resourceGroup().id, prefix)

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: 'eco${suffix}'
}
resource vault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: 'eco-${suffix}'
}
resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' existing = {
  name: '${prefix}-runtime'
}
resource frontendIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' existing = {
  name: '${prefix}-frontend-runtime'
}
resource environment 'Microsoft.App/managedEnvironments@2025-01-01' existing = {
  name: '${prefix}-environment'
}

resource backend 'Microsoft.App/containerApps@2025-01-01' = {
  name: '${prefix}-backend'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${identity.id}': {} }
  }
  properties: {
    managedEnvironmentId: environment.id
    workloadProfileName: 'Consumption'
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8081
        transport: 'http'
        allowInsecure: false
      }
      registries: [ { server: registry.properties.loginServer, identity: identity.id } ]
      secrets: [ {
        name: 'senace-auth-key'
        keyVaultUrl: '${vault.properties.vaultUri}secrets/senace-auth-key'
        identity: identity.id
      } ]
    }
    template: {
      containers: [ {
        name: 'backend'
        image: '${registry.properties.loginServer}/ecoinvest/backend:${imageTag}'
        resources: { cpu: 1, memory: '2Gi' }
        env: [
          { name: 'SPRING_PROFILES_ACTIVE', value: 'azure' }
          { name: 'SENACE_BASE_URL', value: 'https://datosabiertos.senace.gob.pe/Api/datastreams' }
          { name: 'SENACE_AUTH_KEY', secretRef: 'senace-auth-key' }
        ]
        probes: [
          {
            type: 'Startup'
            httpGet: { path: '/actuator/health/liveness', port: 8081, scheme: 'HTTP' }
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 48
          }
          {
            type: 'Liveness'
            httpGet: { path: '/actuator/health/liveness', port: 8081, scheme: 'HTTP' }
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          }
          {
            type: 'Readiness'
            httpGet: { path: '/actuator/health/readiness', port: 8081, scheme: 'HTTP' }
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3
          }
        ]
      } ]
      // Circuit breakers and snapshots are process-local. Do not silently scale replicas.
      scale: { minReplicas: 1, maxReplicas: 1 }
    }
  }
}

resource frontend 'Microsoft.App/containerApps@2025-01-01' = {
  name: '${prefix}-frontend'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${frontendIdentity.id}': {} }
  }
  properties: {
    managedEnvironmentId: environment.id
    workloadProfileName: 'Consumption'
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'http'
        allowInsecure: false
      }
      registries: [ { server: registry.properties.loginServer, identity: frontendIdentity.id } ]
    }
    template: {
      containers: [ {
        name: 'frontend'
        image: '${registry.properties.loginServer}/ecoinvest/frontend:${imageTag}'
        resources: { cpu: json('0.25'), memory: '0.5Gi' }
        env: [ { name: 'BACKEND_FQDN', value: backend.properties.configuration.ingress.fqdn } ]
        probes: [
          {
            type: 'Startup'
            httpGet: { path: '/healthz', port: 8080, scheme: 'HTTP' }
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 24
          }
          {
            type: 'Liveness'
            httpGet: { path: '/healthz', port: 8080, scheme: 'HTTP' }
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          }
          {
            type: 'Readiness'
            httpGet: { path: '/healthz', port: 8080, scheme: 'HTTP' }
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3
          }
        ]
      } ]
      scale: { minReplicas: 0, maxReplicas: 2 }
    }
  }
}

output frontendUrl string = 'https://${frontend.properties.configuration.ingress.fqdn}'
output backendInternalFqdn string = backend.properties.configuration.ingress.fqdn
