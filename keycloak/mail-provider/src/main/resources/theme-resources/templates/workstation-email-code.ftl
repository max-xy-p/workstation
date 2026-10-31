<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${codeTitle}
    <#elseif section = "form">
        <p>${codeDescription}</p>
        <form id="kc-email-code-form" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="email-code" class="${properties.kcLabelClass!}">邮箱验证码</label>
                <input id="email-code" name="code" class="${properties.kcInputClass!}"
                       type="text" inputmode="numeric" autocomplete="one-time-code"
                       pattern="[0-9]{6}" maxlength="6" required autofocus />
            </div>
            <p>${codeHelp}</p>
            <div class="${properties.kcFormGroupClass!}">
                <button id="verify-code" name="operation" value="verify" type="submit"
                        class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}">验证并继续</button>
                <button id="resend-code" name="operation" value="resend" type="submit" formnovalidate
                        class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonBlockClass!}">重新发送验证码</button>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
