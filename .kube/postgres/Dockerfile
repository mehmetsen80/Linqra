FROM postgres:15

# Create custom data directory and set ownership
RUN mkdir -p /var/lib/postgresql/pgdata && \
    chown postgres:postgres /var/lib/postgresql/pgdata

# Set custom data directory
ENV PGDATA=/var/lib/postgresql/pgdata