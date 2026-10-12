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
#
# PHP_CLI_SERVER_WORKERS: the built-in server handles ONE request at a time,
# so a single slow request (a database round-trip, an upload) makes every other
# request wait behind it - and the app sends several calls at once whenever a
# dashboard opens (SyncManager.syncAllSilently pushes pending classes,
# memberships, join requests, lessons, quizzes, results and the profile photo).
# With a single worker that queue ran past the app's 10-second timeout
# (ApiClient.java) and showed up in the Railway logs as a stream of 499s; the
# workers below let those calls overlap instead. Linux only - PHP has no
# forked workers on Windows, where the local XAMPP setup needs none anyway -
# and a plain PHP_CLI_SERVER_WORKERS variable overrides the default on a
# bigger plan.
CMD PHP_CLI_SERVER_WORKERS=${PHP_CLI_SERVER_WORKERS:-4} php -d upload_max_filesize=32M -d post_max_size=34M -d memory_limit=256M -S 0.0.0.0:${PORT:-8080} router.php
