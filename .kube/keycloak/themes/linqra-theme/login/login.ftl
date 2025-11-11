<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Linqra Login</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
    <script src="${url.resourcesPath}/js/home-link.js"></script>
</head>
<body>
    <a href="#" onclick="goToHome(); return false;" class="home-link">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
            <polyline points="9,22 9,12 15,12 15,22"/>
        </svg>
        Home
    </a>
    
    <div class="logo-container">
        <img src="${url.resourcesPath}/img/noBgWhiteOnlyLogo.png" alt="Linqra Logo" />
    </div>
    
    <div class="kc-body">
        <div class="kc-form-card">
            <div class="kc-header">
                <h1>Linqra SSO Login</h1>
                <p className="text-muted lead "> — Agentic AI Orchestration Platform — </p>
                <br/>
                <p className="text-muted lead mt-2">Design, deploy, and monitor enterprise-grade AI workflows with built-in observability and security.</p>
            </div>
            
            <#if displayMessage?? && displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="alert-${message.type}">
                    ${kcSanitize(message.summary)?no_esc}
                </div>
            </#if>
            
            <#if realm.password?? && realm.password>
                <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                    <div class="kc-form-group">
                        <#if usernameEditDisabled??>
                            <input tabindex="1" id="username" class="kc-input" name="username" value="${(login.username!'')}" type="text" disabled />
                        <#else>
                            <input tabindex="1" id="username" class="kc-input" name="username" value="${(login.username!'')}" type="text" autofocus autocomplete="off" 
                                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" />
                        </#if>
                        <label for="username" class="kc-label"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                    </div>
                    
                    <div class="kc-form-group">
                        <input tabindex="2" id="password" class="kc-input" name="password" type="password" autocomplete="off"
                               aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" />
                        <label for="password" class="kc-label">${msg("password")}</label>
                    </div>
                    
                    <div class="kc-form-options">
                        <#if realm.rememberMe?? && !usernameEditDisabled??>
                            <div class="kc-checkbox">
                                <#if login.rememberMe??>
                                    <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" checked>
                                <#else>
                                    <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox">
                                </#if>
                                <label for="rememberMe">${msg("rememberMe")}</label>
                            </div>
                        </#if>
                    </div>
                    
                    <div class="kc-form-buttons">
                        <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                        <input tabindex="4" class="kc-button kc-button-primary" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                    </div>
                </form>
            </#if>
            
            <#if realm.password?? && realm.password && realm.registrationAllowed?? && realm.registrationAllowed && !registrationDisabled??>
                <div class="kc-form-options">
                    <span>${msg("noAccount")} <a href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </#if>
            
            <#if realm.password?? && realm.password && realm.resetPasswordAllowed?? && realm.resetPasswordAllowed>
                <div class="kc-form-options">
                    <span>${msg("forgotPassword")} <a href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a></span>
                </div>
            </#if>
        </div>
    </div>
</body>
</html>
