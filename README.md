# MDSE
Metadata Driven Schema Engine is a Java framework for building metadata-driven systems.

At its core, MDSE provides a runtime schema model that represents entities, fields, relationships, constraints, and other structural metadata independently of handwritten domain classes. 
Applications can interact with this metadata model through validation, dynamic repositories, query services, and schema-aware infrastructure.

MDSE supports two approaches:

* Define schemas explicitly through metadata.
* Discover schemas automatically from existing relational databases through introspection.

This allows MDSE to support both greenfield development and integration with existing systems.

## Features

### Metadata-Driven Schema Model

Represent application structures as metadata rather than tightly coupling behavior to compiled entity classes.

### Schema Registry

Store and manage schema definitions through a centralized registry.

### Dynamic Repository Layer

Perform CRUD and query operations using metadata definitions rather than generated repositories.

### Validation Framework

Apply validation rules directly from schema definitions.

### Relationship Graph Model

Represent and navigate entity relationships through a schema graph.

### Database Introspection

Automatically discover:

* Tables
* Columns
* Data types
* Primary keys
* Foreign key relationships

and register them within the MDSE schema model.

This enables existing databases to be brought under metadata management without manually recreating schema definitions.

## Module Structure

### mdse-core

Core runtime functionality including:

* Schema model
* Schema registry
* Validation
* Relationship graph
* Dynamic repository infrastructure

### mdse-introspection

Database metadata discovery and registration including:

* JDBC schema introspection
* Metadata extraction
* Schema registration services

## Future Direction

The project is designed to serve as the foundation for additional metadata-driven tooling, including:

* Code generation
* Project scaffolding
* API generation

These capabilities are planned but are not yet part of the current implementation.

## Building

```bash
git clone https://github.com/Utilitron/mdse.git
cd mdse
mvn clean install
```

## Requirements

* Java 17+
* Maven 3.9+

## Status

MDSE is under active development and APIs may evolve between releases.

Contributions, feedback, and design discussions are welcome.
