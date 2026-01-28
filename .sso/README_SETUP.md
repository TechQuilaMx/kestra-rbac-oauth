# Kestra SSO - Development Setup Guide

This guide explains how to set up and run the Kestra SSO application with both the webserver API and the Vue.js frontend UI.

## Prerequisites

- Java 21+
- Node.js 22+ and npm
- Docker & Docker Compose
- Gradle (wrapper included)
- Firefox (for UI debugging)

## Step 1: Set Up PostgreSQL Database

Start PostgreSQL using Docker:

```bash
docker run -d \
  --name postgres-kestra \
  -e POSTGRES_DB=kestra \
  -e POSTGRES_USER=kestra \
  -e POSTGRES_PASSWORD=k3str4 \
  -p 5432:5432 \
  postgres:15
```

Verify the connection:
```bash
psql -h localhost -U kestra -d kestra -W
# Password: k3str4
```

## Step 2: Configure the Backend

The backend configuration is already set up in [`cli/src/main/resources/application-override.yml`](cli/src/main/resources/application-override.yml). It uses:
- **Database**: PostgreSQL on `localhost:5432`
- **Username**: kestra
- **Password**: k3str4
- **CORS**: Enabled for `http://localhost:5173` (UI dev server)

If you need to modify the connection, edit the `datasources.postgres.url` in the config file.

## Step 3: Build the Frontend

Navigate to the UI folder and install dependencies:

```bash
cd ui
npm install
npm run build
```

This builds the frontend and makes it available to the webserver.

## Step 4: Start the Backend (Webserver API)

In the root directory, run:

```bash
./gradlew runStandalone
```

The backend API will start on `http://localhost:8080`

**Output**: You should see logs confirming the server started successfully.

## Step 5: Start the Frontend Development Server

In a new terminal, start the Vue.js dev server:

```bash
cd ui
npm run dev
```

The frontend will be available at `http://localhost:5173`

## Step 6: Access the Application

Open your browser and navigate to:
```
http://localhost:5173
```

The UI will communicate with the backend API at `http://localhost:8080` via the configured CORS settings.

## Debugging the UI

### Option 1: Browser DevTools (Quick)

1. Open `http://localhost:5173` in Firefox
2. Press `F12` to open Developer Tools
3. Go to the **Network** tab to see all API requests
4. Go to the **Console** tab to see JavaScript logs
5. Go to the **Debugger** tab to add breakpoints in the code

### Option 2: VS Code Debugger (Recommended)

The debug configuration is already set up in [`.vscode/launch.json`](.vscode/launch.json).

**Prerequisites**: Install the [Debugger for Firefox](https://marketplace.visualstudio.com/items?itemName=firefox-devtools.vscode-firefox-debug) extension.

**Steps**:
1. Make sure the UI dev server is running (`npm run dev` in `ui/` folder)
2. Press `F5` or open the **Run and Debug** panel (Ctrl+Shift+D)
3. Select **"Debug UI with Firefox"**
4. Firefox will launch and VS Code will attach the debugger
5. Set breakpoints in Vue files by clicking on line numbers
6. Interact with the UI and step through code

### Inspecting API Requests

In the browser's Network tab or VS Code debugger:
- Filter by `Fetch/XHR` to see only API calls
- Click on a request to view:
  - **Request headers** (Authorization, Content-Type, etc.)
  - **Request body** (payload sent to the API)
  - **Response** (API response data)
  - **Timing** (how long the request took)

## Stopping the Application

1. Stop the backend: Press `Ctrl+C` in the terminal running `./gradlew runStandalone`
2. Stop the frontend: Press `Ctrl+C` in the terminal running `npm run dev`
3. Stop PostgreSQL:
   ```bash
   docker stop postgres-kestra
   docker rm postgres-kestra
   ```

## Common Issues

### Backend Cannot Connect to PostgreSQL
- **Error**: `java.net.UnknownHostException: host.docker.internal`
- **Solution**: Ensure the PostgreSQL URL in `application-override.yml` is set to `localhost` (not `host.docker.internal`) since you're running the backend directly on your machine.

### CORS Errors in Browser Console
- **Error**: `Access to XMLHttpRequest at 'http://localhost:8080/...' has been blocked by CORS policy`
- **Solution**: Verify that CORS is enabled in `application-override.yml`:
  ```yaml
  micronaut:
    server:
      cors:
        enabled: true
        configurations:
          all:
            allowedOrigins:
              - http://localhost:5173
  ```

### Port Already in Use
- **UI Port 5173 in use**: Change the port in `ui/vite.config.js` or kill the process: `lsof -i :5173 | awk 'NR==2 {print $2}' | xargs kill`
- **API Port 8080 in use**: Kill the process or change the port configuration

### Firefox Debugger Won't Connect
- Ensure Firefox is not already running with other debugger sessions
- Try restarting the debug session in VS Code
- Check that the `debugger.port` is set correctly (default is 6000)

## Environment Structure

```
/data/dev/open-source/kstr-sso/
├── cli/                               # CLI and server implementation
│   └── src/main/resources/
│       └── application-override.yml   # Backend configuration
├── ui/                                # Vue.js frontend
│   ├── src/
│   ├── npm run dev                    # Start dev server
│   └── npm run build                  # Build for production
├── webserver/                         # REST API implementation
├── core/                              # Core Kestra framework
└── .vscode/
    └── launch.json                    # VS Code debug configurations
```

## Useful Commands

```bash
# Backend
./gradlew runStandalone                # Start backend in standalone mode
./gradlew runLocal                     # Start backend with H2 (local) database
./gradlew clean build                  # Clean rebuild

# Frontend
cd ui && npm run dev                   # Start dev server
cd ui && npm run build                 # Build for production
cd ui && npm run lint                  # Check code quality
cd ui && npm run test                  # Run tests

# Database
docker ps                              # List running containers
docker logs postgres-kestra            # View PostgreSQL logs
```

## Next Steps

1. Explore the API documentation at `http://localhost:8080/swagger-ui/` (if available)
2. Check the Vue component structure in `ui/src/components/`
3. Review the store management in `ui/src/stores/` (Pinia)
4. Add API interceptors in `ui/src/utils/axios.js` if needed

---

**Happy debugging!** 🚀
