# CircleGuard Release Notes - v3

**Date:** 2026-05-10
**Build:** 3
**Commit:** d5d375b
**Author:** Juan Camilo Muñoz Barco
**Environment:** master / production
**Range:** last 20 commits

---

## Services Released

| Service              | Port | Image Tag      |
|----------------------|------|----------------|
| auth-service         | 8180 | v3   |
| identity-service     | 8083 | v3   |
| form-service         | 8086 | v3   |
| promotion-service    | 8088 | v3   |
| notification-service | 8082 | v3   |
| dashboard-service    | 8084 | v3   |

---

## Changes

### Features

- feat: add scripts for release notes generation, E2E testing, and performance testing (Juan Camilo Muñoz Barco)
- feat: configure Git identity for Jenkins CI in Dockerfile (Juan Camilo Muñoz Barco)
- feat: add local bin directory to PATH for locust installation in Jenkinsfiles (Juan Camilo Muñoz Barco)
- feat: update image tagging in Jenkinsfiles to use dynamic tags based on SKIP_DOCKER_BUILD parameter (Juan Camilo Muñoz Barco)
- feat: add SKIP_DOCKER_BUILD parameter to Jenkinsfiles and update build conditions (Juan Camilo Muñoz Barco)
- feat: add Role and RoleBinding for jenkins in circleguard-stage and circleguard-master namespaces (Juan Camilo Muñoz Barco)
- feat: add Redis embedded server for integration tests and update test configurations (Juan Camilo Muñoz Barco)
- feat: replace Testcontainers with embedded Neo4j for integration tests and update test configurations (Juan Camilo Muñoz Barco)
- feat: remove DOCKER_HOST variable from Jenkinsfiles and add testcontainers.properties for Docker configuration (Juan Camilo Muñoz Barco)
- feat: add DOCKER_HOST environment variable to Jenkinsfiles for Docker communication (Juan Camilo Muñoz Barco)
- feat: add H2 database dependency and configure test profiles for integration tests (Juan Camilo Muñoz Barco)
- feat: enhance Kubernetes deployment process in Jenkinsfile with improved configuration and commands (Juan Camilo Muñoz Barco)
- feat: update LDAP configuration in SecurityConfig to use properties for better flexibility (Juan Camilo Muñoz Barco)
 
### Bug Fixes

- fix: remove skipTlsVerify option from kubeconfig steps in Jenkinsfiles (Juan Camilo Muñoz Barco)
- fix: use pip3 with --break-system-packages for locust install in CI (Juan Camilo Muñoz Barco)### Other Changes

- refactor: remove tagging steps from Jenkins pipeline and Git configuration from Dockerfile (Juan Camilo Muñoz Barco)
- ci: add Dockerfiles, Kubernetes manifests, and update Jenkins pipelines for CircleGuard services (Juan Camilo Muñoz Barco)
- refactor: streamline Kubernetes deployment process by removing initContainers and adding infrastructure installation script (Juan Camilo Muñoz Barco)
- tests: enhance SurveyListener integration tests with Redis and Neo4j setup (Juan Camilo Muñoz Barco)
- refactor: enhance status code assertions in getPendingSurveys test for clarity (Juan Camilo Muñoz Barco)

## Deployment

- Kubernetes namespace: circleguard-master
- Infrastructure: PostgreSQL 16, Neo4j 5.26, Kafka 7.6.0, Redis 7.2
- Rollout strategy: Rolling update (zero downtime)
