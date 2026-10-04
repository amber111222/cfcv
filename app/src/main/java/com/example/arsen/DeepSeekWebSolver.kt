package com.example.arsen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class DeepSeekWebSolver(
    private val context: Context,
    private val onAnswerReceived: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {

    var webView: WebView? = null
        private set

    private var pendingPrompt: String? = null
    private var isPageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    fun initWebView(): WebView {
        if (webView != null) return webView!!

        val view = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(JsBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isPageReady = true
                    onStatusUpdate("DeepSeek готов к работе")

                    // Если был отложенный запрос — отправляем
                    pendingPrompt?.let { prompt ->
                        injectAndSend(prompt)
                        pendingPrompt = null
                    }
                }
            }

            webChromeClient = WebChromeClient()

            loadUrl("https://chat.deepseek.com")
        }

        webView = view
        return view
    }

    /**
     * Фоновая отправка промпта в DeepSeek Web
     */
    fun sendPrompt(prompt: String) {
        if (!isPageReady || webView == null) {
            pendingPrompt = prompt
            onStatusUpdate("Инициализирую фоновый DeepSeek...")
            initWebView()
            return
        }

        injectAndSend(prompt)
    }

    private fun injectAndSend(prompt: String) {
        onStatusUpdate("Отправляю запрос в DeepSeek...")

        // Экранируем спецсимволы для безопасной вставки в JS
        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        val jsCode = """
            (function() {
                var promptText = "$escapedPrompt";
                
                // Ищем поле ввода
                var textarea = document.querySelector('textarea') || document.querySelector('input[type="text"]');
                if (!textarea) {
                    console.log('No textarea found');
                    return 'NO_INPUT';
                }
                
                textarea.focus();
                textarea.value = promptText;
                textarea.dispatchEvent(new Event('input', { bubbles: true }));
                textarea.dispatchEvent(new Event('change', { bubbles: true }));
                
                // Нажимаем кнопку отправки
                setTimeout(function() {
                    var buttons = Array.from(document.querySelectorAll('button, div[role="button"]'));
                    var sendBtn = buttons.find(function(b) {
                        return b.querySelector('svg') || 
                               b.getAttribute('aria-label') === 'Send' || 
                               b.textContent.includes('Send') ||
                               b.classList.contains('ds-send-btn');
                    });
                    
                    if (sendBtn) {
                        sendBtn.click();
                        console.log('Send clicked');
                    } else {
                        // Пробуем нажать Enter
                        textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                    }
                    
                    // Слушаем ответ
                    var lastContent = '';
                    var stableCount = 0;
                    var pollInterval = setInterval(function() {
                        var messages = document.querySelectorAll('.ds-markdown, div[class*="markdown"], div[class*="message"]');
                        if (messages.length > 0) {
                            var latest = messages[messages.length - 1];
                            var currentText = latest.innerText || latest.textContent || '';
                            if (currentText.length > 15) {
                                if (currentText === lastContent) {
                                    stableCount++;
                                    // Текст не меняется 3 секунды -> генерация завершена
                                    if (stableCount >= 3) {
                                        clearInterval(pollInterval);
                                        if (window.AndroidBridge) {
                                            window.AndroidBridge.onResult(currentText);
                                        }
                                    }
                                } else {
                                    lastContent = currentText;
                                    stableCount = 0;
                                }
                            }
                        }
                    }, 1000);
                }, 400);
                
                return 'SENT';
            })();
        """.trimIndent()

        webView?.evaluateJavascript(jsCode) { result ->
            if (result == "\"NO_INPUT\"") {
                onStatusUpdate("Войдите в DeepSeek в окне ниже для первого запуска")
            } else {
                onStatusUpdate("DeepSeek генерирует ответ...")
            }
        }
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onResult(answerText: String) {
            onAnswerReceived(answerText)
        }
    }
}
