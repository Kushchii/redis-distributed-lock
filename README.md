```markdown
# Redis distributed lock service

## Overview

The Redis distributed lock service is a Spring Boot application designed to handle transaction-related operations. It
uses PostgreSQL for data persistence and Redis for locking.

## Technologies Used

- Java
- Spring Boot
- Gradle
- PostgreSQL
- Redis
- Reactor (for reactive programming)

## Prerequisites

- Java 17 or higher
- Gradle 7.0 or higher
- Docker and Docker Compose

## Getting Started

### Clone the Repository

```sh
git clone https://github.com/Kushchii/redis-distributed-lock.git
cd redis-distributed-lock
```

### Build the Project

```sh
./gradlew build
```

### Running with Docker Compose

Ensure Docker and Docker Compose are installed on your machine. To start the services, run:

```sh
docker-compose up
```

This will start the following services:

- `postgres_1`: PostgreSQL database
- `redis_data`: Redis

### Running the Application

To run the application locally, use:

```sh
./gradlew bootRun
```

### API Endpoints

#### Create Transaction

- **URL**: `/api/transactions`
- **Method**: `POST`
- **Request Body**:
  ```json
  {
    "id": "string",
    "amount": "number",
    "userId": "string",
    "currency": "string",
    "status": "string",
    "description": "string"
  }
  ```
- **Response**:
  ```json
  {
    "message": "Transaction saved successfully"
  }
  ```

#### Send callback

- **URL**: `/api/callback`
- **Method**: `POST`
- **Request Body**:
  ```json
  {
    "id": "string",
    "status": "string"
  }
  ```
- **Response**:
  ```json
  {
    "message": "Callback processed successfully"
  }
  ```