# =============================================================================
# SecureScope AI - Vulnerability Assessment Cost Estimator (Mockup)
#
# Multi-stage build:
#   1. "build" - a well-known Maven+JDK image (guaranteed to have Maven,
#      tar/gzip/unzip, etc. - the minimal runtime image used below does not)
#      compiles the Quarkus fast-jar. The build is self-contained: it does
#      not require Maven (or anything else besides Podman/Docker) on the
#      host. Compiling with JDK 21 and running on JDK 25 below is safe -
#      newer JVMs run older bytecode (this project targets release 21).
#   2. runtime - copies only the built artifacts onto the exact base image
#      requested for this application, registry.access.redhat.com/ubi9/
#      openjdk-25:1.24.
#
# Build:  podman build -t securescope-ai:latest .
# Run:    see README.md "Running with Podman" for full instructions.
# =============================================================================

FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Leverage layer caching: dependencies first, then source.
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline || true

COPY src/ src/
RUN mvn -q -B -DskipTests clean package

# -----------------------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-25:1.24

ENV LANGUAGE='en_US:en'

# Quarkus fast-jar layout.
COPY --from=build --chown=185:root /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185:root /build/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185:root /build/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185:root /build/target/quarkus-app/quarkus/ /deployments/quarkus/

# Persistent data (uploaded ZIPs + the H2 database file) lives here. Owned by
# the default UBI image user (185) but made group-writable too (group "root")
# so it also works when OpenShift assigns an arbitrary non-root UID with gid 0.
RUN mkdir -p /deployments/data \
    && chown -R 185:root /deployments/data \
    && chmod -R g+rwX /deployments/data
VOLUME ["/deployments/data"]

EXPOSE 8080

USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
