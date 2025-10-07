function goToHome() {
    const currentHost = window.location.hostname;
    
    // Determine the frontend port based on environment
    let frontendPort = '3000'; // Default for local development
    
    // If we're on localhost, use port 3000
    if (currentHost === 'localhost' || currentHost === '127.0.0.1') {
        frontendPort = '3000';
    } else {
        // For EC2 or other deployments, use HTTPS standard port (443)
        frontendPort = '443';
    }
    
    // Always use HTTPS for the frontend
    let frontendUrl = 'https://' + currentHost;
    
    // Add port only if it's not a standard HTTPS port
    if (frontendPort && frontendPort !== '443') {
        frontendUrl += ':' + frontendPort;
    }
    
    window.location.href = frontendUrl;
}
