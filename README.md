# Exchange System

Unified repository for high-performance exchange engines and domain services.

## Structure

- **[engines/](./engines)**: Core matching and risk engines.
  - **jvm/**: Kotlin/Aeron based ultra-low latency engine.
  - **rust/**: Rust/Tokio based high-performance engine.
  - **shared/**: SBE protocol definitions.
  - **scripts/**: Simulation and testing tools.
- **[services/](./services)**: Domain-specific microservices.
  - **account-service/**: Identity, Access Management, and User Accounts.

## Getting Started

Please refer to the README in each subdirectory for specific instructions.
