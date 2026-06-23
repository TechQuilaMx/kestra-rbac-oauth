# Docker Compose Setup Guide

This guide will help you run Kestra SSO with OAuth2 RBAC using Docker Compose.

## Prerequisites

- Docker installed (version 20.10+)
- Docker Compose installed (version 2.0+)
- At least 4GB of available RAM
- Ports 8080, 8081, 8085, 9000, 9001, 5432 available

## Quick Start

### 1. Copy Environment Variables

```bash
cp .env.example .env
```

Edit `.env` and update the values as needed (especially `OAUTH2_CLIENT_SECRET`).

### 2. Start the Stack

```bash
docker-compose -f docker-compose-sso.yml up -d
```

This will start:
- **PostgreSQL** (port 5432) - Database for Kestra
- **MinIO** (ports 9000, 9001) - S3-compatible storage
- **Keycloak** (port 8085) - OAuth2/OIDC provider
- **Kestra** (ports 8080, 8081) - Main application

### 3. Wait for Services to Start

Check service health:
```bash
docker-compose -f docker-compose-sso.yml ps
```

All services should show "healthy" status. This may take 1-2 minutes.

### 4. Configure Keycloak

#### Access Keycloak Admin Console

1. Open http://localhost:8085
2. Click "Administration Console"
3. Login with:
   - Username: `admin`
   - Password: `admin`

#### Create Kestra Client

1. Go to **Clients** → Click **Create client**
2. Fill in:
   - **Client ID**: `kestra-app`
   - **Client Protocol**: `openid-connect`
   - Click **Next**

3. Configure Capability:
   - **Client authentication**: ON
   - **Authorization**: OFF
   - **Standard flow**: ON
   - **Direct access grants**: ON
   - Click **Next**

4. Configure Access Settings:
   - **Root URL**: `http://localhost:8080`
   - **Home URL**: `http://localhost:8080`
   - **Valid redirect URIs**: 
     - `http://localhost:8080/*`
     - `http://localhost:5173/*` (for frontend dev)
   - **Valid post logout redirect URIs**: `+`
   - **Web origins**: 
     - `http://localhost:8080`
     - `http://localhost:5173`
   - Click **Save**

5. Get Client Secret:
   - Go to **Credentials** tab
   - Copy the **Client Secret**
   - Update your `.env` file: `OAUTH2_CLIENT_SECRET=<copied-secret>`

#### Create Roles

1. Go to **Realm roles** → Click **Create role**
2. Create two roles:
   - Role name: `kestra-admin`
   - Role name: `kestra-operator`

#### Create Test Users

**Admin User:**
1. Go to **Users** → Click **Create new user**
2. Fill in:
   - **Username**: `admin@example.com`
   - **Email**: `admin@example.com`
   - **Email verified**: ON
   - **First name**: `Admin`
   - **Last name**: `User`
   - Click **Create**

3. Set password:
   - Go to **Credentials** tab
   - Click **Set password**
   - Password: `admin123`
   - Temporary: OFF
   - Click **Save**

4. Assign role:
   - Go to **Role mappings** tab
   - Click **Assign role**
   - Filter by "Realm roles"
   - Select `kestra-admin`
   - Click **Assign**

**Operator User:**
1. Repeat the same process with:
   - Username: `operator@example.com`
   - Email: `operator@example.com`
   - Password: `operator123`
   - Role: `kestra-operator`

### 5. Restart Kestra with Client Secret

Update the `.env` file with the client secret from step 4, then restart:

```bash
docker-compose -f docker-compose-sso.yml restart kestra
```

### 6. Access Kestra

Open http://localhost:8080

You should see the OAuth2 login button. Click it and login with:
- Admin: `admin@example.com` / `admin123`
- Operator: `operator@example.com` / `operator123`

### 7. Create MinIO Bucket (Optional, for S3 storage)

1. Open MinIO Console: http://localhost:9001
2. Login with: `minioadmin` / `minioadmin`
3. Go to **Buckets** → Click **Create Bucket**
4. Bucket name: `kestra`
5. Click **Create Bucket**

## Services Overview

