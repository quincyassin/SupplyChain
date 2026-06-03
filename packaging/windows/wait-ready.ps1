# Wait until the local service responds on port 8080
$uri = 'http://127.0.0.1:8080'
$maxAttempts = 60

for ($attempt = 0; $attempt -lt $maxAttempts; $attempt++) {
    try {
        $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            exit 0
        }
    }
    catch {
        # Service not ready yet
    }
    Start-Sleep -Seconds 1
}

exit 1
