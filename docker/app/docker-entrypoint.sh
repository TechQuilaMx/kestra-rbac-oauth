#!/bin/bash
set -e

# Function to show usage
show_help() {
    echo "Kestra Docker Container"
    echo ""
    echo "Usage: docker run [docker options] kestra/kestra [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  server local       Start Kestra in local mode (H2 database)"
    echo "  server standalone  Start Kestra in standalone mode (requires external DB)"
    echo "  server worker      Start a Kestra worker"
    echo "  server webserver   Start the Kestra web server"
    echo "  server executor    Start the Kestra executor"
    echo "  server scheduler   Start the Kestra scheduler"
    echo "  server indexer     Start the Kestra indexer"
    echo "  plugins install    Install Kestra plugins"
    echo "  --help            Show help message"
    echo "  --version         Show version information"
    echo ""
    echo "Examples:"
    echo "  docker run kestra/kestra server local"
    echo "  docker run kestra/kestra server standalone --worker-thread=128"
}

if [ "$#" -eq 0 ] || [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    show_help
    exit 0
fi

exec /app/kestra "$@"