| Service | Port | URL | Credentials |
|---------|------|-----|-------------|
| Kestra | 8080, 8081 | http://localhost:8080 | Via Keycloak OAuth2 |
| Keycloak | 8085 | http://localhost:8085 | admin / admin |
| MinIO Console | 9001 | http://localhost:9001 | minioadmin / minioadmin |
| MinIO API | 9000 | http://localhost:9000 | minioadmin / minioadmin |
| PostgreSQL | 5432 | localhost:5432 | kestra / k3str4 |

## Useful Commands

### View Logs
```bash
# All services
docker-compose -f docker-compose-sso.yml logs -f

# Specific service
docker-compose -f docker-compose-sso.yml logs -f kestra
docker-compose -f docker-compose-sso.yml logs -f keycloak
```

### Stop Services
```bash
docker-compose -f docker-compose-sso.yml stop
```

### Start Services
```bash
docker-compose -f docker-compose-sso.yml start
```

### Restart Kestra
```bash
docker-compose -f docker-compose-sso.yml restart kestra
```

### Clean Up (Remove all data)
```bash
docker-compose -f docker-compose-sso.yml down -v
```

### Rebuild Kestra Image
If you've made code changes:
```bash
# Build custom image
docker build -f Dockerfile.rbac -t ghcr.io/YOUR_USERNAME/kestra-sso:latest .

# Update docker-compose-sso.yml to use: build: . instead of image:
# Or just restart if using the built image
docker-compose -f docker-compose-sso.yml up -d --force-recreate kestra
```

## Troubleshooting

### Kestra fails to start

**Check logs:**
```bash
docker-compose -f docker-compose-sso.yml logs kestra
```

**Common issues:**
- PostgreSQL not ready: Wait 30 seconds and restart kestra
- Client secret not set: Update `.env` with correct secret
- Port conflicts: Check if ports 8080, 8085, 9000, 5432 are free

### Keycloak not accessible

```bash
docker-compose -f docker-compose-sso.yml logs keycloak
```

Wait for the message: "Keycloak 24.x.x started"

### OAuth2 login fails

1. Check client secret matches in Keycloak and `.env`
2. Verify redirect URIs in Keycloak client settings
3. Check Kestra logs for OAuth2 errors

### Can't see Create Flow button (Admin user)

1. Verify user has `kestra-admin` role in Keycloak
2. Check `/api/v1/user/me` returns `isAdmin: true`
3. Clear browser cache and re-login

### MinIO storage issues

1. Ensure bucket "kestra" exists in MinIO
2. Check MinIO credentials match in docker-compose
3. Verify MinIO is healthy: `docker-compose -f docker-compose-sso.yml ps`

## Customization

### Use Custom Configuration File

1. Create `application-override.yml` with your settings
2. Uncomment the volume mount in `docker-compose-sso.yml`:
   ```yaml
   volumes:
     - ./application-override.yml:/app/kestra/application-override.yml:ro
   ```
3. Restart: `docker-compose -f docker-compose-sso.yml restart kestra`

### Change Storage Backend

Edit the storage section in `docker-compose-sso.yml`:

**For local storage:**
```yaml
storage:
  type: local
  local:
    base-path: "/app/storage"
```

**For MinIO (already configured):**
```yaml
storage:
  type: minio
  minio:
    endpoint: http://minio:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: kestra
```

### Add More Operator Permissions

Edit `authorization.operator-permissions` in `docker-compose-sso.yml` and add:
```yaml
- flows.create
- flows.edit
- executions.restart
```

Restart Kestra to apply changes.

## Production Considerations

For production use:

1. **Change all default passwords** in `.env`
2. **Use PostgreSQL external instance** (not Docker container)
3. **Use external Keycloak** (not embedded)
4. **Enable HTTPS** with reverse proxy (nginx/traefik)
5. **Use Docker secrets** for sensitive data
6. **Set up backups** for PostgreSQL and MinIO
7. **Configure resource limits** in docker-compose
8. **Use volume backups** for data persistence

## Support

For issues:
- Check logs: `docker-compose -f docker-compose-sso.yml logs`
- Review Keycloak configuration
- Verify network connectivity between services
- Check this repository's Issues section

## Next Steps

Once everything is running:
1. Create your first flow
2. Test OAuth2 login with different roles
3. Configure additional Keycloak roles/permissions
4. Set up flow schedules and triggers
5. Explore Kestra plugins

Happy orchestrating! 🚀
