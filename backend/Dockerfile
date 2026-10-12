# KONEKTED API - runs on Render, which has no native PHP runtime, so the
# service is deployed as this Docker container (see render.yaml: runtime: docker).
#
# Inside the container it is exactly the setup that was tested locally with
# PHP 8.2: the built-in server, with router.php as the only public surface
# (serves / and /api/*.php; config/, supabase/, tests/, attachments/ -> 404).
FROM php:8.2-cli

# The only extension the app needs beyond the defaults: the PostgreSQL driver
# (Supabase is reached through PDO pgsql, sslmode=require).
RUN apt-get update \
 && apt-get install -y --no-install-recommends libpq-dev \
 && docker-php-ext-install pdo_pgsql \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /var/www/html

# The Docker build context is the repository root (see .dockerignore there);
# only the backend folder enters the image - including router.php, the api/
# endpoints and the .htaccess files (unused here, harmless).
COPY backend/ /var/www/html/

# Render and Railway both route traffic to $PORT (e.g. 10000 / 8080).
# Shell form so the variable expands; default 8080 if a host forgets to set it.
# The -d flags raise PHP's conservative CLI defaults so a 5 MB profile photo
# or a lesson PDF can actually be uploaded (CLI defaults are 2 MB).
CMD php -d upload_max_filesize=32M -d post_max_size=34M -d memory_limit=256M -S 0.0.0.0:${PORT:-8080} router.php
