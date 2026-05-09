# Generates a kubeconfig file usable from inside the Jenkins container.
#
# Docker Desktop writes its kubeconfig pointing the API server at
# https://127.0.0.1:NNNNN. From inside a container, 127.0.0.1 is the container
# itself — kubectl never reaches the host. This script writes a copy with
# - cluster.server rewritten to use host.docker.internal
# - insecure-skip-tls-verify: true (the cert is only valid for 127.0.0.1)
#
# Output: jenkins/config/.kubeconfig-incontainer  (gitignored)
# Run again whenever Docker Desktop restarts and the API port changes.

$ErrorActionPreference = "Stop"

$source = Join-Path $env:USERPROFILE ".kube\config"
if (-not (Test-Path $source)) {
    throw "Kubeconfig not found at $source. Enable Kubernetes in Docker Desktop first."
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $scriptRoot ".kubeconfig-incontainer"

$content = Get-Content $source -Raw

# Rewrite server URL: 127.0.0.1 / kubernetes.docker.internal -> host.docker.internal
$content = $content -replace 'https://127\.0\.0\.1:', 'https://host.docker.internal:'
$content = $content -replace 'https://kubernetes\.docker\.internal:', 'https://host.docker.internal:'

# Strip any TLS verification: replace certificate-authority-data with insecure-skip-tls-verify
$content = $content -replace '(?m)^(\s*)certificate-authority-data:.*$', '$1insecure-skip-tls-verify: true'

if (Test-Path $target) {
    Remove-Item -Path $target -Force
}
[System.IO.File]::WriteAllText($target, $content, [System.Text.Encoding]::UTF8)
Write-Host "Wrote $target"
Write-Host "Mount this file at /var/jenkins_home/.kube/config in the Jenkins container."
